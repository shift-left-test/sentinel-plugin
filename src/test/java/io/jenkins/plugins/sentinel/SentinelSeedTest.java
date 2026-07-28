/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SentinelSeedTest {

    private static final String RUN_ID = "my-job#42";
    private static final long MAX_UNSIGNED_INT = 4_294_967_295L;
    private static final long KNOWN_SEED_FOR_RUN_ID = 2_260_875_568L;

    @Test
    void sameRunIdYieldsSameSeed() {
        assertThat(SentinelSeed.deriveFrom(RUN_ID))
                .isEqualTo(SentinelSeed.deriveFrom(RUN_ID));
    }

    @Test
    void knownVectorIsStable() {
        assertThat(SentinelSeed.deriveFrom(RUN_ID))
                .isEqualTo(KNOWN_SEED_FOR_RUN_ID);
    }

    @Test
    void consecutiveBuildsYieldNonSequentialSeeds() {
        final long s41 = SentinelSeed.deriveFrom("my-job#41");
        final long s42 = SentinelSeed.deriveFrom(RUN_ID);
        assertThat(Math.abs(s42 - s41)).isGreaterThan(1L);
    }

    @Test
    void differentJobsSameBuildNumberYieldDistinctSeeds() {
        assertThat(SentinelSeed.deriveFrom("proj-a#7"))
                .isNotEqualTo(SentinelSeed.deriveFrom("proj-b#7"));
    }

    @Test
    void seedsStayWithinUnsignedIntRange() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(SentinelSeed.deriveFrom("job-" + i + "#" + i))
                    .isBetween(0L, MAX_UNSIGNED_INT);
        }
    }
}
