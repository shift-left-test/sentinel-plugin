/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Derives the fallback sentinel seed from a build's run ID.
 *
 * <p>Every {@code sentinelRun} execution of a build sees the same
 * {@code Run}, so deriving the seed from the run ID makes all
 * partitions agree on one value without any coordination. SHA-256 is
 * used (rather than {@link String#hashCode()}) so that run IDs that
 * differ only in the trailing build number still produce well-mixed,
 * non-sequential seeds.</p>
 */
public final class SentinelSeed {

    private SentinelSeed() {
    }

    /**
     * Derives a deterministic seed in the range {@code 0} to
     * {@code 4294967295} from the given run ID by reading the first
     * four digest bytes as an unsigned big-endian int. The range must
     * stay within what sentinel accepts for {@code --seed} — the same
     * bound {@code SentinelConfigValidator} enforces.
     *
     * @param runId externalizable run ID, e.g. {@code "my-job#42"}
     * @return derived seed; same input always yields the same value
     */
    public static long deriveFrom(final String runId) {
        return Integer.toUnsignedLong(
                ByteBuffer.wrap(sha256(runId)).getInt());
    }

    private static byte[] sha256(final String input) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 unavailable despite being JDK-mandatory",
                    e);
        }
    }
}
