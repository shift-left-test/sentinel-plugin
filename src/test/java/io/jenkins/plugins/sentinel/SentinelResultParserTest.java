/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.jenkins.plugins.sentinel.model.FileMutationResult;
import io.jenkins.plugins.sentinel.model.MutationEntry;
import io.jenkins.plugins.sentinel.model.SentinelResult;
import org.junit.jupiter.api.Test;

class SentinelResultParserTest {

    private static final String SAMPLE_XML = "mutations-sample.xml";

    @Test
    void parsesSampleXml() throws Exception {
        final SentinelResult result = parseResource(SAMPLE_XML);

        assertThat(result.overallScore().killed()).isEqualTo(3);
        assertThat(result.overallScore().survived()).isEqualTo(1);
        assertThat(result.overallScore().skipped()).isEqualTo(1);
        assertThat(result.overallScore().score())
                .isCloseTo(75.0, within(0.01));
    }

    @Test
    void parsesFileResults() throws Exception {
        final SentinelResult result = parseResource(SAMPLE_XML);

        assertThat(result.fileResults()).hasSize(2);

        final FileMutationResult foo = result.fileResults().stream()
                .filter(f -> "src/foo.cpp".equals(f.filePath()))
                .findFirst().orElseThrow();
        assertThat(foo.score().killed()).isEqualTo(2);
        assertThat(foo.score().survived()).isEqualTo(1);
        assertThat(foo.score().skipped()).isEqualTo(0);

        final FileMutationResult bar = result.fileResults().stream()
                .filter(f -> "src/bar.cpp".equals(f.filePath()))
                .findFirst().orElseThrow();
        assertThat(bar.score().killed()).isEqualTo(1);
        assertThat(bar.score().survived()).isEqualTo(0);
        assertThat(bar.score().skipped()).isEqualTo(1);
    }

    @Test
    void parsesEntries() throws Exception {
        final SentinelResult result = parseResource(SAMPLE_XML);
        assertThat(result.entries()).hasSize(5);
    }

    @Test
    void distinguishesSurvivedFromSkippedEntries() throws Exception {
        final SentinelResult result = parseResource(SAMPLE_XML);

        final MutationEntry survived = result.entries().stream()
                .filter(e -> "multiply".equals(e.mutatedMethod()))
                .findFirst().orElseThrow();
        assertThat(survived.detected()).isFalse();
        assertThat(survived.skipped()).isFalse();

        final MutationEntry skipped = result.entries().stream()
                .filter(e -> "init".equals(e.mutatedMethod()))
                .findFirst().orElseThrow();
        assertThat(skipped.detected()).isFalse();
        assertThat(skipped.skipped()).isTrue();
    }

