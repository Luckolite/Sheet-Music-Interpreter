// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** JUnit discovery adapter for unchanged original finite geometry/flag controls. */
public final class PrintedSourceBeamOriginalsTest {
    @ClassRule public static final TemporaryFolder temporary = new TemporaryFolder();
    private static final Map<String, Path> resources = new HashMap<>();

    private static Path inputs(String name) throws IOException {
        Path value = resources.get(name);
        if (value == null) {
            value = PrintedBeamOriginalResources.materialize(name, temporary);
            resources.put(name, value);
        }
        return value;
    }

    private static String manifest(String name) throws IOException {
        return inputs(name).resolve("fixtures.tsv").toString();
    }

    @Test
    public void rawFiveRuleWitnesses() {
        PrintedBeamRuleWitnessOriginals.main(new String[0]);
    }

    @Test
    public void literalShaftCandidates() {
        PrintedBeamShaftsOriginals.main(new String[0]);
    }

    @Test
    public void finiteGroupsCoreAndTerminalControls() throws Exception {
        PrintedBeamGeometryOriginals.main(new String[] {manifest("geometry")});
    }

    @Test
    public void unknownCarrierCannotFundProof() {
        GeometryUnknownCarrierOriginals.main(new String[0]);
    }

    @Test
    public void isolatedFlagProceduralInputs() throws Exception {
        PrintedIsolatedFlagsOriginals.main(new String[] {manifest("flags")});
    }

    @Test
    public void isolatedFlagRootAndRivalControls() throws Exception {
        PrintedIsolatedFlagsRoleOriginals.main(new String[] {manifest("flags")});
    }

    @Test
    public void finiteJunctionInputs() throws Exception {
        PrintedIsolatedFlagsOriginals.main(
                new String[] {inputs("flags").resolve("junctions/fixtures.tsv").toString()});
    }

    @Test
    public void rawPrimaryPossibilityAtJunctions() throws Exception {
        RawPrimaryBeamAbsenceOriginals.main(
                new String[] {inputs("flags").resolve("junctions/fixtures.tsv").toString()});
    }

    @Test
    public void intervalAndUnknownBoundaries() throws Exception {
        PrimaryAbsenceBoundaryProbe.main(new String[0]);
    }

    @Test
    public void pairFamilyContainment() {
        PairFamilyContainmentOriginals.main(new String[0]);
    }

    @Test
    public void fullFlagUnknownPrimaryGuards() {
        FlagPrimaryJunctionOriginals.main(new String[0]);
    }

    @Test
    public void allOverlappingRivalContacts() {
        OverlappingRivalOriginals.main(new String[0]);
    }

    @FunctionalInterface
    private interface Original {
        void run() throws Exception;
    }

    private static List<String> output(Original body) throws Exception {
        PrintStream previous = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream captured = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            try {
                body.run();
            } finally {
                System.setOut(previous);
            }
        }
        List<String> rows = new ArrayList<>();
        for (String line : bytes.toString(StandardCharsets.UTF_8).split("\\R"))
            if (!line.isBlank()) rows.add(line);
        return rows;
    }

    @Test
    public void mixedEndpointSafetyObservationsAreAssertions() throws Exception {
        List<String> rows = output(() -> PrimaryAbsenceIndependentProbe.main(new String[0]));
        assertEquals(28, rows.size());
        for (String row : rows) {
            if (row.contains("\"geometryAdmitted\":")) {
                assertTrue(row.toString(), flag(row, "geometryAdmitted"));
                assertFalse(row.toString(), flag(row, "absenceCertified"));
                assertNotEquals(row.toString(), "IMPOSSIBLE", text(row, "actualRivalPossibility"));
            } else {
                assertEquals(row.toString(), !flag(row, "beam"), flag(row, "flagAdmitted"));
                assertFalse(row.toString(), flag(row, "budgetExhausted"));
            }
        }
    }

    @Test
    public void witnessedFamiliesAndOverlappingRivals() throws Exception {
        List<String> rows = output(() -> IndependentV3Probe.main(new String[0]));
        assertEquals(7, rows.size());
        for (String row : rows.subList(0, 6)) {
            String verdict =
                    row.contains("\"absenceCertified\":") ? "absenceCertified" : "flagAdmitted";
            assertEquals(row.toString(), !flag(row, "joined"), flag(row, verdict));
        }
        assertEquals(721, number(rows.get(6), "assertions"));
        assertEquals(180, number(rows.get(6), "witnessedFamilies"));
    }

    @Test
    public void fixedReportFormatRejectsMissingOrDuplicateFields() {
        assertTrue(flag("{\"accepted\":true}", "accepted"));
        assertFalse(flag("{\"accepted\":false}", "accepted"));
        assertEquals("UNKNOWN", text("{\"state\":\"UNKNOWN\"}", "state"));
        assertThrows(AssertionError.class, () -> flag("{}", "accepted"));
        assertThrows(
                AssertionError.class,
                () -> flag("{\"accepted\":true,\"accepted\":false}", "accepted"));
        assertThrows(AssertionError.class, () -> flag("{\"accepted\":\"true\"}", "accepted"));
        assertThrows(AssertionError.class, () -> number("{\"count\":false}", "count"));
    }

    // These probes emit a fixed, flat test-report format. Match only the scalar
    // fields asserted above; this is not a JSON parser or production adapter.
    private static String scalar(String row, String field, String token) {
        Matcher match =
                Pattern.compile(
                                "\\\""
                                        + Pattern.quote(field)
                                        + "\\\"\\s*:\\s*("
                                        + token
                                        + ")(?=[,}])")
                        .matcher(row);
        assertTrue("Missing original report field: " + field + " in " + row, match.find());
        String value = match.group(1);
        assertFalse("Duplicate original report field: " + field, match.find());
        return value;
    }

    private static boolean flag(String row, String field) {
        return Boolean.parseBoolean(scalar(row, field, "true|false"));
    }

    private static int number(String row, String field) {
        return Integer.parseInt(scalar(row, field, "[0-9]+"));
    }

    private static String text(String row, String field) {
        String value = scalar(row, field, "\\\"[A-Z_]+\\\"");
        return value.substring(1, value.length() - 1);
    }
}
