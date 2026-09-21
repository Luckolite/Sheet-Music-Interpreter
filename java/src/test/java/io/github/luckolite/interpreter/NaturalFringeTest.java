// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
/** Original offset stems and small scan-fringe protrusions. */
public class NaturalFringeTest {
    private final int w=100,h=100;private final byte[] labels=new byte[w*h];
    private void rect(int x,int y,int ww,int hh){for(int yy=y;yy<y+hh;yy++)for(int xx=x;xx<x+ww;xx++)labels[yy*w+xx]=3;}
    private boolean read()throws Exception {
        var component=Class.forName(OmrScoreInterpreter.class.getName()+"$Component").getDeclaredConstructors()[0];component.setAccessible(true);
        int area=0,x0=w,x1=0,y0=h,y1=0;long sx=0,sy=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(labels[y*w+x]!=0){area++;x0=Math.min(x0,x);x1=Math.max(x1,x);y0=Math.min(y0,y);y1=Math.max(y1,y);sx+=x;sy+=y;}
        Object c=component.newInstance(area,x0,x1,y0,y1,sx/(float)area,sy/(float)area);
        var candidate=Class.forName(OmrScoreInterpreter.class.getName()+"$AccidentalCandidate").getDeclaredConstructors()[0];candidate.setAccessible(true);Object a=candidate.newInstance(c,(byte)3);
        for(var m:OmrScoreInterpreter.class.getDeclaredMethods())if(m.getName().equals("isNaturalGlyph")){m.setAccessible(true);return(boolean)m.invoke(null,labels,w,h,a,16f);}throw new AssertionError();
    }
    @Test public void strongNaturalEndpointsSurviveScanFringe()throws Exception {
        rect(31,25,3,32);rect(44,36,3,42);rect(28,36,20,3);rect(28,54,20,3);
        assertTrue(read());
    }
    @Test public void offsetSharpStillHasExtensionsBeyondBothBridges()throws Exception {
        rect(31,25,3,41);rect(44,36,3,42);rect(28,45,21,3);rect(28,55,21,3);
        assertFalse(read());
    }
    @Test public void fullHeightSharpIsNotNatural()throws Exception {
        rect(31,25,3,53);rect(44,25,3,53);rect(28,40,21,3);rect(28,57,21,3);
        assertFalse(read());
    }
}
