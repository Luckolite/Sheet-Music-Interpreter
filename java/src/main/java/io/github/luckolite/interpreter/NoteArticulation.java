// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Adapted from Music Sheets: standalone package and platform-independent diagnostics.
package io.github.luckolite.interpreter;

/** Independent performance marks: never change written duration or the score clock. */
public final class NoteArticulation {
    public static final int ACCENT=1, MARCATO=2, STACCATO=4, TENUTO=8, STACCATISSIMO=16;
    public static final int ARTICULATIONS=31, ALL=ARTICULATIONS|NoteOrnament.ALL|TabEffect.ALL;
    private NoteArticulation() { }
    public static double gate(int marks) {
        if(TabEffect.gate(marks)<1)return TabEffect.gate(marks);
        if((marks&STACCATISSIMO)!=0)return .28;
        if((marks&STACCATO)!=0)return (marks&TENUTO)!=0?.75:.5;
        return 1; // Unmarked, accented and tenuto notes retain their full written sustain.
    }
    public static double gain(int marks,double age) {
        double emphasis=(marks&MARCATO)!=0?1.25:(marks&ACCENT)!=0?.8:0;
        // Give a real bow/pluck time to speak before the emphasis decays. The old 90 ms
        // exponential spent most of its boost while a recorded bow was still attacking.
        return TabEffect.gain(marks)*(1+emphasis*Math.exp(-Math.max(0,age-.12)/.13));
    }
}
