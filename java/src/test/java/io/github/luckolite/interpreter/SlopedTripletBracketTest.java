// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SlopedTripletBracketTest {
    private static List<ScoreNoteEvent> read(boolean leftArm, boolean rightArm, int shift) {
        byte[] gray=new byte[400*240];Arrays.fill(gray,(byte)255);
        String[] glyph={"..#######...", ".##########.", "###......###", "####.....###",
            "####.....###", "####.....###", ".##.....####", ".......####.",
            "......####..", "....#####...", "....#####...", "....#####...",
            "......####..", ".......####.", "##.....####.", "###....####.",
            "###....####.", "###....####.", ".###....###.", "..########..",
            "..########..", "....####...."};
        for(int y=0;y<glyph.length;y++)for(int x=0;x<12;x++)
            if(glyph[y].charAt(x)=='#')gray[(145+y)*400+119+x]=0;
        if(leftArm)for(int x=96;x<=116;x++)gray[(140+(x-96)/4+shift)*400+x]=0;
        if(rightArm)for(int x=133;x<=154;x++)gray[(145-(x-133)/4+shift)*400+x]=0;
        var notes=new ArrayList<ScoreNoteEvent>();
        for(float x:new float[]{.25f,.3125f,.375f})
            notes.add(new ScoreNoteEvent(0,x,0,0,1,.4f,false,0,0,2,1));
        return TripletRhythmDetector.apply(notes,List.of(new MeasureRegion(0,1,.2f,.6f)),gray,400,240);
    }
    @Test public void slopedArmsMayExtendAboveTheNumeral() {
        var notes=read(true,true,0);
        for(var note:notes){assertEquals(3,note.tupletDivisor());assertEquals(2.0/3,ScoreNoteTiming.writtenDurationBeats(note),.0001);}
    }
    @Test public void missingArmDoesNotTurnFingeringIntoTriplets() {
        for(var note:read(true,false,0))assertEquals(1,note.tupletDivisor());
        for(var note:read(false,true,0))assertEquals(1,note.tupletDivisor());
    }
    @Test public void distantLinesDoNotSupplyBrackets() {
        for(var note:read(true,true,-15))assertEquals(1,note.tupletDivisor());
    }
}
