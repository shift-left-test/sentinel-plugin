# Sentinel Jenkins Plugin

## Project Overview

A Jenkins Pipeline plugin that parallelizes [sentinel](https://github.com/shift-left-test/sentinel) mutation testing across multiple nodes using `--partition` and `--merge-partition`, reducing overall execution time by ~1/N.

- **Pipeline-only** (no Freestyle support)
- **API style**: `script { }` + closure (industry standard for distributed Jenkins plugins)
- **Design spec**: `docs/superpowers/specs/2026-04-12-sentinel-plugin-design.md`

## Tech Stack

| Item | Choice |
|------|--------|
| Jenkins | 2.479.x LTS minimum |
| Java | 17 |
| Build | Maven (jenkins-plugin-parent POM) |
| sentinel | Pre-installed on all nodes, configurable path |

## Architecture

Three Pipeline steps:

- **`sentinelPipeline`** — Main orchestrator. Takes a closure that runs on each partition node. Handles parallel execution, stash/unstash, merge, report generation, and threshold judgment.
- **`sentinelRun`** — Runs sentinel on a single partition. Used inside `sentinelPipeline` closure (auto-injected config) or standalone (manual mode).
- **`sentinelMerge`** — Merges partition results manually. For advanced use cases only.

### Key Design Decisions

- **Merge and report are separate sentinel commands**: `--merge-partition` only merges and exits; `--output-dir` runs separately for report generation.
- **Plugin owns threshold judgment**: Never pass `--threshold` to sentinel. Plugin parses `mutations.xml` and sets build result to FAILURE/UNSTABLE.
- **Source code required on merge node**: HTML reports embed source code. User must `checkout scm` before `sentinelPipeline`.
- **Partition node allocation**: Plugin wraps each closure in `node(nodeLabel) { ... }` internally.
- **Result parsing**: Parse `mutations.xml` (PITest-compatible XML) from `--output-dir` output. Never parse stdout or workspace internals.
- **stash/unstash** for partition result collection (no hard size limit).
- **Workspace path separation**: Plugin auto-assigns unique workspace paths per partition (`.sentinel-1`, `.sentinel-2`, ...).

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
├── SentinelPipelineStep           # sentinelPipeline step
├── SentinelRunStep                # sentinelRun step
├── SentinelMergeStep              # sentinelMerge step
├── SentinelOrchestrator           # Parallel dispatch, collect, merge, report
├── SentinelCommandBuilder         # Builds sentinel CLI commands
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
- `@DataBoundConstructor` for required fields, `@DataBoundSetter` for optional
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

- `SentinelRunStep.partition`: string `"1/4"` format (partition index/total)
- `SentinelMergeStep.partitions`: `List<String>` of workspace paths (`.sentinel-1`, `.sentinel-2`)
- `SentinelPipelineStep.partitions`: `int` count (auto-assigns paths internally)
- MutationScore formula: `killed / (killed + survived) * 100` (skipped excluded from denominator)
- Threshold is optional; if set, `thresholdAction` is required (paired validation)

## Docker

- 3-stage build: `dev` (JDK+Maven) → `build` (compile) → `package` (minimal, .hpi only)
- `docker build --target dev` for development environment
- Maven cache: `--mount=type=cache,target=/root/.m2/repository`

## Workflow Rules

- Always summarize what you plan to implement and get approval before writing code.
- All sentinel CLI options must be configurable via plugin parameters (see design spec Section 4).
- Never pass `--threshold` to sentinel — plugin handles threshold judgment from mutations.xml.
- Merge node = pipeline's current agent. Partition nodes = allocated via `nodeLabel`.
