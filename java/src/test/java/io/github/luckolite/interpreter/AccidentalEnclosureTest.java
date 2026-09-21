// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
public class AccidentalEnclosureTest {
    private byte[] oval(boolean complete) {
        byte[] g=new byte[120*140];Arrays.fill(g,(byte)255);
        for(int y=40;y<100;y++)for(int x=30;x<90;x++) {
            double a=(x-60)/23d,b=(y-70)/24d;
            if(Math.abs(a*a+b*b-1)<.13&&(complete||x<60))g[y*120+x]=0;
        }
        return g;
    }
    @Test public void completeEnclosureContainsItsBottomFragmentButNotNextChord() {
        float[] ring=AccidentalEnclosure.find(oval(true),120,140,54,51,66,84,20);
        assertNotNull(ring);
        assertTrue(AccidentalEnclosure.contains(ring,55,88,65,93));
        assertFalse(AccidentalEnclosure.contains(ring,88,70,110,84));
    }
    @Test public void singleCurveDoesNotEstablishEnclosure() {
        assertNull(AccidentalEnclosure.find(oval(false),120,140,54,51,66,84,20));
    }
    @Test public void staffLinesAloneAreNotAnEnclosure() {
        byte[] g=new byte[120*140];Arrays.fill(g,(byte)255);
        for(int y=30;y<=110;y+=20)for(int x=0;x<120;x++)g[y*120+x]=0;
        assertNull(AccidentalEnclosure.find(g,120,140,54,51,66,84,20));
    }
}
