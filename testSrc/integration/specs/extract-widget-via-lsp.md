# Extract a Flutter widget with the Dart plugin's LSP code actions enabled

**Goal:** as a Flutter developer who has turned on the Dart plugin's experimental LSP
features, I want *Refactor › Extract Flutter Widget…* to still extract the widget
under the caret into a new widget class.
**Source:** https://github.com/flutter/flutter-intellij/pull/9202 (merged as
`8f8f7fdce`; intent: "route Extract Widget requests to LSP code actions when
experimental LSP features are enabled", fixes #9142). MVP milestone M2, run
through the `change-to-spec` path.
**Seam gate result:** `ui` — the outcome depends on a live Dart analysis
server answering a `textDocument/codeAction` request over LSP4IJ and on the
action's enablement in a real editor; `BasePlatformTestCase` has no LSP server
(the PR's unit tests already cover the helper against a mock, which is exactly
what this spec must *not* duplicate). No existing spec shares this fixture:
T1 (`hot-reload-applies-edit.md`) boots the same counter app but with a device
and a running app; this workflow needs neither, so it should not be coalesced
into T1 (its boot is shorter without a device).

## Target seam

- `io.flutter.actions.ExtractWidgetAction` → when
  `FlutterLspRefactoringHelper.isLspCodeActionsEnabled(project)` is true →
  `io.flutter.refactoring.FlutterLspRefactoringHelper.performExtractWidget`
  (code action kind `refactor.extract`, applies the returned workspace edit).
- Action ID `Flutter.ExtractWidget` (`plugin.xml`).

**Why not a unit test:** `FlutterLspRefactoringHelperTest` already proves the
helper builds the right request and applies a given edit. What it cannot see
is whether the real server returns an Extract Widget code action for a real
selection in a real project, whether the edit it returns applies cleanly to the
open editor, and whether the user ends up with a compiling file. Those are the
user's outcome.

## Fixture

- Base project: `flutter create` counter app (`lib/main.dart` contains the
  `Text('You have pushed the button this many times:')` widget). Device: none.
- Overlay: none.
- Product: `IU`. `ide.version`: harness default (262.10968.63); the change is
  not IDE-version-specific.
- Dart plugin: the version `Setup` installs (510.0.0 on 262), with **LSP code
  actions enabled** (the Dart plugin's experimental LSP setting that
  `DartConfigurable.isLspCodeActionsEnabled` reads). Enabling it is part of the
  fixture, not a step, because the Goal is about users who have it on.

## Preconditions

- Flutter SDK configured (`FLUTTER_SDK`); Dart SDK enabled for the project.
- LSP code actions enabled in the Dart plugin settings (see Fixture); the
  analysis server has started and finished analysing `lib/main.dart` (no
  spinner in the status bar; the file has no errors).
- `lib/main.dart` is open in the editor.

## Steps

| # | Task | User interaction | Drives |
|---|---|---|---|
| 1 | Prepare | Open `lib/main.dart` and wait for analysis to finish | `openProjectFile("lib/main.dart")`, `waitForAnalysisIdle()` (probe: how the harness tells LSP analysis is idle) |
| 2 | Select | Put the caret inside the `Text('You have pushed the button this many times:')` expression | `editor.caretAt("Text('You have pushed")` |
| 3 | Extract | **(trigger)** Choose *Refactor › Extract Flutter Widget…* from the editor context menu (or the main menu) | `Flutter.ExtractWidget` via `invokeFlutterAction` with the editor as context (the menu path is not the claim; the action is) |
| 4 | Confirm | If a name dialog appears, accept the default name | `probe` (the LSP path applies the edit with the server's default name; the legacy path shows a dialog. Record which one appeared) |

## Expected outcome

The selected `Text(...)` expression is replaced by a reference to a new widget
(`NewWidget()` or the name accepted in step 4), and a new
`class NewWidget extends StatelessWidget` with a `build` method returning the
original `Text(...)` appears in `lib/main.dart`. The file still analyses
without errors.

**Requirements:** the edit is applied within 30 s of the trigger (one LSP round
trip plus analysis).

## Oracle

- **O1 — Pre-state** (before step 3):
  - `lib/main.dart` contains exactly one `Text('You have pushed the button this many times:')` and no class named `NewWidget`.
  - LSP code actions are enabled (`DartConfigurable.isLspCodeActionsEnabled(project)` is true) and the analysis server is running.
- **O2 — Attribution** (after step 3):
  - The `Flutter.ExtractWidget` action was enabled and invoked in the editor's context (the harness checks enablement before invoking).
  - The document changed within 30 s **without** the legacy "Extract Widget" refactoring dialog appearing (the legacy LTK path is modal; the LSP path applies the server's edit directly). If a dialog appears, O2 fails: the request did not go through the LSP seam.
- **O3 — Positive postconditions:**
  - `lib/main.dart` contains `class NewWidget extends StatelessWidget` (or the accepted name) with a `build` method whose body returns `Text('You have pushed the button this many times:')`.
  - The original site now reads `NewWidget()` (optionally `const`), and the `Text(...)` literal occurs exactly once in the file (inside the new class).
  - The editor shows the modified document (not just the VFS): the new class is visible in the editor text.
- **O4 — Negative / analysis postconditions:**
  - No "Cannot perform refactoring" error hint was shown (`FlutterLspRefactoringHelper.showCannotRefactorErrorHint` outcomes: server not running, no response, action disabled).
  - Zero `ERROR` Dart analysis diagnostics in `lib/main.dart` after the edit (the extracted code compiles).
  - No `SEVERE` `idea.log` entries with `io.flutter` frames (harness, green runs).

## Test level

`ui` — `ExistingProjectUITest` subclass, `@Tag("agent-generated")`.

## Certification plan

- **Hunk classification (from the PR diff, after the intent was written down):**
  `ExtractWidgetAction.java` (+10): **behavior change** — existing action now
  routes to LSP when enabled. `FlutterLspRefactoringHelper.kt` (+193): **new
  behavior** behind that branch. `ExtractWidgetRefactoring.java` (+5,
  deprecation), `FlutterBundle.properties` (strings), `ABOUT.md`, `CHANGELOG.md`,
  `testSrc/**`: **internal**, no spec.
- **Red build:** `merge-base` (`8f8f7fdce^`): the action ID and the fixture
  exist there, so the test reaches the trigger; on that build the action takes
  the legacy LTK path under LSP mode. **Caveat the runner must check:** if the
  legacy path still extracts the widget correctly while LSP mode is on, the
  change is a *refactor* from the user's point of view and `merge-base` will
  pass (`mutant-survived`); in that case use the **hollow seam** instead:
  `HEAD` with `FlutterLspRefactoringHelper.performRefactoring` returning
  without applying the edit (keep the signature; the red then trips O3). Either
  way, do not weaken O2's "no legacy dialog" check to get a red.
  Expected failure: `O2` (legacy dialog appears, or no document change within
  30 s) and/or `O3` (no `NewWidget` class), after steps 1–2 and `O1` complete.
- **Green build:** `HEAD`, `N_green = 3`.
- **N_red:** 1.
