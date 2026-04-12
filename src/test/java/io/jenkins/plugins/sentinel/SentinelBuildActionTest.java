package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;

import io.jenkins.plugins.sentinel.model.FileMutationResult;
import io.jenkins.plugins.sentinel.model.MutationEntry;
import io.jenkins.plugins.sentinel.model.MutationScore;
import io.jenkins.plugins.sentinel.model.SentinelResult;
import org.junit.jupiter.api.Test;

class SentinelBuildActionTest {

    private static final int KILLED_80 = 80;
    private static final int SURVIVED_20 = 20;
    private static final int SKIPPED_5 = 5;
    private static final int LINE_10 = 10;
    private static final int LINE_20 = 20;
    private static final int LINE_30 = 30;

    @Test
    void formattedScoreReturnsOneDecimalPlace() {
        final SentinelBuildAction action = createAction(KILLED_80, SURVIVED_20, 0);
        assertThat(action.getFormattedScore()).isEqualTo("80.0");
    }

    @Test
    void formattedScoreWithFractionalValue() {
        final SentinelBuildAction action = createAction(2, 1, 0);
        assertThat(action.getFormattedScore()).isEqualTo("66.7");
    }

    @Test
    void scoreColorGreenWhenAbove80() {
        final SentinelBuildAction action = createAction(KILLED_80, SURVIVED_20, 0);
        assertThat(action.getScoreColor()).isEqualTo("#1ea64b");
    }

    @Test
    void scoreColorGreenWhenExactly80() {
        final SentinelBuildAction action = createAction(4, 1, 0);
        assertThat(action.getScoreColor()).isEqualTo("#1ea64b");
    }

    @Test
    void scoreColorOrangeWhenAbove50() {
        final SentinelBuildAction action = createAction(3, 2, 0);
        assertThat(action.getScoreColor()).isEqualTo("#fe820a");
    }

    @Test
    void scoreColorOrangeWhenExactly50() {
        final SentinelBuildAction action = createAction(1, 1, 0);
        assertThat(action.getScoreColor()).isEqualTo("#fe820a");
    }

    @Test
    void scoreColorRedWhenBelow50() {
        final SentinelBuildAction action = createAction(1, 3, 0);
        assertThat(action.getScoreColor()).isEqualTo("#e6001f");
    }

    @Test
    void scoreColorRedWhenZero() {
        final SentinelBuildAction action = createAction(0, 0, 0);
        assertThat(action.getScoreColor()).isEqualTo("#e6001f");
    }

    @Test
    void totalWithSkippedIncludesAllThreeCounts() {
        final SentinelBuildAction action = createAction(KILLED_80, SURVIVED_20, SKIPPED_5);
        assertThat(action.getTotalWithSkipped())
                .isEqualTo(KILLED_80 + SURVIVED_20 + SKIPPED_5);
    }

    @Test
    void killedPercentCalculatesCorrectly() {
        final SentinelBuildAction action = createAction(KILLED_80, SURVIVED_20, 0);
        assertThat(action.getKilledPercent()).isEqualTo(KILLED_80);
    }

    @Test
    void survivedPercentCalculatesCorrectly() {
        final SentinelBuildAction action = createAction(KILLED_80, SURVIVED_20, 0);
        assertThat(action.getSurvivedPercent()).isEqualTo(SURVIVED_20);
    }

    @Test
    void skippedPercentCalculatesCorrectly() {
        final SentinelBuildAction action = createAction(KILLED_80, 0, SURVIVED_20);
        assertThat(action.getSkippedPercent()).isEqualTo(SURVIVED_20);
    }

    @Test
    void percentsReturnZeroWhenTotalIsZero() {
        final SentinelBuildAction action = createAction(0, 0, 0);
        assertThat(action.getKilledPercent()).isZero();
        assertThat(action.getSurvivedPercent()).isZero();
        assertThat(action.getSkippedPercent()).isZero();
    }

