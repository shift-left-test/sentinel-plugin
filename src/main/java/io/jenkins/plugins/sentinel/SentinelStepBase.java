package io.jenkins.plugins.sentinel;

import java.io.Serializable;
import java.util.List;

import io.jenkins.plugins.sentinel.config.SentinelConfiguration;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Base class for sentinel pipeline steps.
 *
 * <p>Holds all shared sentinel CLI options and provides
 * {@link #populateConfiguration(SentinelConfiguration)} to copy
 * them into a {@link SentinelConfiguration} instance.</p>
 */
public abstract class SentinelStepBase extends Step
        implements Serializable {

    private static final long serialVersionUID = 1L;

    // Required fields
    private final String buildCommand;
    private final String testCommand;
    private final String testResultDir;

    // Optional sentinel CLI options
    private String sourceDir;
    private String compileDbDir;
    private Integer timeout;
    private String from;
    private boolean uncommitted;
    private List<String> patterns;
    private List<String> extensions;
    private String generator;
    private Integer mutantsPerLine;
    private Long seed;
    private List<String> operators;
    private Integer limit;
    private List<String> lcovTracefiles;
    private String config;
    private boolean clean;
    private boolean dryRun;
    private boolean verbose;
    private String workspace;
    private String outputDir;
    private String sentinelPath;

    /**
     * Creates a new step with required fields.
     *
     * @param buildCommand  build command
     * @param testCommand   test command
     * @param testResultDir test result directory
     */
    protected SentinelStepBase(
            final String buildCommand,
            final String testCommand,
            final String testResultDir) {
        super();
        this.buildCommand = buildCommand;
        this.testCommand = testCommand;
        this.testResultDir = testResultDir;
    }

    /**
     * Populates the shared sentinel CLI options into a configuration.
     *
     * @param c configuration to populate
     */
    protected final void populateConfiguration(
            final SentinelConfiguration c) {
        c.setBuildCommand(buildCommand);
        c.setTestCommand(testCommand);
        c.setTestResultDir(testResultDir);
        c.setSourceDir(sourceDir);
        c.setCompileDbDir(compileDbDir);
        c.setTimeout(timeout);
        c.setFrom(from);
        c.setUncommitted(uncommitted);
        c.setPatterns(patterns);
        c.setExtensions(extensions);
        c.setGenerator(generator);
        c.setMutantsPerLine(mutantsPerLine);
        c.setSeed(seed);
        c.setOperators(operators);
        c.setLimit(limit);
        c.setLcovTracefiles(lcovTracefiles);
        c.setConfig(config);
        c.setClean(clean);
        c.setDryRun(dryRun);
        c.setVerbose(verbose);
        c.setWorkspace(workspace);
        c.setOutputDir(outputDir);
        c.setSentinelPath(sentinelPath);
    }

    /**
     * Returns the build command.
     *
     * @return build command
     */
    public final String getBuildCommand() {
        return buildCommand;
    }

    /**
     * Returns the test command.
     *
     * @return test command
     */
    public final String getTestCommand() {
        return testCommand;
    }

    /**
     * Returns the test result directory.
     *
     * @return test result directory
     */
    public final String getTestResultDir() {
        return testResultDir;
    }

    /**
     * Returns the source directory.
     *
     * @return source directory
     */
    public final String getSourceDir() {
        return sourceDir;
    }

    /**
     * Returns the compile database directory.
     *
     * @return compile database directory
     */
    public final String getCompileDbDir() {
        return compileDbDir;
    }

    /**
     * Returns the timeout in seconds.
     *
     * @return timeout
     */
    public final Integer getTimeout() {
        return timeout;
    }

    /**
     * Returns the from commit reference.
     *
     * @return from reference
     */
    public final String getFrom() {
        return from;
    }

    /**
     * Returns whether to include uncommitted changes.
     *
     * @return uncommitted flag
     */
    public final boolean isUncommitted() {
        return uncommitted;
    }

    /**
     * Returns file patterns.
     *
     * @return file patterns
     */
    public final List<String> getPatterns() {
        return patterns;
    }

    /**
     * Returns file extensions.
     *
     * @return file extensions
     */
    public final List<String> getExtensions() {
        return extensions;
    }

    /**
     * Returns the mutation generator.
     *
     * @return generator
     */
    public final String getGenerator() {
        return generator;
    }

    /**
     * Returns mutants per line limit.
     *
     * @return mutants per line
     */
    public final Integer getMutantsPerLine() {
        return mutantsPerLine;
    }

    /**
     * Returns the random seed.
     *
     * @return seed
     */
    public final Long getSeed() {
        return seed;
    }

    /**
     * Returns mutation operators.
     *
     * @return operators list
     */
    public final List<String> getOperators() {
        return operators;
    }

    /**
     * Returns the mutant limit.
     *
     * @return limit
     */
    public final Integer getLimit() {
        return limit;
    }

    /**
     * Returns lcov trace files.
     *
     * @return lcov tracefiles
     */
    public final List<String> getLcovTracefiles() {
        return lcovTracefiles;
    }

    /**
     * Returns the config file path.
     *
     * @return config path
     */
    public final String getConfig() {
        return config;
    }

    /**
     * Returns whether to clean before running.
     *
     * @return clean flag
     */
    public final boolean isClean() {
        return clean;
    }

    /**
     * Returns whether to run in dry-run mode.
     *
     * @return dry-run flag
     */
    public final boolean isDryRun() {
        return dryRun;
    }

    /**
     * Returns whether verbose output is enabled.
     *
     * @return verbose flag
     */
    public final boolean isVerbose() {
        return verbose;
    }

    /**
     * Returns the workspace directory.
     *
     * @return workspace
     */
    public final String getWorkspace() {
        return workspace;
    }

    /**
     * Returns the output directory.
     *
     * @return output directory
     */
    public final String getOutputDir() {
        return outputDir;
    }

    /**
     * Returns the sentinel executable path.
     *
     * @return sentinel path
     */
    public final String getSentinelPath() {
        return sentinelPath;
    }

    /**
     * Sets the source directory.
     *
     * @param v source directory
     */
    @DataBoundSetter
    public final void setSourceDir(final String v) {
        sourceDir = v;
    }

    /**
     * Sets the compile database directory.
     *
     * @param v compile database directory
     */
    @DataBoundSetter
    public final void setCompileDbDir(final String v) {
        compileDbDir = v;
    }

    /**
     * Sets the timeout in seconds.
     *
     * @param v timeout
     */
    @DataBoundSetter
    public final void setTimeout(final Integer v) {
        timeout = v;
    }

    /**
     * Sets the from commit reference.
     *
     * @param v from reference
     */
    @DataBoundSetter
    public final void setFrom(final String v) {
        from = v;
    }

    /**
     * Sets whether to include uncommitted changes.
     *
     * @param v uncommitted flag
     */
    @DataBoundSetter
    public final void setUncommitted(final boolean v) {
        uncommitted = v;
    }

    /**
     * Sets file patterns.
     *
     * @param v file patterns
     */
    @DataBoundSetter
    public final void setPatterns(final List<String> v) {
        patterns = v;
    }

    /**
     * Sets file extensions.
     *
     * @param v file extensions
     */
    @DataBoundSetter
    public final void setExtensions(final List<String> v) {
        extensions = v;
    }

    /**
     * Sets the mutation generator.
     *
     * @param v generator
     */
    @DataBoundSetter
    public final void setGenerator(final String v) {
        generator = v;
    }

    /**
     * Sets mutants per line limit.
     *
     * @param v mutants per line
     */
    @DataBoundSetter
    public final void setMutantsPerLine(final Integer v) {
        mutantsPerLine = v;
    }

    /**
     * Sets the random seed.
     *
     * @param v seed
     */
    @DataBoundSetter
    public final void setSeed(final Long v) {
        seed = v;
    }

    /**
     * Sets mutation operators.
     *
     * @param v operators list
     */
    @DataBoundSetter
    public final void setOperators(final List<String> v) {
        operators = v;
    }

    /**
     * Sets the mutant limit.
     *
     * @param v limit
     */
    @DataBoundSetter
    public final void setLimit(final Integer v) {
        limit = v;
    }

    /**
     * Sets lcov trace files.
     *
     * @param v lcov tracefiles
     */
    @DataBoundSetter
    public final void setLcovTracefiles(final List<String> v) {
        lcovTracefiles = v;
    }

    /**
     * Sets the config file path.
     *
     * @param v config path
     */
    @DataBoundSetter
    public final void setConfig(final String v) {
        config = v;
    }

    /**
     * Sets whether to clean before running.
     *
     * @param v clean flag
     */
    @DataBoundSetter
    public final void setClean(final boolean v) {
        clean = v;
    }

    /**
     * Sets whether to run in dry-run mode.
     *
     * @param v dry-run flag
     */
    @DataBoundSetter
    public final void setDryRun(final boolean v) {
        dryRun = v;
    }

    /**
     * Sets whether verbose output is enabled.
     *
     * @param v verbose flag
     */
    @DataBoundSetter
    public final void setVerbose(final boolean v) {
        verbose = v;
    }

    /**
     * Sets the workspace directory.
     *
     * @param v workspace
     */
    @DataBoundSetter
    public final void setWorkspace(final String v) {
        workspace = v;
    }

    /**
     * Sets the output directory.
     *
     * @param v output directory
     */
    @DataBoundSetter
    public final void setOutputDir(final String v) {
        outputDir = v;
    }

    /**
     * Sets the sentinel executable path.
     *
     * @param v sentinel path
     */
    @DataBoundSetter
    public final void setSentinelPath(final String v) {
        sentinelPath = v;
    }
}
