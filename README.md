# Sentinel Jenkins Plugin

A Jenkins Pipeline plugin for **[sentinel](https://github.com/shift-left-test/sentinel) mutation testing**. Provides two composable pipeline steps (`sentinelRun` + `sentinelReport`) that integrate with standard Jenkins Declarative Pipeline for distributed execution and reporting.

## What It Does

[Sentinel](https://github.com/shift-left-test/sentinel) is a mutation testing tool for C/C++ projects. It injects small faults (mutants) into your source code and checks whether your tests catch them. The **mutation score** tells you how effective your tests are.

This plugin provides:

1. **`sentinelRun`** — Runs sentinel with configuration from `SENTINEL_*` environment variables. Auto-stashes results.
2. **`sentinelReport`** — Unstashes results, merges partitions, generates reports, applies threshold judgment, and displays results in Jenkins UI.

For distributed execution, combine with standard Jenkins `parallel` stages to split work across multiple nodes, reducing execution time by ~1/N.

## Requirements

- Jenkins 2.479.x LTS or newer
- Java 17+
- Maven 3.9+
- sentinel installed on all Jenkins nodes (or available via Docker)

## Quick Start

### Single Node

The simplest usage — runs sentinel on the current agent:

```groovy
pipeline {
    agent { label 'linux' }
    environment {
        SENTINEL_BUILD_COMMAND = 'make all'
        SENTINEL_TEST_COMMAND = 'make test'
        SENTINEL_TEST_RESULT_DIR = 'test-results/'
    }
    stages {
        stage('Mutation Test') {
            steps {
                checkout scm
                sentinelRun()
            }
        }
        stage('Report') {
            steps {
                sentinelReport(
                    threshold: 80.0,
                    thresholdAction: 'UNSTABLE'
                )
            }
        }
    }
}
```

### Distributed (single source of truth)

Project settings (build/test commands, patterns, …) live in a repo `sentinel.yaml`,
so the Jenkinsfile only carries the Jenkins-side orchestration: how many partitions.

`sentinel.yaml` (in the repo root):

```yaml
build-command: make all
test-command: make test
test-result-dir: test-results/
```

`Jenkinsfile` — one variable `n` drives both the branch count and the
sentinel `--partition` denominator, so they can never drift:

```groovy
def n = 4                                  // the only place the partition count lives
pipeline {
    agent none
    stages {
        stage('Mutation') {
            steps {
                script {
                    def branches = [:]
                    for (int i = 1; i <= n; i++) {
                        int idx = i
                        branches["Partition ${idx}"] = {
                            node('linux') {
                                checkout scm
                                sentinelRun(partitionIndex: idx, partitionTotal: n)
                            }
                        }
                    }
                    parallel branches
                }
            }
        }
        stage('Report') {
            agent { label 'linux' }
            steps {
                checkout scm
                sentinelReport(partitionTotal: n,
                               threshold: 80.0, thresholdAction: 'UNSTABLE')
            }
        }
    }
}
```

**Why this shape:**

- `n` is the only place the partition count lives — the loop and `partitionTotal`
  derive from it, so a mismatched count is impossible. `def n` sits above `pipeline`
  so every stage (including Report) can read it.
- The branch map is built with a plain `for` loop, **not** `(1..n)`: a Groovy
  `IntRange` cannot be serialized by the Pipeline CPS engine when `sentinelRun`
  checkpoints state, so a range-based recipe fails mid-run. The `for` loop avoids it,
  and `int idx = i` captures the index per branch so every closure gets its own value.
- Fan-out is owned by Jenkins' own `parallel`/`node` (the plugin never allocates
  nodes), so node allocation, abort, and restart stay with the engine.
- The Report stage needs `checkout scm` because sentinel embeds source code into
  the HTML report.
- **Shared seed:** sentinel divides mutants by `--partition=i/n`, so every partition
  must select mutants identically for the split to be consistent. Pin a fixed seed
  shared by all partitions — e.g. a fixed `seed` in `sentinel.yaml`, or
  `environment { SENTINEL_SEED = '...' }` (evaluated once, inherited by all stages).
  Without a shared fixed seed the partitions can overlap or drop mutants.

#### Passing build/test commands — three options

| | Where settings live | Needs env? |
|---|---|---|
| **A (recommended)** | `sentinel.yaml` in the repo | no |
| **B** | step params: `sentinelRun(buildCommand: …, testCommand: …, testResultDir: …)` | no |
| **C** | `environment { SENTINEL_* }` block | yes |

`SENTINEL_*` environment variables remain fully supported (option C) and are handy
for the `matrix` recipe below or for run-scoped shared values (e.g. `SENTINEL_SEED`).
In the single-source recipe above, option A or B means no environment variables are
needed at all.

#### Concise alternative: `matrix` (counts NOT auto-checked)

```groovy
matrix {
    axes { axis { name 'PARTITION'; values '1', '2', '3', '4' } }
    agent { label 'linux' }
    stages {
        stage('Run') {
            steps {
                checkout scm
                sentinelRun(partitionIndex: env.PARTITION.toInteger(), partitionTotal: 4)
            }
        }
    }
}
```

> **Warning:** the number of `axis` values and `partitionTotal` (or
> `SENTINEL_PARTITION_TOTAL`) must match — nothing enforces this. A mismatch makes
> `--partition=i/total` wrong and silently overlaps or drops mutants. The plugin
> fails the report loudly if a partition is missing, but cannot *prevent* the
> mismatch. Prefer the single-source recipe when correctness matters. (`matrix`
> cells also do not get per-cell `post {}`.)

### Docker

Use Docker agents with the same pattern:

```groovy
stage('Partition 1') {
    agent {
        docker {
            image 'my-build-env:latest'
            label 'linux'
        }
    }
    steps {
        checkout scm
        sentinelRun(partitionIndex: 1)
    }
}
```

## Pipeline Steps

### `sentinelRun`

Runs sentinel mutation testing. All parameters are optional — configuration comes from `SENTINEL_*` environment variables, with step parameters overriding env vars when both are set.

**Step-specific parameter:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `partitionIndex` | int | Partition index (1-based). Combined with `SENTINEL_PARTITION_TOTAL` env var. |
| `partitionTotal` | int | Total number of partitions. Overrides `SENTINEL_PARTITION_TOTAL`. |

**Override parameters** (override env vars when set):

| Parameter | Type | Env Var | Description |
|-----------|------|---------|-------------|
| `buildCommand` | String | `SENTINEL_BUILD_COMMAND` | Build command (e.g., `make all`) |
| `testCommand` | String | `SENTINEL_TEST_COMMAND` | Test command (e.g., `make test`) |
| `testResultDir` | String | `SENTINEL_TEST_RESULT_DIR` | Test result directory |
| `sourceDir` | String | `SENTINEL_SOURCE_DIR` | Source root directory |
| `seed` | long | `SENTINEL_SEED` | Random seed for reproducibility |
| `verbose` | boolean | `SENTINEL_VERBOSE` | Show detailed output |
| `workspace` | String | `SENTINEL_WORKSPACE` | Sentinel workspace directory |
| `sentinelPath` | String | `SENTINEL_PATH` | Path to sentinel executable |

### `sentinelReport`

Collects results, merges partitions, generates reports, and applies threshold judgment.

| Parameter | Type | Env Var | Default | Description |
|-----------|------|---------|---------|-------------|
| `threshold` | double | - | - | Minimum mutation score (0.0-100.0) |
| `thresholdAction` | String | - | - | Action on failure: `FAILURE` or `UNSTABLE` |
| `partitionTotal` | int | `SENTINEL_PARTITION_TOTAL` | - | Number of partitions to collect and merge. Overrides the env var. |
| `sourceDir` | String | `SENTINEL_SOURCE_DIR` | `.` | Source directory for HTML reports |
| `outputDir` | String | `SENTINEL_OUTPUT_DIR` | `sentinel-report` | Report output directory |
| `sentinelPath` | String | `SENTINEL_PATH` | `sentinel` | Path to sentinel executable |

**Behavior:**
- If `SENTINEL_PARTITION_TOTAL` is set: unstashes all partition results, runs `sentinel --merge-partition`, then generates reports.
- If not set: unstashes a single result and generates reports directly.
- Before unstash/merge/report, the plugin recreates only its default or auto-assigned working directories to avoid stale files from earlier builds.
- Threshold judgment: if score < threshold, sets build result to `FAILURE` or `UNSTABLE`.

## Environment Variables

All sentinel CLI options can be configured via `SENTINEL_*` environment variables in the pipeline `environment` block:

> Environment variables are **one of three ways** to configure sentinel (the others
> are a repo `sentinel.yaml` and step parameters). They are most useful for sharing
> config across the `matrix` recipe or run-scoped values like `SENTINEL_SEED`. With
> the single-source recipe, `sentinel.yaml` or step parameters mean no `SENTINEL_*`
> variables are required.

| Variable | sentinel CLI Option | Type |
|----------|---------------------|------|
| `SENTINEL_BUILD_COMMAND` | `--build-command` | String (required) |
| `SENTINEL_TEST_COMMAND` | `--test-command` | String (required) |
| `SENTINEL_TEST_RESULT_DIR` | `--test-result-dir` | String (required) |
| `SENTINEL_PARTITION_TOTAL` | `--partition` (denominator) | Integer |
| `SENTINEL_SEED` | `--seed` | Long |
| `SENTINEL_SOURCE_DIR` | `--source-dir` | String |
| `SENTINEL_COMPILE_DB_DIR` | `--compiledb-dir` | String |
| `SENTINEL_TIMEOUT` | `--timeout` | Integer (seconds) |
| `SENTINEL_FROM` | `--from` | String (revision) |
| `SENTINEL_UNCOMMITTED` | `--uncommitted` | `true` / `false` |
| `SENTINEL_PATTERNS` | `--pattern` (repeated) | Comma-separated |
| `SENTINEL_EXTENSIONS` | `--extension` (repeated) | Comma-separated |
| `SENTINEL_GENERATOR` | `--generator` | `uniform`, `random`, `weighted` |
| `SENTINEL_MUTANTS_PER_LINE` | `--mutants-per-line` | Integer |
| `SENTINEL_OPERATORS` | `--operator` (repeated) | Comma-separated |
| `SENTINEL_LIMIT` | `--limit` | Integer |
| `SENTINEL_LCOV_TRACEFILES` | `--lcov-tracefile` (repeated) | Comma-separated |
| `SENTINEL_CONFIG` | `--config` | String (path) |
| `SENTINEL_CLEAN` | `--clean` | `true` / `false` |
| `SENTINEL_DRY_RUN` | `--dry-run` | `true` / `false` |
| `SENTINEL_VERBOSE` | `--verbose` | `true` / `false` |
| `SENTINEL_WORKSPACE` | `--workspace` | String |
| `SENTINEL_OUTPUT_DIR` | `--output-dir` | String |
| `SENTINEL_PATH` | sentinel executable path | String |

### Validation

- A **malformed value** for a known variable fails the build with a clear
  message naming the variable — for example `SENTINEL_TIMEOUT must be an
  integer, got: '2h'`. Bad values are never silently ignored.
- An **unrecognized `SENTINEL_*` variable** (usually a typo such as
  `SENTINEL_TIMOUT`) is reported as a warning and ignored; it does not fail
  the build, so unrelated `SENTINEL_`-prefixed variables stay safe.

## Build Results & Reporting

After a mutation test run completes, the plugin provides:

- **Build page summary** — a compact card on the build page showing overall mutation score with a stacked bar (killed/survived/skipped)
- **Mutation Report** — a detailed tabbed report page accessible via the build sidebar:
  - **Overview** tab: mutator type distribution (donut chart)
  - **Files** tab: per-file scores with inline progress bars
  - **Mutations** tab: full mutation detail table with status/file filtering
- **Mutation Score Trend** — a line chart on the project page sidebar showing score progression over recent builds

All charts are rendered using [ECharts](https://echarts.apache.org/) via the Jenkins echarts-api plugin.

### Mutation status

Each mutation is classified as one of three statuses, used consistently across the summary, the Mutations tab filter, and the score:

- **KILLED** — a test detected the mutation (good).
- **SURVIVED** — no test detected the mutation; a test gap to address.
- **SKIPPED** — the mutation could not be evaluated (build failure, timeout, or runtime error).

The mutation score is `killed / (killed + survived) × 100`. **SKIPPED mutations are excluded from the denominator**, so they do not lower the score.

### HTML report and Content-Security-Policy

The **View HTML Report** link serves sentinel's generated HTML from the build's archived report directory. It is served under the same Content-Security-Policy that Jenkins applies to browsed workspace and artifact files, which by default blocks inline scripts and styles. If your sentinel HTML report renders incorrectly because of this, relax it via the standard `hudson.model.DirectoryBrowserSupport.CSP` system property (the same property used by other report-publishing plugins).

## Global Configuration

In **Manage Jenkins > System**, you can set the default sentinel executable path. This is used when neither `sentinelPath` step parameter nor `SENTINEL_PATH` environment variable is specified.

## Quality Gate

When `threshold` and `thresholdAction` are set on `sentinelReport`:

- If the mutation score is **below the threshold**, the build result is set to `FAILURE` or `UNSTABLE` depending on `thresholdAction`.
- If the mutation score **meets or exceeds the threshold**, the build result is not affected.
- If neither is set, the mutation score is reported but does not affect the build result.

## Reliability & Cancellation

Mutation testing is long-running: `sentinelRun` executes your build and test commands once per mutant. A few practices keep builds cancellable and prevent stuck jobs:

- **Wrap the steps in `timeout {}`.** This is the recommended way to bound a run that never finishes (for example, a mutant that makes a test deadlock). On abort or timeout the plugin kills the running sentinel process (and its child build/test processes) on the agent, so cancellation is reliable while the agent connection is alive:

  ```groovy
  stage('Mutation Test') {
      steps {
          checkout scm
          timeout(time: 2, unit: 'HOURS') {
              sentinelRun()
          }
      }
  }
  ```

  `SENTINEL_TIMEOUT` is **not** a substitute — it is sentinel's *per-mutant* `--timeout`, not a wall-clock limit on the whole step.

- **Size executors and agents for the workload.** Each `sentinelRun`/`sentinelReport` holds the node's executor for the entire run. With too few executors on a label, one long partition can leave other builds queued (and appearing not to start) until it finishes. Provision enough executors/agents per label.

- **Keep the remoting ping enabled.** On a remote agent, cancellation relies on the controller↔agent connection. If that connection dies silently (spot reclaim, network blip, paused VM), Jenkins detects it via the remoting ping (`hudson.slaves.ChannelPinger`). Do not disable it, so a dead connection is reaped and the step can be aborted.

- **Avoid restarting the controller mid-run.** These steps do not resume across a controller restart — a restart while a run is in progress fails that step, and a sentinel process already launched on an agent may keep running. Prefer Jenkins' *safe* restart (which waits for running steps), or abort the build first.

## Development

### Docker

```bash
# Development environment — opens a bash shell with JDK 17 + Maven
docker build --target dev -t sentinel-dev .
docker run -it -v $(pwd):/workspace sentinel-dev

# Build and test inside Docker
docker build --target build -t sentinel-build .

# Build with static analysis
docker build --target build --build-arg MAVEN_GOALS="clean verify -Pstatic-analysis" .

# Extract .hpi artifact from the package stage
docker build -t sentinel .
docker cp $(docker create sentinel):/opt/sentinel.hpi .
```

### Building from Source

```bash
# Requires Java 17+ and Maven 3.9+
mvn clean verify

# With static analysis (Checkstyle, SpotBugs, PMD, Error Prone, etc.)
mvn clean verify -Pstatic-analysis
```

### Testing Locally

You can launch a local Jenkins instance with the plugin pre-installed:

```bash
mvn hpi:run
```

This starts Jenkins at `http://localhost:8080/jenkins/` with the plugin loaded. Create a Pipeline job to test `sentinelRun` and `sentinelReport` steps interactively. To use a different port:

```bash
mvn hpi:run -Dport=9090
```

## License

The project source code is available under the MIT license. See [LICENSE](LICENSE).
