/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel.model;

import java.io.Serializable;
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
     * Merges this score with another by summing counts.
     *
     * @param other the other score to merge with
     * @return new merged MutationScore
     */
    public MutationScore merge(final MutationScore other) {
        return new MutationScore(
                killedCount + other.killedCount,
                survivedCount + other.survivedCount,
                skippedCount + other.skippedCount);
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
        return String.format(
                "MutationScore{killed=%d, survived=%d, skipped=%d, "
                        + "total=%d, score=%.1f%%}",
                killedCount, survivedCount, skippedCount,
                total(), score());
    }
}
