---
name: agent-testing-run-integration-test
description: Certify an agent-authored integration test for the Flutter or Dart IntelliJ plugins by proving it fails on a deliberately broken plugin build and passes on the real one, classifying failures from run-result.json, repairing test defects within a budget without weakening the oracle, and producing an evidence report with logs, screenshots, and a walkthrough. Modes certify (red then green, for every new or changed test), repro (red only, for open bugs), and check (green only, for already-certified tests). Use after agent-testing-author-integration-test. Don't use to write specs or tests.
---

# Skill: Run and Certify an Integration Test

Prove that a test means something. A test is **certified** when the same test
code fails on a plugin build that has the behavior broken and passes, three
times in a row, on the real build. You run the builds and the tests, classify
what happened from `run-result.json`, repair test defects inside a budget, and
write the evidence report. You never change what the test checks.

Design: [`agent-integration-testing.md`](../../design-docs/agent-integration-testing.md)
sections 5.6–5.9. Scripts: [`scripts/`](scripts/).

## Modes

| Mode | For | Proof required |
|---|---|---|
| `certify` | Every new or modified test, from any `*-to-spec` path | **Valid red** on the broken build, then **green** `N_green` times on the real build, same test bytes |
| `repro` | Open bugs with no fix yet | **Valid red** on `HEAD`. Test is tagged `unvalidated-repro` until a fix passes `certify` |
| `check` | Tests already certified (new IDE build, new OS, periodic run) | Green `N_green` times |

`N_red = 1` for deterministic faults; `3` when the spec's Requirements say
intermittent or freeze. `N_green = 3` for `ui`, `5` for `platform`. A mixed
green result is **flaky** and fails; report it, don't rerun until it passes.

## Workflow

### 1. Preflight

Fail fast, before any 4-minute run, on:

- `FLUTTER_SDK` unset or `flutter --version` failing; `flutter devices` not
  listing the spec's device.
- No display (`DISPLAY` unset and no Xvfb) for `ui` tests.
- Less than 10 GB free, or the IDE cache missing when offline.
- Lint failure:
  ```bash
  python3 .agents/skills/agent-testing-run-integration-test/scripts/lint_spec.py testSrc/integration/specs/<name>.md --test <test file>
  ```
- A dirty `src/` or `resources/`: `git status --porcelain src resources` must
  be empty, except a component-naming commit the author reported.

Resolve `ide.version` to an exact build now and pass it to every run in this
certification, so red and green use the same IDE.

Record the **bundle hash**: `sha256` over the spec, the test file, and the
overlay directory. Red and green must report the same hash.

### 2. Build the broken plugin (certify and repro)

Read the spec's **Certification plan** and build accordingly, always in a
detached worktree so the working tree is never touched:

```bash
# Closed bug: HEAD with the fix reverted
tool/agent-testing/build-broken-plugin.sh --repo flutter --revert <fix-sha>...
# Change: the code before the change
tool/agent-testing/build-broken-plugin.sh --repo flutter --rev <merge-base>
# Workflow, refactor, or new behavior: a fault patch
tool/agent-testing/build-broken-plugin.sh --repo flutter --patch <fault.patch>
# Dart plugin seam
tool/agent-testing/build-broken-plugin.sh --repo dart --patch <fault.patch>
```

The script prints the zip path. Pass it to the test run as
`-Pflutter.plugin.zip=<zip>` or `DART_PLUGIN_ZIP=<zip>`. If it fails to
build, that's an **environment** result: report which strategy failed and
stop; don't switch strategies on your own, since a different fault proves a
different thing.

**Writing a fault patch** (workflow and refactor cases): 1–5 lines in the
seam the spec names. Make the outcome *wrong*, not absent: a reload that
restarts, an event that's dropped, a value off by one, a branch inverted.
Don't `throw`, and don't `return` at the top of the method when there's logic
below it; those trip O2 and leave O3/O4 untested. Write the patch from the
spec's Target seam and Steps **without reading the test's assertions**; if
you've already read them, say so in the report.

For the suite-wide control, also run once with
`FLUTTER_UI_TEST_WITHOUT_PLUGIN=true`: every `ui` test must fail.

### 3. Red run

