// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic staff with two notes; no raw raster is available. */
public final class SemanticOnlyBoundaryTieTest {
    @Test public void unavailableRawPixelsCannotEstablishBoundaryTieEvidence() throws Exception {
        var arc=OmrScoreInterpreter.class.getDeclaredMethod("hasContinuousTieArc",byte[].class,
                byte[].class,int.class,int.class,int.class,int.class,float.class,float.class);
        arc.setAccessible(true);
        for(byte[] gray:new byte[][]{null,new byte[1]})
            assertEquals(false,arc.invoke(null,new byte[400*240],gray,400,240,100,140,120f,10f));
    }
    @Test public void semanticOnlyNotesDoNotRequireRawBoundaryArcEvidence() {
        int width=400,height=240; byte[] labels=new byte[width*height];
        for(int y:new int[]{80,90,100,110,120})
            for(int x=20;x<380;x++) labels[y*width+x]=OmrMeasurePostProcessor.STAFF;
        for(int center:new int[]{100,300})
            for(int y=117;y<=123;y++) for(int x=center-4;x<=center+4;x++)
                labels[y*width+x]=OmrMeasurePostProcessor.NOTEHEAD;
        var notes=OmrScoreInterpreter.extract(labels,width,height,
                List.of(new MeasureRegion(.05f,.95f,.20f,.62f)));
        assertEquals(2,notes.size()); assertFalse(notes.get(1).tiedFromPrevious());
    }
}
