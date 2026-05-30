/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel.model;

import java.io.Serializable;
import java.util.List;

/**
 * Aggregate mutation testing result containing overall score,
 * per-file results, and individual mutation entries.
 *
 * <p>A plain class (not a record) because instances are persisted into
 * the build's {@code build.xml} via Jenkins' XStream, which cannot
 * deserialize Java records.</p>
 */

public final class SentinelResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final MutationScore overallScore;
    private final List<FileMutationResult> fileResults;
    private final List<MutationEntry> entries;

    /**
     * Creates an aggregate result, defensively copying the lists.
     *
     * @param overallScore the overall mutation score
     * @param fileResults  per-file results (will be copied)
     * @param entries      individual mutation entries (will be copied)
     */
    public SentinelResult(
            final MutationScore overallScore,
            final List<FileMutationResult> fileResults,
            final List<MutationEntry> entries) {
        this.overallScore = overallScore;
        this.fileResults = List.copyOf(fileResults);
        this.entries = List.copyOf(entries);
    }

    /**
     * Returns the overall mutation score.
     *
     * @return overall score
     */
    public MutationScore overallScore() {
        return overallScore;
    }

    /**
     * Returns the per-file results.
     *
     * @return unmodifiable list of per-file results
     */
    public List<FileMutationResult> fileResults() {
        return fileResults;
    }

    /**
     * Returns the individual mutation entries.
     *
     * @return unmodifiable list of mutation entries
     */
    public List<MutationEntry> entries() {
        return entries;
    }
}
