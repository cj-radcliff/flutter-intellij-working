/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import org.junit.jupiter.api.Test
import kotlin.io.path.readText
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Smoke test for [ExistingProjectUITest] and IDE selection.
 *
 * **Shows:** the selected IDE opens a project created with `flutter create`, loads the Flutter and Dart plugins, and
 * opens `lib/main.dart` in an editor.
 *
 * Run it against a specific IDE with `-Pide.product` and `-Pide.version` (see `testSrc/integration/README.md`).
 */
class OpenProjectSmokeUITest : ExistingProjectUITest("open_project_smoke") {

  @Test
  fun opensFlutterProject() {
    startIdeOnProject()

    step("Check the plugins", "The IDE log lists the Flutter and Dart plugins as loaded.")
    waitUntil("the Flutter and Dart plugins to load") {
      val loaded = ideLogFile.readText().lineSequence().firstOrNull { it.contains("Loaded custom plugins:") }
      loaded != null && loaded.contains("Flutter") && loaded.contains("Dart")
    }

    step("Open lib/main.dart", "The editor shows the `flutter create` app, which calls `runApp`.")
    openProjectFile("lib/main.dart")
    inFrame {
      val text = editorFor("main.dart").text
      assertTrue(text.contains("runApp(")) { "lib/main.dart should contain runApp(, but was:\n$text" }
    }
  }
}
