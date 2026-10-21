// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import static org.junit.Assert.*;

public class TabGuitarXmlTest {
    @Test
    public void fractionalSignedReleaseHasTwoDeltasAndBarIdentity() throws Exception {
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        var notation = doc.createElement("notations");
        TabGuitarXml.write(notation, TabEffect.encodePitch(TabEffect.BAR_RELEASE, -.5));
        var deltas = notation.getElementsByTagName("bend-alter");
        assertEquals(2, deltas.getLength());
        assertEquals("-0.5", deltas.item(0).getTextContent());
        assertEquals("0.5", deltas.item(1).getTextContent());
        assertEquals(2, notation.getElementsByTagName("with-bar").getLength());
        assertEquals(1, notation.getElementsByTagName("release").getLength());
    }

    @Test
    public void strumDownMeansAscendingSoundingPitch() throws Exception {
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        var notation = doc.createElement("notations");
        TabGuitarXml.write(notation, TabEffect.encode(TabEffect.STRUM_DOWN, 2));
        assertEquals(
                "up", ((org.w3c.dom.Element) notation.getFirstChild()).getAttribute("direction"));
    }
}