    @Test
    void parsesEmptyMutationsXml() throws Exception {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <mutations>
                </mutations>
                """;
        try (InputStream in = new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8))) {
            final SentinelResult result =
                    SentinelResultParser.parse(in);
            assertThat(result.overallScore().total()).isEqualTo(0);
            assertThat(result.fileResults()).isEmpty();
            assertThat(result.entries()).isEmpty();
        }
    }

    @Test
    void throwsIoExceptionOnNonNumericLineNumber() {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <mutations>
                    <mutation detected="true">
                        <sourceFile>a.cpp</sourceFile>
                        <sourceFilePath>src/a.cpp</sourceFilePath>
                        <mutatedClass>A</mutatedClass>
                        <mutatedMethod>m</mutatedMethod>
                        <lineNumber>not-a-number</lineNumber>
                        <mutator>AOR</mutator>
                        <killingTest>T</killingTest>
                    </mutation>
                </mutations>
                """;
        final InputStream in = new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> SentinelResultParser.parse(in))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("lineNumber");
    }

    @Test
    void throwsOnInvalidXml() {
        final InputStream badInput = new ByteArrayInputStream(
                "not xml".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(
                () -> SentinelResultParser.parse(badInput))
                .isInstanceOf(IOException.class);
    }

    @Test
    void absentKillingTestElementYieldsNull() throws Exception {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <mutations>
                    <mutation detected="false">
                        <sourceFile>a.cpp</sourceFile>
                        <sourceFilePath>src/a.cpp</sourceFilePath>
                        <mutatedClass>A</mutatedClass>
                        <mutatedMethod>m</mutatedMethod>
                        <lineNumber>7</lineNumber>
                        <mutator>AOR</mutator>
                    </mutation>
                </mutations>
                """;
        final SentinelResult result = parse(xml);

        assertThat(result.entries()).singleElement()
                .satisfies(e -> assertThat(e.killingTest()).isNull());
        assertThat(result.overallScore().survived()).isEqualTo(1);
    }

    @Test
    void emptyKillingTestElementYieldsNull() throws Exception {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <mutations>
                    <mutation detected="true">
                        <sourceFile>a.cpp</sourceFile>
                        <sourceFilePath>src/a.cpp</sourceFilePath>
                        <mutatedClass>A</mutatedClass>
                        <mutatedMethod>m</mutatedMethod>
                        <lineNumber>7</lineNumber>
                        <mutator>AOR</mutator>
                        <killingTest></killingTest>
                    </mutation>
                </mutations>
                """;
        assertThat(parse(xml).entries()).singleElement()
                .satisfies(e -> assertThat(e.killingTest()).isNull());
    }

    @Test
    void surroundingWhitespaceIsTrimmedFromElementText() throws Exception {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <mutations>
                    <mutation detected="true">
                        <sourceFile>  a.cpp  </sourceFile>
                        <sourceFilePath>  src/a.cpp  </sourceFilePath>
                        <mutatedClass>  A  </mutatedClass>
                        <mutatedMethod>  m  </mutatedMethod>
                        <lineNumber>  7  </lineNumber>
                        <mutator>  AOR  </mutator>
                        <killingTest>  T  </killingTest>
                    </mutation>
                </mutations>
                """;
        final MutationEntry entry = parse(xml).entries().get(0);

        assertThat(entry.sourceFile()).isEqualTo("a.cpp");
        assertThat(entry.sourceFilePath()).isEqualTo("src/a.cpp");
        assertThat(entry.mutatedClass()).isEqualTo("A");
        assertThat(entry.mutatedMethod()).isEqualTo("m");
        assertThat(entry.mutator()).isEqualTo("AOR");
        assertThat(entry.killingTest()).isEqualTo("T");
        assertThat(entry.lineNumber()).isEqualTo(7);
    }

    @Test
    void absentLineNumberFailsWithAClearMessage() {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <mutations>
                    <mutation detected="true">
                        <sourceFile>a.cpp</sourceFile>
                        <sourceFilePath>src/a.cpp</sourceFilePath>
                        <mutatedClass>A</mutatedClass>
                        <mutatedMethod>m</mutatedMethod>
                        <mutator>AOR</mutator>
                    </mutation>
                </mutations>
                """;
        assertThatThrownBy(() -> parse(xml))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("lineNumber");
    }

    @Test
    void unknownDetectedValueCountsAsSurvived() throws Exception {
        // Anything that is neither "true" nor "skip" is not a kill.
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <mutations>
                    <mutation detected="maybe">
                        <sourceFile>a.cpp</sourceFile>
                        <sourceFilePath>src/a.cpp</sourceFilePath>
                        <mutatedClass>A</mutatedClass>
                        <mutatedMethod>m</mutatedMethod>
                        <lineNumber>1</lineNumber>
                        <mutator>AOR</mutator>
                    </mutation>
                </mutations>
                """;
        final SentinelResult result = parse(xml);

        assertThat(result.overallScore().survived()).isEqualTo(1);
        assertThat(result.overallScore().killed()).isZero();
        assertThat(result.overallScore().skipped()).isZero();
    }

    @Test
    void fileResultsKeepDocumentOrder() throws Exception {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <mutations>
                    <mutation detected="true">
                        <sourceFile>z.cpp</sourceFile>
                        <sourceFilePath>src/z.cpp</sourceFilePath>
                        <mutatedClass>Z</mutatedClass>
                        <mutatedMethod>m</mutatedMethod>
                        <lineNumber>1</lineNumber>
                        <mutator>AOR</mutator>
                        <killingTest>T</killingTest>
                    </mutation>
                    <mutation detected="true">
                        <sourceFile>a.cpp</sourceFile>
                        <sourceFilePath>src/a.cpp</sourceFilePath>
                        <mutatedClass>A</mutatedClass>
                        <mutatedMethod>m</mutatedMethod>
                        <lineNumber>1</lineNumber>
                        <mutator>AOR</mutator>
                        <killingTest>T</killingTest>
                    </mutation>
                </mutations>
                """;
        assertThat(parse(xml).fileResults())
                .extracting(FileMutationResult::filePath)
                .containsExactly("src/z.cpp", "src/a.cpp");
    }

    private SentinelResult parse(final String xml) throws Exception {
        try (InputStream in = new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8))) {
            return SentinelResultParser.parse(in);
        }
    }

    private SentinelResult parseResource(final String name)
            throws Exception {
        try (InputStream in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(name)) {
            return SentinelResultParser.parse(in);
        }
    }
}
