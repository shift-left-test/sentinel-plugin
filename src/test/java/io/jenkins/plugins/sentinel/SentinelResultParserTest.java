package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import io.jenkins.plugins.sentinel.model.FileMutationResult;
import io.jenkins.plugins.sentinel.model.SentinelResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void parsesEmptyMutationsXml(@TempDir final Path tempDir)
            throws Exception {
        final Path xml = tempDir.resolve("mutations.xml");
        Files.writeString(xml, """
                <?xml version="1.0" encoding="UTF-8"?>
                <mutations>
                </mutations>
                """);
        final SentinelResult result = SentinelResultParser.parse(xml);
        assertThat(result.overallScore().total()).isEqualTo(0);
        assertThat(result.fileResults()).isEmpty();
        assertThat(result.entries()).isEmpty();
    }

    @Test
    void throwsOnMissingFile(@TempDir final Path tempDir) {
        final Path missing = tempDir.resolve("nonexistent.xml");
        assertThatThrownBy(
                () -> SentinelResultParser.parse(missing))
                .isInstanceOf(IOException.class);
    }

    private SentinelResult parseResource(final String name)
            throws Exception {
        try (InputStream is = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(name)) {
            final Path tempFile = Files.createTempFile(
                    "mutations-", ".xml");
            Files.copy(is, tempFile,
                    java.nio.file.StandardCopyOption
                            .REPLACE_EXISTING);
            try {
                return SentinelResultParser.parse(tempFile);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }
}
