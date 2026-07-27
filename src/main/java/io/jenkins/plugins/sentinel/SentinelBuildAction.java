/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import hudson.model.Run;
import io.jenkins.plugins.sentinel.model.MutationEntry;
import io.jenkins.plugins.sentinel.model.MutationScore;
import io.jenkins.plugins.sentinel.model.SentinelResult;
import jakarta.servlet.ServletException;
import jenkins.model.RunAction2;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Attaches mutation testing results to a Jenkins build.
 * Provides data for build page sidebar and summary.
 */

public class SentinelBuildAction implements RunAction2 {

    /**
     * System property that overrides the report CSP header. Hardcoded
     * instead of referencing {@code DirectoryBrowserSupport.CSP_PROPERTY_NAME}
     * because that constant is {@code @Restricted} and the access-modifier
     * checker fails the build on its use; the literal matches Jenkins core.
     */
    private static final String CSP_PROPERTY =
            "hudson.model.DirectoryBrowserSupport.CSP";
    /**
     * Plugin-scoped system property that overrides {@link #REPORT_CSP}.
     * Checked before {@link #CSP_PROPERTY} so that tightening the
     * Jenkins-wide policy for workspace browsing does not silently
     * blank this report, while an administrator who wants one policy
     * everywhere can still set only the Jenkins-wide property.
     */
    private static final String SENTINEL_CSP_PROPERTY =
            "io.jenkins.plugins.sentinel.reportCsp";
    /**
     * Default Content-Security-Policy for the served HTML report.
     *
     * <p>The sentinel report is a self-contained HTML file that renders
     * its entire content with inline JavaScript, so inline scripts and
     * styles must be allowed or the page stays blank. {@code sandbox}
     * <em>without</em> {@code allow-same-origin} runs the report in an
     * opaque origin: its scripts cannot reach Jenkins cookies, DOM, or
     * APIs. This is deliberately more permissive than
     * {@code DirectoryBrowserSupport.DEFAULT_CSP_VALUE}, which targets
     * arbitrary workspace files.</p>
     */
    private static final String REPORT_CSP =
            "sandbox allow-scripts; default-src 'none'; "
                    + "img-src 'self' data:; "
                    + "style-src 'unsafe-inline'; "
                    + "script-src 'unsafe-inline';";
    /** Multiplier for percent calculation. */
    private static final int PERCENT = 100;

    /** The mutation testing result attached to this build. */
    private final SentinelResult result;
    /** The Jenkins build run this action belongs to. */
    private transient Run<?, ?> run;

    /**
     * Creates a new SentinelBuildAction with the given result.
     *
     * @param sentinelResult the mutation testing result
     */
    public SentinelBuildAction(final SentinelResult sentinelResult) {
        this.result = sentinelResult;
    }

    /**
     * Returns the mutation testing result.
     *
     * @return sentinel result
     */
    public SentinelResult getResult() {
        return result;
    }

    /**
     * Returns the associated build run.
     *
     * @return the build run
     */
    public Run<?, ?> getRun() {
        return run;
    }

    /**
     * Sets the associated build run.
     * Used by init scripts that create actions outside
     * the normal Jenkins lifecycle.
     *
     * @param buildRun the build run
     */
    public void setRun(final Run<?, ?> buildRun) {
        this.run = buildRun;
    }

    /** {@inheritDoc} */
    @Override
    public void onAttached(final Run<?, ?> r) {
        this.run = r;
    }

    /** {@inheritDoc} */
    @Override
    public void onLoad(final Run<?, ?> r) {
        this.run = r;
    }

