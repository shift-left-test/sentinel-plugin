package io.jenkins.plugins.sentinel.model;

import java.io.Serializable;

/**
 * A single mutation entry parsed from mutations.xml.
 */
public record MutationEntry(
        String sourceFile,
        String sourceFilePath,
        String mutatedClass,
        String mutatedMethod,
        int lineNumber,
        String mutator,
        boolean detected,
        String killingTest) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a skipped mutation entry (not detected, no killing test).
     *
     * @param sourceFile     source file name
     * @param sourceFilePath source file path
     * @param mutatedClass   mutated class name
     * @param mutatedMethod  mutated method name
     * @param lineNumber     line number
     * @param mutator        mutator name
     * @return skipped MutationEntry
     */
    public static MutationEntry skipped(
            final String sourceFile,
            final String sourceFilePath,
            final String mutatedClass,
            final String mutatedMethod,
            final int lineNumber,
            final String mutator) {
        return new MutationEntry(
                sourceFile, sourceFilePath,
                mutatedClass, mutatedMethod,
                lineNumber, mutator,
                false, null);
    }
}
