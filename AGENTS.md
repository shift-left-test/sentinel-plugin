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

## Static Analysis (9 tools)

All integrated via `static-analysis` Maven profile:

| Tool | Phase | Purpose |
|------|-------|---------|
| Maven Enforcer | validate | Build rules (Java 17, Maven version) |
| Error Prone | compile | Compile-time bug detection |
| Modernizer | compile | Legacy API detection |
| JaCoCo | test | Code coverage |
| Checkstyle | verify | Code style |
| SpotBugs | verify | Bytecode bug detection |
| PMD | verify | Code quality patterns |
| OWASP Dependency-Check | verify | Dependency vulnerability scan |
| Javadoc | verify | API doc completeness |

## Package Structure

```
io.jenkins.plugins.sentinel
├── SentinelGlobalConfiguration    # Global config (sentinel path)
├── SentinelRunStep                # sentinelRun step (env var config, auto-stash)
├── SentinelReportStep             # sentinelReport step (unstash, merge, report, threshold)
├── SentinelEnvironment            # SENTINEL_* env var mapping, naming conventions
├── SentinelCommandBuilder         # Builds sentinel CLI commands
├── SentinelRunner                 # Executes sentinel CLI via Jenkins launcher
├── SentinelPostProcessor          # Merge, report generation, threshold judgment
├── SentinelResultParser           # Parses mutations.xml
├── SentinelBuildAction            # Build page: summary widget, report page (RunAction2)
├── SentinelProjectAction          # Project page: mutation score trend chart
├── SentinelProjectActionFactory   # TransientActionFactory for SentinelProjectAction
├── config/
│   ├── SentinelConfiguration      # Config data class
│   ├── SentinelConfigValidator    # Config validation
│   └── ThresholdAction            # Enum: FAILURE, UNSTABLE
└── model/
    ├── SentinelResult             # Result data model
    ├── MutationScore              # Mutation score model
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
- ECharts charts: pass JSON via `data-*` attributes, load via `<st:adjunct includes="io.jenkins.plugins.echarts"/>`

## PMD/SpotBugs Exclusion Rationale

- Pipeline steps have many public fields (maps to sentinel CLI options) — GodClass/TooManyFields excluded
- StepExecution.run() requires `throws Exception` — SignatureDeclareThrowsException excluded
- Inner classes in steps need outer reference for CPS context — SE_INNER_CLASS excluded

## Key Semantics

- `SentinelRunStep.partitionIndex`: Integer (1-based). Combined with `SENTINEL_PARTITION_TOTAL` env var to form `--partition index/total`.
- `SentinelConfiguration.getPartitionSpec()`: derives `"index/total"` string from `partitionIndex` + `partitionTotal`.
- `SentinelEnvironment`: single source of truth for env var names, stash names, workspace paths.
- MutationScore formula: `killed / (killed + survived) * 100` (skipped excluded from denominator)
- Threshold is optional; if set, `thresholdAction` is required (paired validation)

## Docker

- 3-stage build: `dev` (JDK+Maven) → `build` (compile) → `package` (minimal, .hpi only)
- `docker build --target dev` for development environment
- Maven cache: `--mount=type=cache,target=/root/.m2/repository`

## Workflow Rules

- Always summarize what you plan to implement and get approval before writing code.
- All sentinel CLI options must be configurable via `SENTINEL_*` env vars and step parameters.
- Never pass `--threshold` to sentinel — plugin handles threshold judgment from mutations.xml.
- Report node = pipeline's current agent. Partition nodes = allocated by user via standard Jenkins `agent`/`node` directives.
- When writing new code or modifying existing code:
  1. Run the `simplify` skill for code review.
  2. Run all static analysis including Javadoc (`mvn clean verify -Pstatic-analysis`) and fix all issues.
  3. Update `README.md` to reflect any user-facing changes.
