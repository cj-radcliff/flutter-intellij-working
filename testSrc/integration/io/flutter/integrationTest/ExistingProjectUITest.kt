/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditorForFile
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.project.LocalProjectInfo
import io.flutter.integrationTest.utils.Platform
import io.flutter.integrationTest.utils.RunResult.Phase
import io.flutter.integrationTest.utils.createFlutterProjectWithCli
import io.flutter.integrationTest.utils.deleteFlutterProject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Base class for UI tests that start with the IDE already open on an existing Flutter project.
 *
 * The project is created with `flutter create` (not the plugin's New Project wizard) in a temp directory before the
 * IDE starts, and deleted afterwards unless `KEEP_TEST_PROJECT=true`. Set `KEEP_IDE_OPEN_MINUTES` to leave the IDE
 * running after the test. Requires the `FLUTTER_SDK` environment variable.
 *
 * The plugin-loaded gate, `step`/`assertOracle`/`probe`, the `idea.log` gate, and process cleanup come from
 * [FlutterIdeUITest].
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ExistingProjectUITest(private val packageName: String) : FlutterIdeUITest() {

  private val projectFolder = "${packageName}_${System.currentTimeMillis()}"
  private val parentDir: String = Platform.testProjectsDir.toString()

  /** Root of the test project on disk. */
  protected val projectDir: Path = Paths.get(parentDir, projectFolder)

  @BeforeAll
  fun createProject() {
    createFlutterProjectWithCli(projectFolder, projectName = packageName, directory = parentDir)
    prepareProject()
  }

  /** Hook for subclasses to adjust the project on disk before the IDE opens it, for example to apply an overlay. */
  protected open fun prepareProject() {}

  @AfterAll
  fun deleteProject() {
    if (System.getenv("KEEP_TEST_PROJECT").toBoolean()) {
      println("Keeping test project: $projectDir")
    }
    else {
      deleteFlutterProject(projectFolder, directory = parentDir)
    }
  }

  /** Starts the IDE on [projectDir] and waits until the project is open and indexed. */
  protected fun startIdeOnProject() {
    println("Opening existing project $projectDir")
    step(
      "Open the existing Flutter project",
      "IntelliJ IDEA starts (splash screen), then opens the project created with `flutter create`. " +
      "The Project view shows its files and the status bar shows indexing until it finishes."
    )
    startIde(Setup.setupTestContextIU262(javaClass.simpleName, LocalProjectInfo(projectDir))) { it.runIdeWithDriver() }
    result.currentPhase = Phase.SETUP
    waitForProjectReady()
    // The Trial tab usually opens while the project loads, but can arrive shortly after; poll briefly here and again
    // when opening files or polling conditions in [dismissInterruptions].
    val deadline = System.currentTimeMillis() + 3_000
    while (!closeTrialTab() && System.currentTimeMillis() < deadline) Thread.sleep(500)
  }

  /** Runs [action] inside the project frame. */
  protected fun inFrame(action: IdeaFrameUI.() -> Unit) {
    driver.withContext { ideFrame { action() } }
  }

  /** Opens [relativePath] (relative to the project root) in an editor and waits for code analysis. */
  protected fun openProjectFile(relativePath: String) {
    closeTrialTab()
    driver.openFile(relativePath)
    closeTrialTab()
  }

  /** The editor for [fileName], e.g. `main.dart`. */
  protected fun IdeaFrameUI.editorFor(fileName: String): JEditorUiComponent = codeEditorForFile(fileName)

  // ---------------------------------------------------------------------------------------------------------------
  // Consoles

  /**
   * Text of the console views in the frame (Run, Debug, Messages, Flutter consoles), and only those: source editors are
   * excluded, so a string in `main.dart` or `pubspec.yaml` can't satisfy a console check. Pass [toolWindow] (the tool
   * window's title, for example `Run`) to read only consoles inside that tool window. The result starts at
   * [sinceOffset], so a test can record the length before its trigger step and read only what came after.
   */
  protected fun IdeaFrameUI.consoleText(toolWindow: String? = null, sinceOffset: Int = 0): String {
    val scope = if (toolWindow == null) "" else "//div[@class='InternalDecoratorImpl'][.//div[@accessiblename='$toolWindow' or @text='$toolWindow']]"
    val all = xx("$scope//div[@class='ConsoleViewImpl']//div[@class='EditorComponentImpl']", JEditorUiComponent::class.java).list()
      .joinToString("\n") { runCatching { it.text }.getOrDefault("") }
    return when {
      sinceOffset <= 0 -> all
      sinceOffset >= all.length -> ""
      else -> all.substring(sinceOffset)
    }
  }

  /** Length of [consoleText] now, to pass as `sinceOffset` after the trigger step. */
  protected fun IdeaFrameUI.consoleOffset(toolWindow: String? = null): Int = consoleText(toolWindow).length

  /**
   * Waits until a console contains one of [texts]; returns the one found. Fails fast if one of [failTexts] appears
   * first, so an error doesn't turn into a timeout. Reads consoles only, never source editors.
   */
  protected fun IdeaFrameUI.waitForConsoleText(
    vararg texts: String,
    toolWindow: String? = null,
    sinceOffset: Int = 0,
    failTexts: List<String> = emptyList(),
    timeout: Duration = 3.minutes,
  ): String {
    var found = ""
    waitFor("console to contain one of ${texts.toList()}", timeout, 2.seconds) {
      dismissInterruptions()
      val all = consoleText(toolWindow, sinceOffset)
      failTexts.firstOrNull { all.contains(it) }?.let { bad ->
        throw AssertionError("Console shows '$bad' while waiting for one of ${texts.toList()}")
      }
      found = texts.firstOrNull { all.contains(it) } ?: ""
      found.isNotEmpty()
    }
    println("Found console text: $found")
    return found
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Devices and apps

  /** Opens the Flutter device selector and clicks the device whose name starts with one of [names]. */
  protected fun IdeaFrameUI.selectDevice(vararg names: String) {
    require(names.isNotEmpty()) { "At least one device name must be provided" }
    fun String.matches() = names.any { startsWith(it, ignoreCase = true) }
    val selector = x(
      "//div[contains(@javaclass, 'DeviceSelectorAction')]//div[@class='JBLabel' and @text!=''] | " +
      "//div[@class='JBLabel' and (contains(@text, '(desktop)') or contains(@text, '(web)') or " +
      "contains(@text, '(mobile)') or @text='No Devices' or @text='No Device Selected' or @text='Loading Devices...')]"
    )
    var attempt = 0
    waitUntil("device ${names.toList()} to be selected", timeout = 3.minutes) {
      val currentTexts = selector.getAllTexts().map { it.text }
      if (currentTexts.any { it.matches() }) return@waitUntil true
      if (currentTexts.isEmpty() || currentTexts.any { it == "Loading Devices..." }) return@waitUntil false
      val name = names[attempt++ % names.size]
      selector.click()
      runCatching {
        waitFor("device popup to open", 5.seconds, 200.milliseconds) {
          driver.ui.xx("//div[@class='MyList' or @class='JBList']").list().isNotEmpty()
        }
      }
      // Use the popup's speed search. If nothing matches (the device daemon may not have listed the device yet, or
      // the name differs), close the popup so the typed text can't land in the editor, and try the next name.
      keyboard { typeText(name, 50) }
      val nothingToShow = runCatching {
        waitFor("speed search result", 1.seconds, 200.milliseconds) {
          x("//div[contains(@visible_text, 'Nothing to show')]").present() ||
          driver.ui.xx("//div[contains(@visible_text, 'Nothing to show')]").list().isNotEmpty()
        }
        true
      }.getOrDefault(false)
      if (nothingToShow) {
        keyboard { escape() }
        false
      }
      else {
        keyboard { enter() }
        runCatching {
          waitFor("device '$name' to be selected", 5.seconds, 200.milliseconds) {
            selector.getAllTexts().any { it.text.matches() }
          }
          true
        }.getOrDefault(false)
      }
    }
    println("Selected device: ${selector.getAllTexts().joinToString("") { it.text }}")
  }

  /**
   * Stops the running app by clicking a Stop button (in the toolbar or the Run/Debug tool window), like a user would,
   * and **fails** unless the process ends: the Stop buttons go disabled and the console reports the process finished.
   * The Stop shortcut differs between keymaps (Ctrl+F2 vs. ⌘F2 on macOS), and invoking the `Stop` action with the
   * editor as context is rejected as disabled, so the button is the reliable path.
   */
  protected fun IdeaFrameUI.stopAppAndVerifyTerminated(timeout: Duration = 30.seconds) {
    val stop = xx("//div[@class='ActionButton' and @myicon='stop.svg']").list().firstOrNull { it.isEnabled() }
               ?: throw AssertionError("No enabled Stop button: is an app running?")
    stop.click()
    waitFor("app process to stop", timeout, 500.milliseconds) {
      xx("//div[@class='ActionButton' and @myicon='stop.svg']").list().none { it.isEnabled() }
    }
    waitFor("console to report the process finished", timeout, 500.milliseconds) {
      val text = consoleText()
      text.contains("Process finished") || text.contains("Application finished") || text.contains("Lost connection to device")
    }
    println("App stopped")
  }

  /** Best-effort stop for cleanup paths; prefer [stopAppAndVerifyTerminated] in test steps. */
  protected fun IdeaFrameUI.stopApp() {
    runCatching { stopAppAndVerifyTerminated() }
      .onFailure { println("Could not stop the app (${it.message}); it stops when the IDE closes.") }
  }

  private fun waitForProjectReady(attempts: Int = 3) {
    repeat(attempts) { attempt ->
      try {
        inFrame {
          waitFound(2.minutes)
          driver.waitForIndicators(5.minutes)
        }
        println("Project is open and indexed.")
        return
      }
      catch (e: Exception) {
        if (attempt == attempts - 1) throw e
        println("Project frame not stable yet (${e.javaClass.simpleName}); retrying")
        Thread.sleep(5_000)
      }
    }
  }
}
