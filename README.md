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

### Distributed (4 Partitions)

Split mutation testing across multiple nodes for faster execution:

```groovy
pipeline {
    agent none
    environment {
        SENTINEL_BUILD_COMMAND = 'make all'
        SENTINEL_TEST_COMMAND = 'make test'
        SENTINEL_TEST_RESULT_DIR = 'test-results/'
        SENTINEL_SEED = "${System.currentTimeMillis()}"
        SENTINEL_PARTITION_TOTAL = '4'
    }
    stages {
        stage('Partition') {
            parallel {
                stage('Partition 1') {
                    agent { label 'linux' }
                    steps {
                        checkout scm
                        sentinelRun(partitionIndex: 1)
                    }
                }
                stage('Partition 2') {
                    agent { label 'linux' }
                    steps {
                        checkout scm
                        sentinelRun(partitionIndex: 2)
                    }
                }
                stage('Partition 3') {
                    agent { label 'linux' }
                    steps {
                        checkout scm
                        sentinelRun(partitionIndex: 3)
                    }
                }
                stage('Partition 4') {
                    agent { label 'linux' }
                    steps {
                        checkout scm
                        sentinelRun(partitionIndex: 4)
                    }
                }
            }
        }
        stage('Report') {
            agent { label 'linux' }
            steps {
                checkout scm
                sentinelReport(
                    threshold: 80.0,
                    thresholdAction: 'UNSTABLE'
                )
            }
        }
    }
}
```

**How it works:**

- Shared configuration lives in the `environment` block — all stages inherit it.
- Each partition stage only differs by `partitionIndex`.
- `sentinelRun` automatically stashes results; `sentinelReport` automatically unstashes, merges, generates reports, and applies threshold judgment.
- The Report stage needs `checkout scm` because HTML reports embed source code.
- All partitions must share the same `SENTINEL_SEED` for merge to work.

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
| `sourceDir` | String | `SENTINEL_SOURCE_DIR` | `.` | Source directory for HTML reports |
| `outputDir` | String | `SENTINEL_OUTPUT_DIR` | `sentinel-report` | Report output directory |
| `sentinelPath` | String | `SENTINEL_PATH` | `sentinel` | Path to sentinel executable |

**Behavior:**
- If `SENTINEL_PARTITION_TOTAL` is set: unstashes all partition results, runs `sentinel --merge-partition`, then generates reports.
- If not set: unstashes a single result and generates reports directly.
- Threshold judgment: if score < threshold, sets build result to `FAILURE` or `UNSTABLE`.

## Environment Variables

All sentinel CLI options can be configured via `SENTINEL_*` environment variables in the pipeline `environment` block:

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

## Build Results & Reporting

After a mutation test run completes, the plugin provides:

- **Build page summary** — a compact card on the build page showing overall mutation score with a stacked bar (killed/survived/skipped)
- **Mutation Report** — a detailed tabbed report page accessible via the build sidebar:
  - **Overview** tab: mutator type distribution (donut chart)
  - **Files** tab: per-file scores with inline progress bars
  - **Mutations** tab: full mutation detail table with status/file filtering
- **Mutation Score Trend** — a line chart on the project page sidebar showing score progression over recent builds

All charts are rendered using [ECharts](https://echarts.apache.org/) via the Jenkins echarts-api plugin.

## Global Configuration

In **Manage Jenkins > System**, you can set the default sentinel executable path. This is used when neither `sentinelPath` step parameter nor `SENTINEL_PATH` environment variable is specified.

## Quality Gate

When `threshold` and `thresholdAction` are set on `sentinelReport`:

- If the mutation score is **below the threshold**, the build result is set to `FAILURE` or `UNSTABLE` depending on `thresholdAction`.
- If the mutation score **meets or exceeds the threshold**, the build result is not affected.
- If neither is set, the mutation score is reported but does not affect the build result.

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
