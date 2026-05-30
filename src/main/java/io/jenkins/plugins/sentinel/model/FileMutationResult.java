/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel.model;

import java.io.Serializable;

/**
 * Mutation result for a single source file.
 *
 * <p>A plain class (not a record) because instances are persisted into
 * the build's {@code build.xml} via Jenkins' XStream, which cannot
 * deserialize Java records.</p>
 */

public final class FileMutationResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String filePath;
    private final MutationScore score;

    /**
     * Creates a per-file mutation result.
     *
     * @param filePath source file path
     * @param score    mutation score for the file
     */
    public FileMutationResult(final String filePath,
                              final MutationScore score) {
        this.filePath = filePath;
        this.score = score;
    }

    /**
     * Returns the source file path.
     *
     * @return file path
     */
    public String filePath() {
        return filePath;
    }

    /**
     * Returns the mutation score for the file.
     *
     * @return mutation score
     */
    public MutationScore score() {
        return score;
    }
}
