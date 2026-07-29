/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel.model;

import java.io.Serializable;

/**
 * A single mutation entry parsed from mutations.xml.
 *
 * <p>A plain class (not a record) because instances are persisted into
 * the build's {@code build.xml} via Jenkins' XStream, which cannot
 * deserialize Java records.</p>
 */

public final class MutationEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String sourceFile;
    private final String sourceFilePath;
    private final String mutatedClass;
    private final String mutatedMethod;
    private final int lineNumber;
    private final String mutator;
    private final boolean detected;
    private final boolean skipped;
    private final String killingTest;

    /**
     * Creates a mutation entry.
     *
     * @param sourceFile     source file name
     * @param sourceFilePath source file path
     * @param mutatedClass   mutated class name
     * @param mutatedMethod  mutated method name
     * @param lineNumber     line number
     * @param mutator        mutator name
     * @param detected       whether a test detected (killed) the mutation
     * @param skipped        whether the mutation was skipped (not evaluated)
     * @param killingTest    name of the killing test, or null
     */
    public MutationEntry(
            final String sourceFile,
            final String sourceFilePath,
            final String mutatedClass,
            final String mutatedMethod,
            final int lineNumber,
            final String mutator,
            final boolean detected,
            final boolean skipped,
            final String killingTest) {
        this.sourceFile = sourceFile;
        this.sourceFilePath = sourceFilePath;
        this.mutatedClass = mutatedClass;
        this.mutatedMethod = mutatedMethod;
        this.lineNumber = lineNumber;
        this.mutator = mutator;
        this.detected = detected;
        this.skipped = skipped;
        this.killingTest = killingTest;
    }

    /**
     * Returns the source file name.
     *
     * @return source file name
     */
    public String sourceFile() {
        return sourceFile;
    }

    /**
     * Returns the source file path.
     *
     * @return source file path
     */
    public String sourceFilePath() {
        return sourceFilePath;
    }

    /**
     * Returns the mutated class name.
     *
     * @return mutated class name
     */
    public String mutatedClass() {
        return mutatedClass;
    }

    /**
     * Returns the mutated method name.
     *
     * @return mutated method name
     */
    public String mutatedMethod() {
        return mutatedMethod;
    }

    /**
     * Returns the line number.
     *
     * @return line number
     */
    public int lineNumber() {
        return lineNumber;
    }

    /**
     * Returns the mutator name.
     *
     * @return mutator name
     */
    public String mutator() {
        return mutator;
    }

    /**
     * Returns whether a test detected (killed) the mutation.
     *
     * @return true if detected
     */
    public boolean detected() {
        return detected;
    }

    /**
     * Returns whether the mutation was skipped (not evaluated).
     *
     * @return true if skipped
     */
    public boolean skipped() {
        return skipped;
    }

    /**
     * Returns the name of the killing test, or null.
     *
     * @return killing test name, or null
     */
    public String killingTest() {
        return killingTest;
    }
}
