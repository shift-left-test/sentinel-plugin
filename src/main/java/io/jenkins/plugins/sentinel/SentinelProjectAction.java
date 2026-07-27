/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;
import io.jenkins.plugins.sentinel.model.MutationScore;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Project-level action that provides mutation score trend data
 * across recent builds. Displayed on the project page sidebar
 * and used by the trend chart.
 */

public class SentinelProjectAction implements Action {

    /**
     * Maximum number of builds to include in trend data.
     * Also used by {@link SentinelProjectActionFactory} as the
     * lookback window when deciding whether to attach this action.
     */
    static final int MAX_BUILDS = 25;

    /** Trend JSON for a job with no mutation results. */
    private static final String EMPTY_TREND = "[]";

    /**
     * Memoized trend JSON per job, keyed by the last completed build
     * number.
     *
     * <p>Both the trend charts and {@link SentinelProjectActionFactory}
     * read this on every job page render, and computing it walks up to
     * {@value #MAX_BUILDS} builds - each {@code getAction} call on a
     * build that has fallen out of Jenkins' run cache deserializes that
     * build's whole {@code build.xml}. The result can only change when
     * another build completes, because actions are attached while a
     * build is still running and never after it completes. Weak keys
     * let entries die with their job.</p>
     */
    private static final Map<Job<?, ?>, CachedTrend> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** The Jenkins job this action is attached to. */
    private final transient Job<?, ?> job;

    /** Trend JSON computed for one job history state. */
    private record CachedTrend(int lastCompleted, String json) { }

    /**
     * Creates a new SentinelProjectAction for the given job.
     *
     * @param owner the Jenkins job
     */
    SentinelProjectAction(final Job<?, ?> owner) {
        this.job = owner;
    }

    /**
     * Returns the associated Jenkins job.
     *
     * @return the job
     */
    public Job<?, ?> getJob() {
        return job;
    }

    /**
     * Returns the trend data for a job, computing it at most once per
     * completed build (see {@link #CACHE}).
     *
     * <p>The trend window is the last {@value #MAX_BUILDS} completed
     * builds. This is the single definition of the window: the trend
     * charts show exactly these builds, and
     * {@link SentinelProjectActionFactory} attaches this action exactly
     * when the window holds results.</p>
     *
     * @param job the job to inspect
     * @return JSON string of trend data points, oldest first
     */
    static String trendDataJson(final Job<?, ?> job) {
        final Run<?, ?> lastCompleted = job.getLastCompletedBuild();
        if (lastCompleted == null) {
            return EMPTY_TREND;
        }
        final CachedTrend cached = CACHE.get(job);
        if (cached != null
                && cached.lastCompleted() == lastCompleted.getNumber()) {
            return cached.json();
        }
        final String json = collectTrendData(lastCompleted);
        CACHE.put(job, new CachedTrend(lastCompleted.getNumber(), json));
        return json;
    }

    /**
     * Returns whether the trend window holds any mutation results.
     *
     * @param job the job to inspect
     * @return true if the trend chart would have data to show
     */
    static boolean hasTrendData(final Job<?, ?> job) {
        return !EMPTY_TREND.equals(trendDataJson(job));
    }

    /**
     * Returns the trend data as JSON for the trend charts.
     *
     * @return JSON string of trend data points
     */
    public String getTrendDataJson() {
        return trendDataJson(job);
    }

    private static String collectTrendData(final Run<?, ?> lastCompleted) {
        final List<JSONObject> points = new ArrayList<>();
        Run<?, ?> build = lastCompleted;
        for (int i = 0; build != null && i < MAX_BUILDS; i++) {
            final SentinelBuildAction action =
                    build.getAction(SentinelBuildAction.class);
            if (action != null) {
                final MutationScore score = action.getResult().overallScore();
                final JSONObject point = new JSONObject();
                point.put("buildNumber", build.getNumber());
                point.put("score", score.score());
                point.put("killed", score.killed());
                point.put("survived", score.survived());
                point.put("skipped", score.skipped());
                points.add(point);
            }
            build = build.getPreviousBuild();
        }

        Collections.reverse(points);

        final JSONArray array = new JSONArray();
        for (final JSONObject point : points) {
            array.add(point);
        }
        return array.toString();
    }

    /** {@inheritDoc} */
    @Override
    public String getIconFileName() {
        return "graph.png";
    }

    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
        return "Sentinel Trend Report";
    }

    /** {@inheritDoc} */
    @Override
    public String getUrlName() {
        return "sentinel-trend";
    }
}
