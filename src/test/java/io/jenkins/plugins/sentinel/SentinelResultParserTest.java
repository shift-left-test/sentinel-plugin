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
import io.jenkins.plugins.sentinel.model.SentinelResult;
import org.junit.jupiter.api.Test;

class SentinelResultParserTest {

    @Test
    void parsesSampleXml() throws Exception {
        final SentinelResult result = parseResource("mutations-sample.xml");

        assertThat(result.overallScore().killed()).isEqualTo(3);
        assertThat(result.overallScore().survived()).isEqualTo(1);
        assertThat(result.overallScore().skipped()).isEqualTo(1);
        assertThat(result.overallScore().score())
                .isCloseTo(75.0, within(0.01));
    }

    @Test
    void parsesFileResults() throws Exception {
        final SentinelResult result = parseResource("mutations-sample.xml");

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
        final SentinelResult result = parseResource("mutations-sample.xml");
        assertThat(result.entries()).hasSize(5);
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
    void throwsOnInvalidXml() {
        final InputStream badInput = new ByteArrayInputStream(
                "not xml".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(
                () -> SentinelResultParser.parse(badInput))
                .isInstanceOf(IOException.class);
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
