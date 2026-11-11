---
name: ui-test-reference
description: Reference for writing and debugging IntelliJ IDE Starter + Driver UI tests for the Flutter and Dart plugins. Covers how Starter launches the IDE and installs plugins, how Driver calls IDE services and finds Swing components, our harness base classes and helpers (FlutterIdeUITest, ExistingProjectUITest, step, assertOracle, probe, consoleText, invokeFlutterAction, overlays), platform differences on Linux, macOS, and Windows, what Driver cannot see, and the pitfalls already hit. Use whenever writing, repairing, or reading a test under testSrc/integration. Not a workflow skill; it's the manual the agent-testing-* skills and record-test-walkthrough point to.
---

# UI Test Reference: Starter, Driver, and the Flutter harness

> **Status:** outline. The sections are filled in during MVP step 1, alongside
> the harness code they document. Until then, the authoritative sources are
> the files linked from each section.

The UI tests in [`testSrc/integration`](../../../testSrc/integration/README.md)
run a real IDE in a separate process and drive it from the test JVM. Three
layers are involved, and most mistakes come from confusing them:

| Layer | What it is | Where |
|---|---|---|
| **IDE Starter** | JetBrains' framework that downloads an IDE build and plugins, prepares a project, launches the IDE, and collects logs | `Setup.kt` |
| **Driver** | JetBrains' remote-call API from the test JVM into the running IDE: services via `@Remote` interfaces, UI via XPath-like locators | used throughout the tests |
| **Our harness** | Base classes and helpers that encode Flutter-plugin workflows and the certification contract | `ExistingProjectUITest.kt`, `utils/` |

## 1. Starter

- Test contexts: `Setup.setupTestContextIU` / `IU262` / `FromProperties`,
  `ide.product` and `ide.version`, how `latest` resolves.
- Plugins: `path.to.build.plugin` (the zip `./gradlew buildPlugin` just made),
  `-Pflutter.plugin.zip` to substitute another build, `DART_PLUGIN_ZIP` for a
  locally built Dart plugin, `dartPluginVersion` from `gradle.properties`,
  `FLUTTER_UI_TEST_WITHOUT_PLUGIN=true` for the plugin-off control.
- Where things land: `build/out/ide-tests/` (IDE, sandbox, `idea.log`,
  screenshots), `build/agent-testing/run-result.json`.
- Timeouts and what a bare timeout means (nothing).

## 2. Driver

- Calling services: `@Remote("fully.qualified.Class")` interfaces, `driver.service(...)`,
  `driver.utility(...)`; EDT vs. background.
- Finding UI: `x("//div[...]")` and `xx(...)`, the attribute vocabulary
  (`class`, `name`, `accessiblename`, `text`, `visible_text`, `title`,
  `myicon`, `myaction`, `enabled`, `visible`), `waitFound`, `present`.
- Typed UI components (`IdeaFrameUI`, `JEditorUiComponent`, …) and when to
  use them over raw locators.
- What Driver **cannot** see or do, and what to do instead: JCEF/JxBrowser
  page content (`JBCefJSQuery`), native file dialogs (`Setup.kt` forces Swing
  dialogs), OS-level windows (Chrome, emulator), actions whose `update()`
  would disable them (use `invokeFlutterAction`, which checks first).

## 3. Our harness

- Base classes: `FlutterIdeUITest` (welcome screen; all common gates),
  `ExistingProjectUITest` (project from `flutter create`), the New Project
  base. Which to pick, from the spec's Fixture.
- Certification helpers: `step(title, trigger)`, `assertOracle(id, description)`,
  `probe(title)`, what each records and how `run-result.json` is written.
- Observation helpers: `consoleText(toolWindow, sinceOffset)`,
  `waitForConsoleText`, `waitUntil`, `editorFor`, the `idea.log` slice.
- Action helpers: `invokeFlutterAction(id)`, `selectDevice`, `openProjectFile`,
  `stopAppAndVerifyTerminated`, `dismissInterruptions` (Trial tab, surveys,
  Android Studio sign-in).
- Fixtures: `prepareProject()`, overlays under `testData/integration/overlays/`.
- Walkthrough hooks: how `step` titles become captions
  (`WalkthroughSteps`), and `record-test-walkthrough`.

## 4. Platforms

- Everything runs on Linux, macOS, and Windows; `Platform.kt` holds the
  differences (paths, modifier keys, display mode, Chrome executable).
- Linux: X11 vs. Wayland desktop, Xvfb and Xephyr for headless/visible,
  `XDG_SESSION_TYPE` and Chrome.
- macOS and Windows: desktop session required; no headless; what `Setup.kt`
  configures (`ide.mac.file.chooser.native=false`, etc.).
- Linux-only: Xvfb, the walkthrough recorder.

## 5. Pitfalls already hit

Each with the symptom, the cause, and the rule that came out of it:

- The empty `Flutter.zip` (tests ran without the plugin) → plugin-loaded gate.
- JCEF `NoClassDefFoundError` showed as a 3-minute timeout → `idea.log` gate.
- `allEditorText()` matched `main.dart` and `pubspec.yaml` → scoped `consoleText`.
- `"Reloaded"` matched `"Restarted"` and `"Reloaded 0 of"` → O4 negatives.
- `stopApp()` left `flutter` daemons running → `stopAppAndVerifyTerminated`
  and teardown cleanup.
- Chrome opened on the Wayland desktop instead of Xvfb → `XDG_SESSION_TYPE=x11`
  and `--ozone-platform=x11`.
- Trial tab, feedback survey, and Android Studio sign-in blocking background
  tasks → `dismissInterruptions`.
