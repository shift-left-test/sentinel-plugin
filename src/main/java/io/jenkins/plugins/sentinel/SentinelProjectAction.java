package io.jenkins.plugins.sentinel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    /** Maximum number of builds to include in trend data. */
    private static final int MAX_BUILDS = 25;

    /** The Jenkins job this action is attached to. */
    private final transient Job<?, ?> job;

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
     * Collects trend data from recent builds (up to {@value MAX_BUILDS}).
     *
     * <p>Walks from the last build backward, collecting mutation score
     * data from builds that have a {@link SentinelBuildAction}.
     * Returns a JSON array ordered chronologically (oldest first).</p>
     *
     * @return JSON string of trend data points
     */
    public String getTrendDataJson() {
        final List<JSONObject> points = new ArrayList<>();
        Run<?, ?> build = job.getLastBuild();
        int count = 0;

        while (build != null && count < MAX_BUILDS) {
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
                count++;
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