```bash
FLUTTER_SDK=... ./gradlew --no-daemon integration --tests <fqcn> \
  -Pide.version=<pinned> -Pflutter.plugin.zip=<broken.zip>
```

Then classify:

```bash
python3 .agents/skills/agent-testing-run-integration-test/scripts/parse_run_result.py build/agent-testing/run-result.json --expect red --expect-oracle O3,O4
```

A red run is **valid** only when all of these hold, and the script is what
decides, not your reading of the log:

- `pluginLoaded` is true (the broken plugin loaded; it's broken, not missing).
- `testsAborted` is 0 (no `assumeTrue` skips).
- Every `step` before the trigger completed, and `O1` passed.
- `failurePhase` is `ORACLE` and the failed part is one the spec's
  certification plan named (or any of O2–O4 if it didn't).

Anything else is not red; it's one of the buckets in step 5. In particular, a
failure in `SETUP` or `STEP` on the broken build means the test can't reach
the oracle, and a failure at `O1` means the spec's pre-state is wrong.

For freeze specs, the harness samples thread dumps on timeout; valid red needs
the named thread in the named frame in two dumps at least 5 s apart.

### 4. Green runs

Same command, without the broken zip, `N_green` times. All must pass with
`failurePhase: NONE`, zero `idea.log` `SEVERE` entries with `io.flutter`
frames (the harness fails the run on those during green), and no leftover
`flutter`/`dart` processes. On Linux, attach the walkthrough recorder to the
last green run; it costs no extra boot:

```bash
.agents/skills/record-test-walkthrough/scripts/record_walkthrough.sh --test <fqcn> -- \
  ./gradlew --no-daemon integration --tests <fqcn> -Pide.version=<pinned>
```

### 5. Classify and repair

Every non-passing run goes in exactly one bucket, from `run-result.json`:

| Bucket | Signs | Action |
|---|---|---|
| **Environment** | `PREFLIGHT` phase; download, SDK, display, or build failure | Report and stop. No repair |
| **Test defect** | `SETUP` or `STEP` failure; `testsAborted > 0`; a locator not found; flaky green; the broken build *passing* | Repair, within budget |
| **Product behavior** | `ORACLE` or `FREEZE` after all steps passed | On the broken build: valid red. On the real build: a real finding; report it and stop |

Repair budget: **3 iterations or about 45 minutes**, whichever first. Repair
runs are `N = 1`. What a repair may touch: locators, waits (condition-based
only), setup steps, overlay files, the harness. What it may not touch: the
spec, any predicate inside an `assertOracle` block, the `idea.log` allowlist.

**Any edit to the test or overlay changes the bundle hash and invalidates the
red run.** After a repair, go back to step 3. If the repaired test passes on
the broken build, stop: the repair weakened the test, even if you don't see
how, and the user decides what happens next.

When the budget runs out, stop and report with the evidence so far.

### 6. Evidence report

Write to the directory the user names (default `~/agent-testing-evidence/<test>-<timestamp>/`), outside the repo:

```
report.md            summary table (below) + what was repaired and why
spec.md              copy of the spec
bundle.sha256
broken-build.diff    the revert, merge-base range, or fault patch
red/run-result.json  + idea.log slice + screenshots
green-1..N/          run-result.json each
walkthrough/         from the last green run (Linux)
git-status.txt       `git status --porcelain src resources` after everything: must be empty
```

Summary table in `report.md`:

| Field | Value |
|---|---|
| Test / spec | |
| Mode | certify / repro / check |
| IDE build | pinned version |
| Bundle hash | |
| Red | strategy; failed at `O3`/`O4`; wall-clock |
| Green | 3/3; wall-clock each |
| Repairs | count, what changed, why |
| Plugin-off control | failed as expected / not run |
| Verdict | **certified** / unvalidated-repro / flaky / product finding / blocked |
| Suggested commit message | |

Report to the user with the verdict and the path. Commit to the local branch
only if asked. Never push.

## Hard rules

- Never edit the spec or any `assertOracle` predicate. Repairing is not
  re-specifying.
- Never accept a red run that the parser rejects, and never classify by
  reading the log instead of `run-result.json`.
- Never run green before red in `certify`.
- Never leave a worktree, a broken zip in the working tree, or a change in
  `src/` or `resources/` behind. The last thing you do is check `git status`.
- Never push, open PRs, or comment on issues.
