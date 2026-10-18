// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class OctaveDirectionOrderOriginalTest {
    static Element child(Element p, String name, String text) {
        var e = p.getOwnerDocument().createElement(name);
        if (text != null) e.setTextContent(text);
        p.appendChild(e);
        return e;
    }

    static Document scene(int[] shifts, boolean[] grace, int[] voices, int[] staffs)
            throws Exception {
        var d = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        var root = d.createElement("score-partwise");
        d.appendChild(root);
        var part = child(root, "part", null);
        var m = child(part, "measure", null);
        for (int i = 0; i < shifts.length; i++) {
            var n = child(m, "note", null);
            if (grace[i]) child(n, "grace", null);
            var pitch = child(n, "pitch", null);
            child(pitch, "step", "G");
            child(pitch, "octave", Integer.toString(3 + shifts[i]));
            if (!grace[i]) child(n, "duration", "1");
            child(n, "voice", Integer.toString(voices[i]));
            child(n, "staff", Integer.toString(staffs[i]));
            MusicXmlOctaveShifts.mark(n, shifts[i]);
        }
        return d;
    }

    static List<String> observed(Document d) {
        var part = (Element) d.getElementsByTagName("measure").item(0);
        var states = new HashMap<String, Integer>();
        var result = new ArrayList<String>();
        for (var node = part.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element e)) continue;
            if (e.getTagName().equals("direction")) {
                var x = (Element) e.getElementsByTagName("octave-shift").item(0);
                String v = e.getElementsByTagName("voice").item(0).getTextContent();
                states.put(
                        v,
                        x.getAttribute("type").equals("stop")
                                ? 0
                                : (x.getAttribute("type").equals("down") ? 1 : -1)
                                        * ((Integer.parseInt(x.getAttribute("size")) - 1) / 7));
            } else if (e.getTagName().equals("note")) {
                String v = e.getElementsByTagName("voice").item(0).getTextContent();
                result.add(v + ":" + states.getOrDefault(v, 0));
            }
        }
        return result;
    }

    @Test
    public void unshiftedGraceThenShiftedPrincipal() throws Exception {
        var d =
                scene(
                        new int[] {0, 1},
                        new boolean[] {true, false},
                        new int[] {1, 1},
                        new int[] {1, 1});
        MusicXmlOctaveShifts.apply(d);
        assertEquals(List.of("1:0", "1:1"), observed(d));
    }

    @Test
    public void graceAtOpposingShiftBoundaryUsesItsActualOrder() throws Exception {
        var d =
                scene(
                        new int[] {1, 1, -1},
                        new boolean[] {false, true, false},
                        new int[] {1, 1, 1},
                        new int[] {1, 1, 1});
        MusicXmlOctaveShifts.apply(d);
        assertEquals(List.of("1:1", "1:1", "1:-1"), observed(d));
    }

    @Test
    public void afterGraceMayReturnToWrittenOctave() throws Exception {
        var d =
                scene(
                        new int[] {1, 0},
                        new boolean[] {false, true},
                        new int[] {1, 1},
                        new int[] {1, 1});
        MusicXmlOctaveShifts.apply(d);
        assertEquals(List.of("1:1", "1:0"), observed(d));
    }

    @Test
    public void normalAndDenseVirtualVoicesNeverAliasObjects() throws Exception {
        var d =
                scene(
                        new int[] {1, -1, 2},
                        new boolean[] {false, false, false},
                        new int[] {101, 5, 105},
                        new int[] {1, 2, 1});
        MusicXmlOctaveShifts.apply(d);
        var active = new HashSet<String>();
        var ds = d.getElementsByTagName("octave-shift");
        for (int i = 0; i < ds.getLength(); i++) {
            var e = (Element) ds.item(i);
            var k = e.getAttribute("number");
            assertTrue(Integer.parseInt(k) >= 1 && Integer.parseInt(k) <= 16);
            if (e.getAttribute("type").equals("stop")) assertTrue(active.remove(k));
            else assertTrue(active.add(k));
        }
        assertTrue(active.isEmpty());
        assertEquals(List.of("101:1", "5:-1", "105:2"), observed(d));
    }

    @Test
    public void zeroShiftDoesNotInsertAnyDirection() throws Exception {
        var d =
                scene(
                        new int[] {0, 0},
                        new boolean[] {true, false},
                        new int[] {1, 1},
                        new int[] {1, 1});
        MusicXmlOctaveShifts.apply(d);
        assertEquals(0, d.getElementsByTagName("direction").getLength());
    }
}
