// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Shareable regression for forwarding an OCR-read parenthesized octave direction. */
public class OcrOctaveSpanTest {
    private static final int W=700,H=600;

    @Test public void parenthesizedOcrSpanRaisesEveryCoveredNote() {
        byte[] gray=new byte[W*H];Arrays.fill(gray,(byte)255);
        for(int x=120;x<=370;x++)if((x-120)%14<7){gray[55*W+x]=0;gray[56*W+x]=0;}
        var staff=new PlayingTechniqueDetector.Staff(100,164,16,0,1);
        var measure=List.of(new MeasureRegion(0,1,0,1));
        var word=new PlayingTechniqueDetector.Word("(8va)--------",80/(float)W,40/(float)H,
                120/(float)W,60/(float)H);
        var notes=List.of(note(150),note(300),note(500));

        var result=OctaveMarkDetector.apply(List.of(word),List.of(staff),measure,notes,gray,W,H);

        assertEquals(List.of(1,1,0),result.stream().map(ScoreNoteEvent::octaveShift).toList());
    }

    private static ScoreNoteEvent note(int x) {
        return new ScoreNoteEvent(0,x/(float)W,2,0,1,150/(float)H,
                false,0,0,1,1).withClef(ScoreNoteEvent.CLEF_TREBLE);
    }
}