    @Test
    void percentsWithSkippedIncluded() {
        final SentinelBuildAction action = createAction(KILLED_80, SURVIVED_20, SKIPPED_5);
        final int total = KILLED_80 + SURVIVED_20 + SKIPPED_5;
        assertThat(action.getKilledPercent())
                .isCloseTo(KILLED_80 * 100 / total, within(1));
        assertThat(action.getSurvivedPercent())
                .isCloseTo(SURVIVED_20 * 100 / total, within(1));
        assertThat(action.getSkippedPercent())
                .isCloseTo(SKIPPED_5 * 100 / total, within(1));
    }

    @Test
    void mutatorDistributionCountsByMutator() {
        final SentinelBuildAction action = createActionWithEntries(List.of(
                entry("AOR", LINE_10),
                entry("AOR", LINE_20),
                entry("ROR", LINE_30)));
        final Map<String, Integer> dist = action.getMutatorDistribution();
        assertThat(dist).containsEntry("AOR", 2);
        assertThat(dist).containsEntry("ROR", 1);
    }

    @Test
    void mutatorDistributionEmptyWhenNoEntries() {
        final SentinelBuildAction action = createActionWithEntries(List.of());
        assertThat(action.getMutatorDistribution()).isEmpty();
    }

    @Test
    void mutatorDistributionJsonFormatsCorrectly() {
        final SentinelBuildAction action = createActionWithEntries(List.of(
                entry("AOR", LINE_10),
                entry("AOR", LINE_20),
                entry("ROR", LINE_30)));
        final String json = action.getMutatorDistributionJson();
        assertThat(json).contains("\"name\"");
        assertThat(json).contains("\"value\"");
        assertThat(json).contains("AOR");
        assertThat(json).contains("ROR");
    }

    @Test
    void fileCountReturnsNumberOfFileResults() {
        final List<FileMutationResult> files = List.of(
                new FileMutationResult("Foo.java", new MutationScore(1, 0, 0)),
                new FileMutationResult("Bar.java", new MutationScore(1, 0, 0)));
        final SentinelResult result = new SentinelResult(
                new MutationScore(2, 0, 0), files, List.of());
        final SentinelBuildAction action = new SentinelBuildAction(result);
        assertThat(action.getFileCount()).isEqualTo(2);
    }

    @Test
    void entryCountReturnsNumberOfEntries() {
        final SentinelBuildAction action = createActionWithEntries(List.of(
                entry("AOR", LINE_10),
                entry("ROR", LINE_20)));
        assertThat(action.getEntryCount()).isEqualTo(2);
    }

    @Test
    void iconFileNameReturnsGraphPng() {
        final SentinelBuildAction action = createAction(1, 0, 0);
        assertThat(action.getIconFileName()).isEqualTo("graph.png");
    }

    @Test
    void displayNameReturnsMutationReport() {
        final SentinelBuildAction action = createAction(1, 0, 0);
        assertThat(action.getDisplayName()).isEqualTo("Sentinel Report");
    }

    @Test
    void urlNameReturnsSentinel() {
        final SentinelBuildAction action = createAction(1, 0, 0);
        assertThat(action.getUrlName()).isEqualTo("sentinel");
    }

    private SentinelBuildAction createAction(
            final int killed, final int survived, final int skipped) {
        final MutationScore score = new MutationScore(killed, survived, skipped);
        final SentinelResult result = new SentinelResult(
                score, List.of(), List.of());
        return new SentinelBuildAction(result);
    }

    private SentinelBuildAction createActionWithEntries(
            final List<MutationEntry> entries) {
        final MutationScore score = new MutationScore(
                entries.size(), 0, 0);
        final SentinelResult result = new SentinelResult(
                score, List.of(), entries);
        return new SentinelBuildAction(result);
    }

    private MutationEntry entry(final String mutator, final int line) {
        return new MutationEntry(
                "Foo.java", "src/Foo.java",
                "Foo", "bar", line, mutator,
                true, "FooTest::testBar");
    }
}
