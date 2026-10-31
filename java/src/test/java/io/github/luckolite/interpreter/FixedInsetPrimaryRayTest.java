// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import org.junit.Test;

/** Complete-ray oracles, including an adversarial union that must never be accepted. */
public final class FixedInsetPrimaryRayTest {
    @Test
    public void originalParameterizedBeamSources() {
        for (var c : PrimaryRayScenes.CASES) {
            var p = PrimaryRayScenes.page(c);
            assertEquals(c.name(), c.next(), PrimaryRayScenes.read(p));
        }
    }

    @Test
    public void rayPremisesAndAntiUnionAreReal() {
        for (var c : PrimaryRayScenes.CASES) {
            var p = PrimaryRayScenes.page(c);
            String m = PrimaryRayScenes.metrics(p);
            java.util.Map<String, String> v = new java.util.HashMap<>();
            for (String part : m.substring(1, m.length() - 1).split(",")) {
                String[] a = part.split(":");
                v.put(a[0].replace("\"", ""), a[1]);
            }
            int total = Integer.parseInt(v.get("total")),
                    old = Integer.parseInt(v.get("legacy")),
                    inner = Integer.parseInt(v.get("inset")),
                    union = Integer.parseInt(v.get("union"));
            if (c.kind().equals("aa")) {
                assertTrue(c.name(), old < total * .9f);
                assertTrue(c.name(), inner >= total * .9f);
                assertEquals("true", v.get("bfs"));
            }
            if (c.kind().equals("narrow")) {
                assertTrue(old >= total * .9f);
                assertTrue(inner < total * .9f);
                assertEquals("true", v.get("bfs"));
            }
            if (c.kind().equals("alternating")) {
                assertTrue(old < total * .9f && inner < total * .9f);
                assertTrue(union >= total * .9f);
                assertEquals("true", v.get("bfs"));
            }
            if (c.kind().equals("thin")) {
                assertTrue(old < total * .9f && inner < total * .9f);
            }
        }
    }

    @Test
    public void openingPickupActualTypedHeaderClockAndGuards() {
        for (String mode : PrimaryRayScenes.CONSUMERS)
            assertEquals(
                    mode,
                    PrimaryRayScenes.expectedConsumer(mode, true),
                    PrimaryRayScenes.consumer(mode) != null);
    }
}
