/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel.model;

import java.io.Serializable;

/**
 * Mutation result for a single source file.
 */

public record FileMutationResult(
        String filePath,
        MutationScore score) implements Serializable {

    private static final long serialVersionUID = 1L;
}
