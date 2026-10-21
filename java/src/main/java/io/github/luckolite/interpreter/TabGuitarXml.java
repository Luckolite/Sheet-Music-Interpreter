// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.w3c.dom.Element;

/** MusicXML 4 guitar gestures, expressed in semitones rather than target frets. */
public final class TabGuitarXml {
    private TabGuitarXml() {}

    public static void write(Element notation, int marks) {
        String kind = TabEffect.name(marks);
        double delta = TabEffect.semitones(marks);
        if (kind.equals("bend")
                || kind.equals("bend_release")
                || kind.equals("whammy")
                || kind.equals("whammy_release")
                || kind.equals("pre_bend")) {
            Element technical = child(notation, "technical");
            bend(technical, delta, false, kind.startsWith("whammy"), kind.equals("pre_bend"));
            if (kind.endsWith("_release"))
                bend(technical, -delta, true, kind.startsWith("whammy"), false);
        } else if (kind.startsWith("strum_")) {
            child(notation, "arpeggiate")
                    .setAttribute("direction", kind.equals("strum_down") ? "up" : "down");
        } else if ((marks & TabEffect.ALL) != 0) {
            Element other = child(notation, "other-notation");
            other.setAttribute("type", "single");
            other.setTextContent(kind + (delta == 0 ? "" : " (" + delta + " semitones)"));
        }
        if ((marks & TabEffect.VIBRATO) != 0) {
            Element other = child(notation, "other-notation");
            other.setAttribute("type", "single");
            other.setTextContent("vibrato");
        }
    }

    private static void bend(
            Element parent, double delta, boolean release, boolean bar, boolean pre) {
        Element bend = child(parent, "bend");
        bend.setAttribute("shape", "curved");
        child(bend, "bend-alter").setTextContent(Double.toString(delta));
        if (pre) child(bend, "pre-bend");
        if (release) child(bend, "release");
        if (bar) child(bend, "with-bar");
    }

    private static Element child(Element parent, String name) {
        Element child = parent.getOwnerDocument().createElement(name);
        parent.appendChild(child);
        return child;
    }
}