    /** {@inheritDoc} */
    @Override
    public String getIconFileName() {
        return "graph.png";
    }

    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
        return "Sentinel Report";
    }

    /** {@inheritDoc} */
    @Override
    public String getUrlName() {
        return "sentinel";
    }

    /**
     * Returns the overall mutation score formatted to one decimal place.
     *
     * @return formatted score string
     */
    public String getFormattedScore() {
        return result.overallScore().formattedScore();
    }

    /**
     * Returns a CSS color for the overall score band.
     *
     * @return hex color string
     * @see MutationScore#scoreColor()
     */
    public String getScoreColor() {
        return result.overallScore().scoreColor();
    }

    /**
     * Returns total mutant count including skipped.
     *
     * @return killed + survived + skipped
     */
    public int getTotalWithSkipped() {
        final MutationScore score = result.overallScore();
        return score.killed() + score.survived() + score.skipped();
    }

    /**
     * Returns killed percentage of total including skipped.
     * Returns 0 if total is zero.
     *
     * @return killed percent
     */
    public int getKilledPercent() {
        return percentOf(result.overallScore().killed());
    }

    /**
     * Returns survived percentage of total including skipped.
     * Returns 0 if total is zero.
     *
     * @return survived percent
     */
    public int getSurvivedPercent() {
        return percentOf(result.overallScore().survived());
    }

    /**
     * Returns skipped percentage of total including skipped.
     * Returns 0 if total is zero.
     *
     * @return skipped percent
     */
    public int getSkippedPercent() {
        return percentOf(result.overallScore().skipped());
    }

    /**
     * Returns whether the archived HTML report exists.
     *
     * @return true if the archived HTML report file exists
     */
    public boolean hasHtmlReport() {
        return run != null
                && Files.isRegularFile(getHtmlReportPath());
    }

    /**
     * Serves the archived HTML report file.
     *
     * <p>Applies a sandboxing Content-Security-Policy that lets the
     * report render itself with its inline scripts and styles while
     * isolating it in an opaque origin (see {@link #REPORT_CSP}). The
     * policy can be overridden with the
     * {@code io.jenkins.plugins.sentinel.reportCsp} system property, or
     * Jenkins-wide with
     * {@code hudson.model.DirectoryBrowserSupport.CSP}.</p>
     *
     * @param req  stapler request
     * @param rsp  stapler response
     * @throws IOException      if file cannot be read
     * @throws ServletException if serving fails
     */
    public void doDynamic(
            final StaplerRequest2 req,
            final StaplerResponse2 rsp)
            throws IOException, ServletException {
        if (run == null) {
            rsp.sendError(
                    StaplerResponse2.SC_NOT_FOUND,
                    "HTML report not available");
            return;
        }
        final Path htmlFile = getHtmlReportPath();
        applyContentSecurityPolicy(rsp);
        rsp.setContentType("text/html;charset=UTF-8");
        try {
            Files.copy(htmlFile, rsp.getOutputStream());
        } catch (NoSuchFileException e) {
            rsp.sendError(
                    StaplerResponse2.SC_NOT_FOUND,
                    "HTML report not found");
        }
    }

    private static void applyContentSecurityPolicy(
            final StaplerResponse2 rsp) {
        // REPORT_CSP is a non-null constant, so neither lookup returns
        // null; an explicitly empty value disables CSP.
        final String csp = System.getProperty(
                SENTINEL_CSP_PROPERTY,
                System.getProperty(CSP_PROPERTY, REPORT_CSP));
        if (!csp.isEmpty()) {
            rsp.setHeader("Content-Security-Policy", csp);
            rsp.setHeader("X-WebKit-CSP", csp);
            rsp.setHeader("X-Content-Security-Policy", csp);
        }
    }

    private Path getHtmlReportPath() {
        return run.getRootDir().toPath()
                .resolve(SentinelEnvironment.ARCHIVE_DIR)
                .resolve(SentinelEnvironment.HTML_REPORT_FILE);
    }

    private int percentOf(final int count) {
        final int total = getTotalWithSkipped();
        if (total == 0) {
            return 0;
        }
        return count * PERCENT / total;
    }

    /**
     * Returns a map of mutator type to count from all entries.
     *
     * @return mutator distribution map
     */
    public Map<String, Integer> getMutatorDistribution() {
        final Map<String, Integer> dist = new LinkedHashMap<>();
        for (final MutationEntry entry : result.entries()) {
            dist.merge(entry.mutator(), 1, Integer::sum);
        }
        return dist;
    }

    /**
     * Returns mutator distribution as a JSON array string.
     * Format: {@code [{"name":"AOR","value":8}, ...]}
     *
     * @return JSON array string
     */
    public String getMutatorDistributionJson() {
        final JSONArray array = new JSONArray();
        for (final Map.Entry<String, Integer> entry
                : getMutatorDistribution().entrySet()) {
            array.add(distEntry(entry.getKey(), entry.getValue()));
        }
        return array.toString();
    }

    /**
     * Returns the killed/survived/skipped counts as a JSON array
     * string for the score distribution donut chart.
     * Format: {@code [{"name":"Killed","value":42}, ...]}
     *
     * @return JSON array string
     */
    public String getScoreDistributionJson() {
        final MutationScore score = result.overallScore();
        final JSONArray array = new JSONArray();
        array.add(distEntry("Killed", score.killed()));
        array.add(distEntry("Survived", score.survived()));
        array.add(distEntry("Skipped", score.skipped()));
        return array.toString();
    }

    private static JSONObject distEntry(final String name, final int value) {
        final JSONObject obj = new JSONObject();
        obj.put("name", name);
        obj.put("value", value);
        return obj;
    }

    /**
     * Returns the number of files with mutation results.
     *
     * @return file count
     */
    public int getFileCount() {
        return result.fileResults().size();
    }

    /**
     * Returns the total number of mutation entries.
     *
     * @return entry count
     */
    public int getEntryCount() {
        return result.entries().size();
    }
}
