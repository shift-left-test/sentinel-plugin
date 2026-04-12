package io.jenkins.plugins.sentinel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import io.jenkins.plugins.sentinel.model.FileMutationResult;
import io.jenkins.plugins.sentinel.model.MutationEntry;
import io.jenkins.plugins.sentinel.model.MutationScore;
import io.jenkins.plugins.sentinel.model.SentinelResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Parses sentinel's mutations.xml (PITest-compatible format)
 * into a SentinelResult.
 */
public final class SentinelResultParser {

    private static final String DETECTED_SKIP = "skip";
    private static final String DETECTED_TRUE = "true";
    private static final int IDX_KILLED = 0;
    private static final int IDX_SURVIVED = 1;
    private static final int IDX_SKIPPED = 2;
    private static final int COUNT_ARRAY_SIZE = 3;

    private SentinelResultParser() {
    }

    /**
     * Parses a mutations.xml file into a SentinelResult.
     *
     * @param xmlPath path to mutations.xml
     * @return parsed result
     * @throws IOException if file cannot be read or parsed
     */
    public static SentinelResult parse(final Path xmlPath)
            throws IOException {
        final Document doc = parseDocument(xmlPath);
        final NodeList mutationNodes =
                doc.getElementsByTagName("mutation");

        final List<MutationEntry> entries = new ArrayList<>();
        final int[] totals = new int[COUNT_ARRAY_SIZE];

        // Per-file accumulators: filePath -> [killed, survived, skipped]
        final Map<String, int[]> fileCounts = new LinkedHashMap<>();

        for (int i = 0; i < mutationNodes.getLength(); i++) {
            final Element elem =
                    (Element) mutationNodes.item(i);
            final String detectedAttr =
                    elem.getAttribute("detected");

            final String sourceFile =
                    getChildText(elem, "sourceFile");
            final String sourceFilePath =
                    getChildText(elem, "sourceFilePath");
            final String mutatedClass =
                    getChildText(elem, "mutatedClass");
            final String mutatedMethod =
                    getChildText(elem, "mutatedMethod");
            final int lineNumber = Integer.parseInt(
                    getChildText(elem, "lineNumber"));
            final String mutator =
                    getChildText(elem, "mutator");
            final String rawKillingTest =
                    getChildText(elem, "killingTest");

            final boolean skipped =
                    DETECTED_SKIP.equals(detectedAttr);
            final boolean detected =
                    !skipped && DETECTED_TRUE.equals(detectedAttr);
            final String killingTest =
                    (rawKillingTest != null
                            && rawKillingTest.isEmpty())
                            ? null : rawKillingTest;

            entries.add(new MutationEntry(
                    sourceFile, sourceFilePath,
                    mutatedClass, mutatedMethod,
                    lineNumber, mutator,
                    detected, killingTest));

            increment(totals, skipped, detected);
            increment(
                    fileCounts.computeIfAbsent(
                            sourceFilePath,
                            k -> new int[COUNT_ARRAY_SIZE]),
                    skipped, detected);
        }

        final MutationScore overallScore = new MutationScore(
                totals[IDX_KILLED],
                totals[IDX_SURVIVED],
                totals[IDX_SKIPPED]);

        final List<FileMutationResult> fileResults =
                new ArrayList<>();
        for (final Map.Entry<String, int[]> entry
                : fileCounts.entrySet()) {
            final int[] c = entry.getValue();
            fileResults.add(new FileMutationResult(
                    entry.getKey(),
                    new MutationScore(
                            c[IDX_KILLED],
                            c[IDX_SURVIVED],
                            c[IDX_SKIPPED])));
        }

        return new SentinelResult(overallScore, fileResults, entries);
    }

    private static void increment(
            final int[] counts,
            final boolean skipped,
            final boolean detected) {
        if (skipped) {
            counts[IDX_SKIPPED]++;
        } else if (detected) {
            counts[IDX_KILLED]++;
        } else {
            counts[IDX_SURVIVED]++;
        }
    }

    private static Document parseDocument(final Path xmlPath)
            throws IOException {
        try {
            final DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
            // Disable external entities for security
            factory.setFeature(
                    "http://apache.org/xml/features/"
                            + "disallow-doctype-decl",
                    true);
            final DocumentBuilder builder =
                    factory.newDocumentBuilder();
            return builder.parse(xmlPath.toFile());
        } catch (ParserConfigurationException
                 | SAXException e) {
            throw new IOException(
                    "Failed to parse mutations.xml: "
                            + xmlPath, e);
        }
    }

    private static String getChildText(
            final Element parent, final String tagName) {
        final NodeList nodes =
                parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        final String text = nodes.item(0).getTextContent();
        return text != null ? text.trim() : null;
    }
}
