package io.jenkins.plugins.sentinel.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate mutation testing result containing overall score,
 * per-file results, and individual mutation entries.
 */
public record SentinelResult(
        MutationScore overallScore,
        List<FileMutationResult> fileResults,
        List<MutationEntry> entries) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Compact constructor that defensively copies the lists.
     *
     * @param overallScore the overall mutation score
     * @param fileResults  per-file results (will be copied)
     * @param entries      individual mutation entries (will be copied)
     */
    public SentinelResult {
        fileResults = Collections.unmodifiableList(
                new ArrayList<>(fileResults));
        entries = Collections.unmodifiableList(
                new ArrayList<>(entries));
    }
}
