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
    void toStringContainsScore() {
        final MutationScore score = new MutationScore(80, 20, 0);
        assertThat(score.toString()).contains("80.0");
    }

    @Test
    void mergesCombinesCounts() {
        final MutationScore a = new MutationScore(10, 5, 2);
        final MutationScore b = new MutationScore(20, 10, 3);
        final MutationScore merged = a.merge(b);
        assertThat(merged.killed()).isEqualTo(30);
        assertThat(merged.survived()).isEqualTo(15);
        assertThat(merged.skipped()).isEqualTo(5);
    }
}
