/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel.model;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable mutation score value object.
 *
 * <p>Score = killed / (killed + survived) * 100.
 * Skipped mutants (build failures, timeouts, runtime errors)
 * are excluded from the score calculation.</p>
 */

public final class MutationScore implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final double HUNDRED = 100.0;
    /** Score threshold for the green band (80% or above). */
    private static final double GREEN_THRESHOLD = 80.0;
    /** Score threshold for the orange band (50% or above). */
    private static final double ORANGE_THRESHOLD = 50.0;

    private final int killedCount;
    private final int survivedCount;
    private final int skippedCount;

    /**
     * Creates a new MutationScore with the given counts.
     *
     * @param killedCount   number of killed mutants
     * @param survivedCount number of survived mutants
     * @param skippedCount  number of skipped mutants
     */
    public MutationScore(
            final int killedCount,
            final int survivedCount,
            final int skippedCount) {
        this.killedCount = killedCount;
        this.survivedCount = survivedCount;
        this.skippedCount = skippedCount;
    }

    /**
     * Returns number of killed mutants.
     *
     * @return killed count
     */
    public int killed() {
        return killedCount;
    }

    /**
     * Returns number of survived mutants.
     *
     * @return survived count
     */
    public int survived() {
        return survivedCount;
    }

    /**
     * Returns number of skipped mutants.
     *
     * @return skipped count
     */
    public int skipped() {
        return skippedCount;
    }

    /**
     * Returns killed + survived (excludes skipped).
     *
     * @return total actionable mutants
     */
    public int total() {
        return killedCount + survivedCount;
    }

    /**
     * Returns every mutant sentinel produced, including skipped ones.
     *
     * <p>This is the denominator for the stacked status bar, which shows
     * how the run was spent; {@link #total()} is the denominator for the
     * score, which measures only mutants that were actually evaluated.</p>
     *
     * @return killed + survived + skipped
     */
    public int totalWithSkipped() {
        return killedCount + survivedCount + skippedCount;
    }

    /**
     * Returns a count as a whole percentage of {@link #totalWithSkipped()}.
     *
     * @param count one of the status counts
     * @return the percentage, or 0 when there are no mutants at all
     */
    public int percentOf(final int count) {
        final int total = totalWithSkipped();
        if (total == 0) {
            return 0;
        }
        return count * (int) HUNDRED / total;
    }

    /**
     * Returns mutation score as percentage (0.0 to 100.0).
     * Returns 0.0 if total is zero.
     *
     * @return mutation score percentage
     */
    public double score() {
        final int t = total();
        if (t == 0) {
            return 0.0;
        }
        return (double) killedCount / t * HUNDRED;
    }

    /**
     * Returns the score formatted to one decimal place, e.g. {@code "66.7"}.
     *
     * <p>Formatting is done in Java because Jelly's JEXL cannot invoke
     * static methods like {@code String.format}. Uses {@link Locale#ROOT}
     * so the decimal separator is always a period regardless of the
     * server locale.</p>
     *
     * @return formatted score string
     */
    public String formattedScore() {
        return String.format(Locale.ROOT, "%.1f", score());
    }

    /**
     * Returns the score rounded to a whole percent, e.g. {@code "67"}.
     * Suitable for CSS width values in score bars.
     *
     * @return whole-percent score string
     */
    public String wholePercent() {
        return String.format(Locale.ROOT, "%.0f", score());
    }

    /**
     * Returns a CSS color for this score's band: green for
     * 80% or above, orange for 50% or above, red otherwise.
     *
     * <p>Single source of the score-to-color mapping used by all
     * Jelly views (overall score and per-file rows).</p>
     *
     * @return hex color string
     */
    public String scoreColor() {
        final double s = score();
        if (s >= GREEN_THRESHOLD) {
            return "#1ea64b";
        }
        if (s >= ORANGE_THRESHOLD) {
            return "#fe820a";
        }
        return "#e6001f";
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MutationScore that)) {
            return false;
        }
        return killedCount == that.killedCount
                && survivedCount == that.survivedCount
                && skippedCount == that.skippedCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(killedCount, survivedCount, skippedCount);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "MutationScore{killed=%d, survived=%d, skipped=%d, "
                        + "total=%d, score=%s%%}",
                killedCount, survivedCount, skippedCount,
                total(), formattedScore());
    }
}
