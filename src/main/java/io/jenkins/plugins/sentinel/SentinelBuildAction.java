package io.jenkins.plugins.sentinel;

import java.util.LinkedHashMap;
import java.util.Map;

import hudson.model.Run;
import io.jenkins.plugins.sentinel.model.MutationEntry;
import io.jenkins.plugins.sentinel.model.MutationScore;
import io.jenkins.plugins.sentinel.model.SentinelResult;
import jenkins.model.RunAction2;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Attaches mutation testing results to a Jenkins build.
 * Provides data for build page sidebar and summary.
 */
public class SentinelBuildAction implements RunAction2 {

    /** Score threshold for green color (80% or above). */
    private static final double GREEN_THRESHOLD = 80.0;
    /** Score threshold for orange color (50% or above). */
    private static final double ORANGE_THRESHOLD = 50.0;
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
        return String.format("%.1f", result.overallScore().score());
    }

    /**
     * Returns a CSS color for the score.
     * Green if &gt;= 80, orange if &gt;= 50, red otherwise.
     *
     * @return hex color string
     */
    public String getScoreColor() {
        final double score = result.overallScore().score();
        if (score >= GREEN_THRESHOLD) {
            return "#1ea64b";
        }
        if (score >= ORANGE_THRESHOLD) {
            return "#fe820a";
        }
        return "#e6001f";
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
            final JSONObject obj = new JSONObject();
            obj.put("name", entry.getKey());
            obj.put("value", entry.getValue());
            array.add(obj);
        }
        return array.toString();
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
