// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Original licensed-font corpus; no commercial score, model or native runtime. */
public class EighthLiteralRecoveryTest {
    @ClassRule public static final TemporaryFolder TEMP = new TemporaryFolder();
    private static Path root;
    private static Path matrix;

    @BeforeClass
    public static void originals() throws Exception {
        root = TEMP.newFolder("eighth-originals").toPath();
        matrix = root.resolve("original-matrix-v2");
        OriginalGlyphMatrix.main(new String[] {"java/assets/Bravura.otf", matrix.toString()});
        List<String> names;
        try (var paths = Files.list(matrix)) {
            names =
                    paths.map(p -> p.getFileName().toString().replace(".bin", ""))
                            .sorted()
                            .toList();
        }
        Files.write(root.resolve("original-matrix-specs-v2.txt"), names);
        Files.copy(
                Path.of("java/assets/eighth-rest-original-expected.txt"),
                root.resolve("helper-expected.txt"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    public void literalRawEndpointsClassificationAndRestoration() throws Exception {
        EighthHelperControls.main(new String[] {root.toString(), matrix.toString()});
        EighthClassificationControls.main(new String[] {matrix.toString()});
    }

    @Test
    public void allNewPaidCoresKeepDefaultSemanticsAndAtomicQuotas() throws Exception {
        EighthPaidCoreControls.main(new String[] {"paid"});
    }

    @Test
    public void wholeCallerRivalsLegacyPriorityAndQuarterRemainder() throws Exception {
        EighthCallerControls.main(new String[] {root.toString(), matrix.toString()});
        EighthPositiveControls.main(new String[] {matrix.toString()});
    }

    @Test
    public void priorOrdinaryProducerAndQuarterStageStayIntact() throws Exception {
        EighthQuarterStageControls.main(new String[0]);
    }

    @Test
    public void exactWitnessCannotBorrowDimensionsOrBudget() throws Exception {
        EighthAffinityControls.main(new String[] {matrix.toString()});
    }
}
