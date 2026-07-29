# Sentinel Jenkins Plugin

## Project Overview

A Jenkins Pipeline plugin for [sentinel](https://github.com/shift-left-test/sentinel) mutation testing. Provides two composable pipeline steps (`sentinelRun` + `sentinelReport`) that users combine with standard Jenkins Declarative Pipeline constructs for distributed execution.

- **Pipeline-only** (no Freestyle support)
- **API style**: Composable steps with `SENTINEL_*` environment variable configuration

## Tech Stack

| Item | Choice |
|------|--------|
| Jenkins | 2.479.x LTS minimum |
| Java | 17 |
| Build | Maven (jenkins-plugin-parent POM) |
| sentinel | Pre-installed on all nodes, configurable path |

## Architecture

Two composable Pipeline steps:

- **`sentinelRun`** — Runs sentinel. Reads config from `SENTINEL_*` env vars, step params override. Auto-stashes results for `sentinelReport`. Accepts `partitionIndex` for distributed execution.
- **`sentinelReport`** — Unstashes results, merges partitions (if `SENTINEL_PARTITION_TOTAL` set), generates reports, parses `mutations.xml`, attaches build action, applies threshold judgment.

### Key Design Decisions

- **Composable steps, not orchestrator**: Users compose `sentinelRun` + `sentinelReport` with standard Jenkins `parallel`, `node`, `agent` directives. No closure-based orchestration.
- **Environment variable configuration**: All sentinel CLI options configurable via `SENTINEL_*` env vars in Declarative Pipeline `environment {}` block. Step params override env vars.
- **Merge and report are separate sentinel commands**: `--merge-partition` only merges and exits; `--output-dir` runs separately for report generation.
- **Plugin owns threshold judgment**: Never pass `--threshold` to sentinel. Plugin parses `mutations.xml` and sets build result to FAILURE/UNSTABLE.
- **Source code required on report node**: HTML reports embed source code. User must `checkout scm` before `sentinelReport`.
- **Result parsing**: Parse `mutations.xml` (PITest-compatible XML) from `--output-dir` output. Never parse stdout or workspace internals.
- **stash/unstash handled by plugin**: `sentinelRun` auto-stashes, `sentinelReport` auto-unstashes. Users don't manage stash names.
- **Workspace path separation**: Plugin auto-assigns unique workspace paths per partition (`.sentinel-1`, `.sentinel-2`, ...).
- **partitionIndex is a step param, not env var**: Each parallel stage specifies its own `partitionIndex`. `SENTINEL_PARTITION_TOTAL` is shared via env var.

## Build & Test

```bash
# Basic build
mvn clean verify

# Build with all static analysis
mvn clean verify -Pstatic-analysis

# Run individual analysis
mvn checkstyle:check
mvn spotbugs:check
mvn pmd:check
```

## Code Style (Checkstyle enforced)

- No wildcard imports, no unused imports
- No tabs, no trailing whitespace, newline at EOF
- Max 700 lines per file
- Braces required on all blocks (`if`, `for`, etc.)
- No magic numbers in production code (tests exempt)
- `equals()` must pair with `hashCode()`

## Test Conventions

- JUnit 5 + AssertJ + Mockito
- One test class per production class
- Descriptive method names: `validConfigPasses()`, `throwsOnMissingFile()`
- AssertJ fluent: `assertThat(...).isEqualTo()`, `isCloseTo(val, within(0.01))`
- Error assertions: `assertThatThrownBy(...).isInstanceOf().hasMessageContaining()`
- `@TempDir` for file system tests, static final constants for test data

## Static Analysis

`license-maven-plugin` and Maven Enforcer run in every build. The rest are
in the `static-analysis` Maven profile:

| Tool | Phase | Purpose |
|------|-------|---------|
| Maven Enforcer | validate | Build rules (Java 17, Maven version) — always on |
| License header check | verify | MIT header on every source file — always on |
| Error Prone | compile | Compile-time bug detection |
| Modernizer | compile | Legacy API detection |
| JaCoCo | test + verify | Coverage report **and** a `check` floor |
| JXR | generate-sources | Source cross-reference for the PMD/Checkstyle reports |
| Checkstyle | verify | Code style |
| SpotBugs | verify | Bytecode bug detection |
| PMD | verify | Code quality patterns |
| Javadoc | verify | Doc correctness (`failOnWarnings`) |

There is **no** dependency vulnerability scanner wired in. To add one,
put `dependency-check-maven` in its own opt-in profile — it needs an NVD
API key and a multi-minute database download, so it must not sit in the
profile developers run on every change.

### Coverage floors

`jacoco:check` enforces BUNDLE minimums (instruction 0.95, branch 0.90,
line 0.95), set just under what the suite achieves. Raise them when
coverage rises; never lower them to make a build pass.

### Javadoc is a real check

`failOnError` **and** `failOnWarnings` are true, so a broken `{@link}`
or a malformed tag fails the build. `doclint` keeps `-missing`: the point
is that the comments which exist are correct, not that every
package-private helper has one.

## Package Structure

```
io.jenkins.plugins.sentinel
├── SentinelGlobalConfiguration    # Global config (sentinel path)
├── SentinelRunStep                # sentinelRun step (env var config, auto-stash)
├── SentinelReportStep             # sentinelReport step (unstash, merge, report, threshold)
├── SentinelEnvironment            # SENTINEL_* env var mapping, naming conventions
├── SentinelSeed                   # Per-build fallback seed derived from the run ID
├── SentinelCommandBuilder         # Builds sentinel CLI commands
├── SentinelRunner                 # Executes sentinel CLI via Jenkins launcher
├── SentinelPostProcessor          # Merge, report generation, threshold judgment
├── SentinelResultParser           # Parses mutations.xml
├── SentinelBuildAction            # Build page: summary widget, report page (RunAction2)
├── SentinelProjectAction          # Project page: mutation score trend chart
├── SentinelProjectActionFactory   # TransientActionFactory for SentinelProjectAction
├── SentinelStepExecution          # Base execution: REQUIRED_CONTEXT, inputs(), abort handling
├── SentinelProcHandle             # Holds the running Proc so stop() can kill it
├── SentinelWorkspaceCleaner       # Recreates only plugin-managed directories
├── config/
│   ├── SentinelConfiguration      # Config data class
│   ├── SentinelConfigValidator    # Config validation
│   └── ThresholdAction            # Enum: FAILURE, UNSTABLE
└── model/
    ├── SentinelResult             # Result data model
    ├── MutationScore              # Mutation score model + score/percent formatting
    ├── MutationEntry              # One mutation parsed from mutations.xml
    └── FileMutationResult         # Per-file result model
```

## Jenkins Plugin Patterns

- All step/model classes must be `Serializable` (CPS pipeline requirement)
- `@DataBoundConstructor` with no-arg constructor, `@DataBoundSetter` for all optional fields (env vars provide defaults)
- `load()` in GlobalConfiguration constructor is standard Jenkins pattern (SpotBugs excluded)
- Jelly templates use `escape-by-default='true'` for XSS protection
- `listener.getLogger()` is standard Jenkins logging pattern
- `TransientActionFactory` for project-level actions (no manual registration needed)
- `RunAction2` for build actions that need persisted `Run` reference
- ECharts charts: pass JSON via `data-*` attributes; load the library via `<script src="${rootURL}/plugin/echarts-api/js/echarts.min.js"/>` (echarts-api ships no `io.jenkins.plugins.echarts` adjunct)
- No inline `<script>` or inline event handlers in Jelly — Jenkins enforces CSP `script-src 'self'` on plugin views; all client JS lives in the `io.jenkins.plugins.sentinel.charts` adjunct
- In `floatingBox.jelly`, `it` is the Job, not the action (core taglib includes it via `st:include from=` only) — reference the action as `${from}`; `summary.jelly` does get `it` = action
- No `String.format(...)` in Jelly — JEXL cannot call static methods and silently renders empty; format in Java getters (e.g. `MutationScore.formattedScore()`)
- **Declaring `annotationProcessorPaths` disables classpath processor
  discovery.** Adding Error Prone there switches javac to `-processorpath`,
  which silently drops SezPoz and the Jenkins annotation-indexer, so
  `META-INF/annotations/hudson.Extension` never gets written and every
  `@Extension` (steps, descriptors) is undiscoverable at runtime — the
  failure shows up as "No such DSL method", not as a compile error. The
  pom lists both processors explicitly; leave them there. Versions come
  from the parent POM's `dependencyManagement`, so do not pin them.

## PMD/SpotBugs Exclusion Rationale

- Pipeline steps have many public fields (maps to sentinel CLI options) — GodClass/TooManyFields excluded
- StepExecution.run() requires `throws Exception` — SignatureDeclareThrowsException excluded
- Inner classes in steps need outer reference for CPS context — SE_INNER_CLASS excluded

## Key Semantics

- `SentinelRunStep.partitionIndex`: Integer (1-based). Combined with `SENTINEL_PARTITION_TOTAL` env var to form `--partition index/total`.
- `SentinelConfiguration.getPartitionSpec()`: derives `"index/total"` string from `partitionIndex` + `partitionTotal`.
- `SentinelEnvironment`: single source of truth for env var names, stash names, workspace paths.
- MutationScore formula: `killed / (killed + survived) * 100` (skipped excluded from denominator)
- Seed resolution: step param > `SENTINEL_SEED` > derived from
  `Run.getExternalizableId()` via SHA-256 (`SentinelSeed`, range 0..2^32-1).
  `sentinelRun` always ends up passing `--seed`; all partitions of a build
  share the derived value.
- **Blank means unset.** `SentinelEnvironment.isSet` is the single
  definition: absent, empty, or whitespace-only all mean "the user did not
  choose a value", so the plugin default applies. `toConfiguration`
  normalizes at read time, so no downstream code repeats the check.
- **Both steps build a `SentinelConfiguration` and validate it before
  doing any work.** `sentinelReport` used to skip this, which made the
  threshold range check and the threshold/thresholdAction pairing
  unreachable in production. Never resolve a `sentinelReport` parameter
  outside `toConfiguration`.
- Threshold and `thresholdAction` must be set together — either one alone
  is a quality gate that silently does nothing, so the validator rejects
  both halves.
- **The stash follows the configured workspace**, never a recomputed
  `.sentinel-{index}`. Stash contents are relative to the stash root, so
  `sentinelReport` can still unstash into `.sentinel-{index}` to merge.

## Docker

- 3-stage build: `dev` (JDK+Maven) → `build` (compile) → `package` (minimal, .hpi only)
- `docker build --target dev` for development environment
- Maven cache: `--mount=type=cache,target=/root/.m2/repository`

## Workflow Rules

- Always summarize what you plan to implement and get approval before writing code.
- Every sentinel CLI option must be reachable via a `SENTINEL_*` env var
  (or a repo `sentinel.yaml`). Step parameters cover only the options
  users set per-branch — `sentinelRun` exposes 10 of the 24 options, and
  that is deliberate, not a gap to close reflexively.
- Never pass `--threshold` to sentinel — plugin handles threshold judgment from mutations.xml.
- Report node = pipeline's current agent. Partition nodes = allocated by user via standard Jenkins `agent`/`node` directives.
- When writing new code or modifying existing code:
  1. Run the `simplify` skill for code review.
  2. Run all static analysis including Javadoc (`mvn clean verify -Pstatic-analysis`) and fix all issues.
  3. Update `README.md` to reflect any user-facing changes.
