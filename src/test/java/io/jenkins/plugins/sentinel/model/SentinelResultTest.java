/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import hudson.util.XStream2;
import org.junit.jupiter.api.Test;

class SentinelResultTest {

    private static final String FOO_CPP = "src/foo.cpp";
    private static final String BAR_CPP = "src/bar.cpp";

    @Test
    void roundTripsThroughXStreamForBuildXmlPersistence() {
        // SentinelBuildAction (a RunAction2) is persisted into build.xml via
        // Jenkins' XStream2. The model must survive a serialize -> deserialize
        // cycle so the report reloads after a controller restart.
        final XStream2 xstream = new XStream2();
        xstream.allowTypes(new Class<?>[] {
            SentinelResult.class, MutationScore.class,
            FileMutationResult.class, MutationEntry.class});

        final SentinelResult original = new SentinelResult(
                new MutationScore(3, 1, 1),
                List.of(new FileMutationResult(
                        FOO_CPP, new MutationScore(2, 1, 0))),
                List.of(new MutationEntry(
                        "foo.cpp", FOO_CPP, "Foo", "bar",
                        42, "AOR", true, false, "FooTest")));

        final String xml = xstream.toXML(original);
        final SentinelResult restored =
                (SentinelResult) xstream.fromXML(xml);

        assertThat(restored.overallScore())
                .isEqualTo(original.overallScore());
        assertThat(restored.fileResults()).hasSize(1);
        assertThat(restored.fileResults().get(0).filePath())
                .isEqualTo(FOO_CPP);
        assertThat(restored.entries()).hasSize(1);
        final MutationEntry entry = restored.entries().get(0);
        assertThat(entry.sourceFilePath()).isEqualTo(FOO_CPP);
        assertThat(entry.lineNumber()).isEqualTo(42);
        assertThat(entry.detected()).isTrue();
        assertThat(entry.skipped()).isFalse();
        assertThat(entry.killingTest()).isEqualTo("FooTest");
    }

    @Test
    void entryFieldsAreAccessible() {
        final MutationEntry entry = new MutationEntry(
                "foo.cpp", FOO_CPP, "MyClass", "myMethod",
                42, "AOR", true, false, "TestA");
        assertThat(entry.sourceFile()).isEqualTo("foo.cpp");
        assertThat(entry.sourceFilePath()).isEqualTo(FOO_CPP);
        assertThat(entry.mutatedClass()).isEqualTo("MyClass");
        assertThat(entry.mutatedMethod()).isEqualTo("myMethod");
        assertThat(entry.lineNumber()).isEqualTo(42);
        assertThat(entry.mutator()).isEqualTo("AOR");
        assertThat(entry.detected()).isTrue();
        assertThat(entry.skipped()).isFalse();
        assertThat(entry.killingTest()).isEqualTo("TestA");
    }

    @Test
    void skippedEntryIsNeitherDetectedNorKilledByATest() {
        final MutationEntry entry = new MutationEntry(
                "bar.cpp", BAR_CPP, "BarClass", "barMethod",
                10, "ROR", false, true, null);
        assertThat(entry.detected()).isFalse();
        assertThat(entry.skipped()).isTrue();
        assertThat(entry.killingTest()).isNull();
    }

    @Test
    void fileMutationResultAggregatesScore() {
        final FileMutationResult result = new FileMutationResult(
                FOO_CPP, new MutationScore(8, 2, 1));
        assertThat(result.filePath()).isEqualTo(FOO_CPP);
        assertThat(result.score().killed()).isEqualTo(8);
        assertThat(result.score().score())
                .isCloseTo(80.0, within(0.01));
    }

    @Test
    void sentinelResultAggregatesTotalScore() {
        final List<FileMutationResult> files = List.of(
                new FileMutationResult(FOO_CPP,
                        new MutationScore(8, 2, 1)),
                new FileMutationResult(BAR_CPP,
                        new MutationScore(12, 3, 0)));
        final SentinelResult result = new SentinelResult(
                new MutationScore(20, 5, 1), files, List.of());
        assertThat(result.overallScore().killed()).isEqualTo(20);
        assertThat(result.overallScore().survived()).isEqualTo(5);
        assertThat(result.overallScore().score())
                .isCloseTo(80.0, within(0.01));
        assertThat(result.fileResults()).hasSize(2);
    }

    @Test
    void sentinelResultIsSerializable() {
        final SentinelResult result = new SentinelResult(
                new MutationScore(10, 5, 2),
                List.of(), List.of());
        assertThat(result).isInstanceOf(java.io.Serializable.class);
    }
}
