package io.jenkins.plugins.sentinel.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable configuration data class holding all sentinel CLI options
 * and plugin-specific options.
 */
public class SentinelConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    // Build/Test
    private String buildCommand;
    private String testCommand;
    private String testResultDir;
    private String sourceDir;
    private String compileDbDir;
    private Integer timeout;

    // Mutation scope
    private String from;
    private boolean uncommitted;
    private List<String> patterns = new ArrayList<>();
    private List<String> extensions = new ArrayList<>();

    // Mutation strategy
    private String generator;
    private Integer mutantsPerLine;
    private Long seed;
    private List<String> operators = new ArrayList<>();
    private Integer limit;

    // Advanced
    private List<String> lcovTracefiles = new ArrayList<>();

    // Execution control
    private String config;
    private boolean clean;
    private boolean dryRun;
    private boolean verbose;
    private String workspace;
    private String outputDir;

    // Plugin-specific
    private int partitions = 1;
    private String nodeLabel = "";
    private Double threshold;
    private ThresholdAction thresholdAction;
    private String sentinelPath;

    // Partition (set internally by orchestrator)
    private String partition;

    /**
     * Returns the build command.
     *
     * @return build command
     */
    public String getBuildCommand() {
        return buildCommand;
    }

    /**
     * Sets the build command.
     *
     * @param buildCommand build command
     */
    public void setBuildCommand(final String buildCommand) {
        this.buildCommand = buildCommand;
    }

    /**
     * Returns the test command.
     *
     * @return test command
     */
    public String getTestCommand() {
        return testCommand;
    }

    /**
     * Sets the test command.
     *
     * @param testCommand test command
     */
    public void setTestCommand(final String testCommand) {
        this.testCommand = testCommand;
    }

    /**
     * Returns the test result directory.
     *
     * @return test result directory
     */
    public String getTestResultDir() {
        return testResultDir;
    }

    /**
     * Sets the test result directory.
     *
     * @param testResultDir test result directory
     */
    public void setTestResultDir(final String testResultDir) {
        this.testResultDir = testResultDir;
    }

    /**
     * Returns the source directory.
     *
     * @return source directory
     */
    public String getSourceDir() {
        return sourceDir;
    }

    /**
     * Sets the source directory.
     *
     * @param sourceDir source directory
     */
    public void setSourceDir(final String sourceDir) {
        this.sourceDir = sourceDir;
    }

    /**
     * Returns the compile database directory.
     *
     * @return compile database directory
     */
    public String getCompileDbDir() {
        return compileDbDir;
    }

    /**
     * Sets the compile database directory.
     *
     * @param compileDbDir compile database directory
     */
    public void setCompileDbDir(final String compileDbDir) {
        this.compileDbDir = compileDbDir;
    }

    /**
     * Returns the timeout in seconds.
     *
     * @return timeout
     */
    public Integer getTimeout() {
        return timeout;
    }

    /**
     * Sets the timeout in seconds.
     *
     * @param timeout timeout
     */
    public void setTimeout(final Integer timeout) {
        this.timeout = timeout;
    }

    /**
     * Returns the from commit reference.
     *
     * @return from reference
     */
    public String getFrom() {
        return from;
    }

    /**
     * Sets the from commit reference.
     *
     * @param from from reference
     */
    public void setFrom(final String from) {
        this.from = from;
    }

    /**
     * Returns whether to include uncommitted changes.
     *
     * @return uncommitted flag
     */
    public boolean isUncommitted() {
        return uncommitted;
    }

    /**
     * Sets whether to include uncommitted changes.
     *
     * @param uncommitted uncommitted flag
     */
    public void setUncommitted(final boolean uncommitted) {
        this.uncommitted = uncommitted;
    }

    /**
     * Returns file patterns to include/exclude.
     *
     * @return patterns list
     */
    public List<String> getPatterns() {
        return patterns;
    }

    /**
     * Sets file patterns.
     *
     * @param patterns patterns list
     */
    public void setPatterns(final List<String> patterns) {
        this.patterns = patterns != null
                ? new ArrayList<>(patterns) : new ArrayList<>();
    }

    /**
     * Returns file extensions to include.
     *
     * @return extensions list
     */
    public List<String> getExtensions() {
        return extensions;
    }

    /**
     * Sets file extensions.
     *
     * @param extensions extensions list
     */
    public void setExtensions(final List<String> extensions) {
        this.extensions = extensions != null
                ? new ArrayList<>(extensions) : new ArrayList<>();
    }

    /**
     * Returns the mutation generator.
     *
     * @return generator
     */
    public String getGenerator() {
        return generator;
    }

    /**
     * Sets the mutation generator.
     *
     * @param generator generator
     */
    public void setGenerator(final String generator) {
        this.generator = generator;
    }

    /**
     * Returns mutants per line limit.
     *
     * @return mutants per line
     */
    public Integer getMutantsPerLine() {
        return mutantsPerLine;
    }

    /**
     * Sets mutants per line limit.
     *
     * @param mutantsPerLine mutants per line
     */
    public void setMutantsPerLine(final Integer mutantsPerLine) {
        this.mutantsPerLine = mutantsPerLine;
    }

    /**
     * Returns the random seed.
     *
     * @return seed
     */
    public Long getSeed() {
        return seed;
    }

    /**
     * Sets the random seed.
     *
     * @param seed seed
     */
    public void setSeed(final Long seed) {
        this.seed = seed;
    }

    /**
     * Returns mutation operators.
     *
     * @return operators list
     */
    public List<String> getOperators() {
        return operators;
    }

    /**
     * Sets mutation operators.
     *
     * @param operators operators list
     */
    public void setOperators(final List<String> operators) {
        this.operators = operators != null
                ? new ArrayList<>(operators) : new ArrayList<>();
    }

    /**
     * Returns the mutant limit.
     *
     * @return limit
     */
    public Integer getLimit() {
        return limit;
    }

    /**
     * Sets the mutant limit.
     *
     * @param limit limit
     */
    public void setLimit(final Integer limit) {
        this.limit = limit;
    }

    /**
     * Returns lcov trace files.
     *
     * @return lcov tracefiles list
     */
    public List<String> getLcovTracefiles() {
        return lcovTracefiles;
    }

    /**
     * Sets lcov trace files.
     *
     * @param lcovTracefiles lcov tracefiles list
     */
    public void setLcovTracefiles(final List<String> lcovTracefiles) {
        this.lcovTracefiles = lcovTracefiles != null
                ? new ArrayList<>(lcovTracefiles) : new ArrayList<>();
    }

    /**
     * Returns path to sentinel config file.
     *
     * @return config path
     */
    public String getConfig() {
        return config;
    }

    /**
     * Sets path to sentinel config file.
     *
     * @param config config path
     */
    public void setConfig(final String config) {
        this.config = config;
    }

    /**
     * Returns whether to clean before running.
     *
     * @return clean flag
     */
    public boolean isClean() {
        return clean;
    }

    /**
     * Sets whether to clean before running.
     *
     * @param clean clean flag
     */
    public void setClean(final boolean clean) {
        this.clean = clean;
    }

    /**
     * Returns whether to run in dry-run mode.
     *
     * @return dry-run flag
     */
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * Sets whether to run in dry-run mode.
     *
     * @param dryRun dry-run flag
     */
    public void setDryRun(final boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Returns whether verbose output is enabled.
     *
     * @return verbose flag
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets verbose output flag.
     *
     * @param verbose verbose flag
     */
    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns the sentinel workspace directory.
     *
     * @return workspace
     */
    public String getWorkspace() {
        return workspace;
    }

    /**
     * Sets the sentinel workspace directory.
     *
     * @param workspace workspace
     */
    public void setWorkspace(final String workspace) {
        this.workspace = workspace;
    }

    /**
     * Returns the output directory for reports.
     *
     * @return output directory
     */
    public String getOutputDir() {
        return outputDir;
    }

    /**
     * Sets the output directory for reports.
     *
     * @param outputDir output directory
     */
    public void setOutputDir(final String outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * Returns the number of partitions.
     *
     * @return partitions count
     */
    public int getPartitions() {
        return partitions;
    }

    /**
     * Sets the number of partitions.
     *
     * @param partitions partitions count
     */
    public void setPartitions(final int partitions) {
        this.partitions = partitions;
    }

    /**
     * Returns the node label for partition execution.
     *
     * @return node label
     */
    public String getNodeLabel() {
        return nodeLabel;
    }

    /**
     * Sets the node label for partition execution.
     *
     * @param nodeLabel node label
     */
    public void setNodeLabel(final String nodeLabel) {
        this.nodeLabel = nodeLabel;
    }

    /**
     * Returns the mutation score threshold.
     *
     * @return threshold
     */
    public Double getThreshold() {
        return threshold;
    }

    /**
     * Sets the mutation score threshold.
     *
     * @param threshold threshold percentage (0-100)
     */
    public void setThreshold(final Double threshold) {
        this.threshold = threshold;
    }

    /**
     * Returns the threshold action.
     *
     * @return threshold action
     */
    public ThresholdAction getThresholdAction() {
        return thresholdAction;
    }

    /**
     * Sets the threshold action.
     *
     * @param thresholdAction threshold action
     */
    public void setThresholdAction(final ThresholdAction thresholdAction) {
        this.thresholdAction = thresholdAction;
    }

    /**
     * Returns the path to the sentinel executable.
     *
     * @return sentinel path
     */
    public String getSentinelPath() {
        return sentinelPath;
    }

    /**
     * Sets the path to the sentinel executable.
     *
     * @param sentinelPath sentinel path
     */
    public void setSentinelPath(final String sentinelPath) {
        this.sentinelPath = sentinelPath;
    }

    /**
     * Returns the partition spec (e.g. "1/4").
     *
     * @return partition spec
     */
    public String getPartition() {
        return partition;
    }

    /**
     * Sets the partition spec (e.g. "1/4").
     *
     * @param partition partition spec
     */
    public void setPartition(final String partition) {
        this.partition = partition;
    }
}
