# Sentinel Jenkins Plugin

A Jenkins Pipeline plugin that **parallelizes [sentinel](https://github.com/shift-left-test/sentinel) mutation testing** across multiple nodes, reducing execution time by distributing partitions and merging results automatically.

## What It Does

[Sentinel](https://github.com/shift-left-test/sentinel) is a mutation testing tool for C/C++ projects. It injects small faults (mutants) into your source code and checks whether your tests catch them. The **mutation score** tells you how effective your tests are.

This plugin makes sentinel faster by:

1. **Splitting** the mutant list into N partitions (`--partition`)
2. **Running** each partition on a separate Jenkins node in parallel
3. **Merging** the results back together (`--merge-partition`)
4. **Reporting** the combined mutation score with HTML reports

A test suite that takes 2 hours with sentinel alone can finish in ~30 minutes with 4 partitions.

## Requirements

- Jenkins 2.479.x LTS or newer
- Java 17+
- Maven 3.9+
- sentinel installed on all Jenkins nodes (or available via Docker)

## Quick Start

### Single Node

The simplest usage — no partitions, runs on the current agent:

```groovy
pipeline {
    agent {
        docker { image 'my-build-env:latest' }
    }
    stages {
        stage('Mutation Test') {
            steps {
                checkout scm
                script {
                    sentinelPipeline(
                        buildCommand: 'make all',
                        testCommand: 'make test',
                        testResultDir: 'test-results/',
                        outputDir: 'sentinel-report'
                    ) {
                        checkout scm
                        sh 'cmake . && make'
                        sentinelRun()
                    }
                }
            }
        }
    }
}
```

### Closure Mode (Recommended)

Distributes work across multiple nodes. The plugin handles partition assignment, stash/unstash, merge, and reporting:

```groovy
pipeline {
    agent {
        docker { image 'my-build-env:latest' }
    }
    stages {
        stage('Mutation Test') {
            steps {
                checkout scm
                script {
                    sentinelPipeline(
                        buildCommand: 'make all',
                        testCommand: 'make test',
                        testResultDir: 'test-results/',
                        partitions: 4,
                        nodeLabel: 'linux',
                        outputDir: 'sentinel-report',
                        threshold: 80.0,
                        thresholdAction: 'UNSTABLE'
                    ) {
                        docker.image('my-build-env:latest').inside {
                            checkout scm
                            sh 'cmake -B build . && cmake --build build -j$(nproc)'
                            sentinelRun()
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            publishHTML(target: [
                reportDir: 'sentinel-report',
                reportFiles: 'index.html',
                reportName: 'Mutation Report'
            ])
        }
    }
}
```

**How it works:**

- The outer `agent` is the **merge node** where results are collected and reports are generated. It needs sentinel installed and source code checked out.
- The `closure` block runs on **each partition node** (allocated via `nodeLabel`). It sets up the build environment and calls `sentinelRun()`.
- The plugin automatically assigns partition numbers, manages stash/unstash, runs merge, generates reports, and applies threshold judgment.

### Manual Mode (Advanced)

For cases where you need different Docker images or configurations per partition:

```groovy
pipeline {
    agent none
    stages {
        stage('Partition Test') {
            parallel {
                stage('Partition 1') {
                    agent { docker { image 'my-build-env:latest' } }
                    steps {
                        checkout scm
                        sh 'cmake . && make'
                        sentinelRun(
                            partition: '1/4',
                            workspace: '.sentinel-1',
                            buildCommand: 'make all',
                            testCommand: 'make test',
                            testResultDir: 'test-results/',
                            seed: 12345
                        )
                        stash name: 'sentinel-1', includes: '.sentinel-1/**'
                    }
                }
                // Repeat for partitions 2, 3, 4...
            }
        }
        stage('Merge & Report') {
            agent { docker { image 'my-build-env:latest' } }
            steps {
                checkout scm
                unstash 'sentinel-1'
                unstash 'sentinel-2'
                unstash 'sentinel-3'
                unstash 'sentinel-4'
                sentinelMerge(
                    partitions: ['.sentinel-1', '.sentinel-2', '.sentinel-3', '.sentinel-4'],
                    workspace: '.sentinel-merged',
                    sourceDir: '.',
                    outputDir: 'sentinel-report',
                    threshold: 80.0,
                    thresholdAction: 'UNSTABLE'
                )
            }
        }
    }
}
```

**Manual mode caveats:**
- Use the **same `seed`** across all partitions (otherwise merge will fail)
- Use **unique `workspace` paths** per partition (otherwise unstash will overwrite)
- All partitions must use the **same sentinel options** (buildCommand, testCommand, etc.)

## Pipeline Steps

### `sentinelPipeline`

The main orchestration step. Distributes partitions, collects results, merges, and reports.

**Required parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `buildCommand` | String | Build command (e.g., `make all`) |
| `testCommand` | String | Test command (e.g., `make test`) |
| `testResultDir` | String | Test result directory |

**Common parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `partitions` | int | `1` | Number of partitions |
| `nodeLabel` | String | `''` | Label for partition nodes |
| `outputDir` | String | - | Report output directory |
| `threshold` | double | - | Minimum mutation score (0.0-100.0) |
| `thresholdAction` | String | - | Action on threshold failure: `FAILURE` or `UNSTABLE` |
| `sourceDir` | String | `.` | Source root directory |
| `sentinelPath` | String | `sentinel` | Path to sentinel executable |

<details>
<summary>Advanced parameters</summary>

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `compileDbDir` | String | `.` | compile_commands.json directory |
| `timeout` | int | - | Test timeout in seconds |
| `from` | String | - | Diff base revision (e.g., `HEAD~1`) |
| `uncommitted` | boolean | `false` | Include uncommitted changes |
| `patterns` | List | - | File glob patterns (`!` prefix to exclude) |
| `extensions` | List | - | File extensions to mutate |
| `generator` | String | `uniform` | Mutant generator: `uniform`, `random`, `weighted` |
| `mutantsPerLine` | int | `1` | Max mutants per source line |
| `seed` | long | auto | Random seed (auto-generated if omitted) |
| `operators` | List | all | Mutation operators: `AOR`, `BOR`, `LCR`, `ROR`, `SDL`, `SOR`, `UOI` |
| `limit` | int | `0` | Max mutants (0 = unlimited) |
| `lcovTracefiles` | List | - | LCOV tracefiles to skip uncovered mutants |
| `config` | String | - | Path to sentinel.yaml |
| `clean` | boolean | `false` | Clear workspace before run |
| `dryRun` | boolean | `false` | Generate mutants but don't evaluate |
| `verbose` | boolean | `false` | Show detailed output |

</details>

### `sentinelRun`

Runs sentinel on a single partition. Used inside `sentinelPipeline` closure (auto-configured) or standalone in manual mode.

Accepts the same parameters as `sentinelPipeline` plus:

| Parameter | Type | Description |
|-----------|------|-------------|
| `partition` | String | Partition spec (e.g., `2/4`) |

### `sentinelMerge`

Merges partition results and generates reports. Used in manual mode only.

| Parameter | Type | Required | Description |
|-----------|------|:--------:|-------------|
| `partitions` | List | Yes | Paths to partition workspaces |
| `workspace` | String | | Target merge workspace |
| `sourceDir` | String | | Source directory for HTML report |
| `outputDir` | String | | Report output directory |
| `threshold` | double | | Minimum mutation score |
| `thresholdAction` | String | | `FAILURE` or `UNSTABLE` |
| `sentinelPath` | String | | Path to sentinel executable |

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

In **Manage Jenkins > System**, you can set the default sentinel executable path. This is used when `sentinelPath` is not specified in the pipeline step.

## Quality Gate

When `threshold` and `thresholdAction` are set:

- If the mutation score is **below the threshold**, the build result is set to `FAILURE` or `UNSTABLE` depending on `thresholdAction`.
- If the mutation score **meets or exceeds the threshold**, the build result is not affected.
- If neither is set, the mutation score is reported but does not affect the build result.

## Environment Variables

Inside a `sentinelPipeline` closure, the following environment variables are available:

| Variable | Description |
|----------|-------------|
| `SENTINEL_PARTITION_INDEX` | Current partition number (1-based) |
| `SENTINEL_PARTITION_TOTAL` | Total number of partitions |
| `SENTINEL_SEED` | Random seed shared across all partitions |

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

This starts Jenkins at `http://localhost:8080/jenkins/` with the plugin loaded. Create a Pipeline job to test `sentinelPipeline`, `sentinelRun`, and `sentinelMerge` steps interactively. To use a different port:

```bash
mvn hpi:run -Dport=9090
```

## License

MIT License
