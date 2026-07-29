/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class MutationScoreTest {

    @Test
    void scoreCalculation() {
        final MutationScore score = new MutationScore(80, 20, 5);
        assertThat(score.killed()).isEqualTo(80);
        assertThat(score.survived()).isEqualTo(20);
        assertThat(score.skipped()).isEqualTo(5);
        assertThat(score.total()).isEqualTo(100);
        assertThat(score.score()).isCloseTo(80.0, within(0.01));
    }

    @Test
    void scoreWithZeroTotalReturnsZero() {
        final MutationScore score = new MutationScore(0, 0, 0);
        assertThat(score.total()).isEqualTo(0);
        assertThat(score.score()).isEqualTo(0.0);
    }

    @Test
    void scoreWithOnlyKilledReturnsHundred() {
        final MutationScore score = new MutationScore(50, 0, 10);
        assertThat(score.score()).isCloseTo(100.0, within(0.01));
    }

    @Test
    void scoreExcludesSkippedFromCalculation() {
        // Score = killed / (killed + survived) * 100
        // Skipped is excluded
        final MutationScore score = new MutationScore(60, 40, 100);
        assertThat(score.score()).isCloseTo(60.0, within(0.01));
        assertThat(score.total()).isEqualTo(100);
    }

    @Test
    void equalsAndHashCode() {
        final MutationScore a = new MutationScore(10, 5, 2);
        final MutationScore b = new MutationScore(10, 5, 2);
        final MutationScore c = new MutationScore(10, 5, 3);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void equalsIsReflexive() {
        final MutationScore score = new MutationScore(10, 5, 2);
        assertThat(score.equals(score)).isTrue();
    }

    @Test
    void equalsRejectsNullAndOtherTypes() {
        final MutationScore score = new MutationScore(10, 5, 2);
        assertThat(score).isNotEqualTo(null);
        assertThat(score).isNotEqualTo("10/5/2");
    }

    @Test
    void equalsComparesEveryCountSeparately() {
        final MutationScore base = new MutationScore(10, 5, 2);
        assertThat(base).isNotEqualTo(new MutationScore(11, 5, 2));
        assertThat(base).isNotEqualTo(new MutationScore(10, 6, 2));
        assertThat(base).isNotEqualTo(new MutationScore(10, 5, 3));
    }

    @Test
    void totalWithSkippedCountsEveryMutant() {
        final MutationScore score = new MutationScore(80, 20, 5);
        assertThat(score.totalWithSkipped()).isEqualTo(105);
        // total() is the score denominator and must stay skipped-free.
        assertThat(score.total()).isEqualTo(100);
    }

    @Test
    void percentOfSplitsTheStatusCounts() {
        final MutationScore score = new MutationScore(50, 30, 20);
        assertThat(score.percentOf(score.killed())).isEqualTo(50);
        assertThat(score.percentOf(score.survived())).isEqualTo(30);
        assertThat(score.percentOf(score.skipped())).isEqualTo(20);
    }

    @Test
    void percentOfReturnsZeroWhenThereAreNoMutants() {
        final MutationScore score = new MutationScore(0, 0, 0);
        assertThat(score.totalWithSkipped()).isZero();
        assertThat(score.percentOf(0)).isZero();
    }

    @Test
    void allSkippedScoresZeroButCountsEveryMutant() {
        // The case that trips a threshold without any test gap: nothing was
        // evaluated, so score() is 0.0 by definition.
        final MutationScore score = new MutationScore(0, 0, 40);
        assertThat(score.total()).isZero();
        assertThat(score.score()).isEqualTo(0.0);
        assertThat(score.totalWithSkipped()).isEqualTo(40);
        assertThat(score.percentOf(score.skipped())).isEqualTo(100);
    }

    @Test
    void formattedScoreHasOneDecimal() {
        // 42 / (42 + 21) * 100 = 66.666...
        final MutationScore score = new MutationScore(42, 21, 14);
        assertThat(score.formattedScore()).isEqualTo("66.7");
    }

    @Test
    void wholePercentRoundsToInteger() {
        final MutationScore score = new MutationScore(42, 21, 14);
        assertThat(score.wholePercent()).isEqualTo("67");
    }

    @Test
    void formattedScoreOfZeroTotal() {
        final MutationScore score = new MutationScore(0, 0, 5);
        assertThat(score.formattedScore()).isEqualTo("0.0");
        assertThat(score.wholePercent()).isEqualTo("0");
    }

    @Test
    void scoreColorBands() {
        assertThat(new MutationScore(80, 20, 0).scoreColor())
                .isEqualTo("#1ea64b");
        assertThat(new MutationScore(50, 50, 0).scoreColor())
                .isEqualTo("#fe820a");
        assertThat(new MutationScore(49, 51, 0).scoreColor())
                .isEqualTo("#e6001f");
    }

    @Test
    void toStringContainsScore() {
        final MutationScore score = new MutationScore(80, 20, 0);
        assertThat(score.toString()).contains("80.0");
    }

}
