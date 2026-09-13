// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original long tie geometry, with pale ends and a dark central returning curve. */
public class FadedDetachedTieTest {
    private boolean arc(int mode,boolean below)throws Exception {
        int w=260,h=160,left=40,right=180,first=52,last=177;float cy=80,gap=15;
        byte[] gray=new byte[w*h],labels=new byte[w*h];Arrays.fill(gray,(byte)255);
        for(int x=first;x<=last;x++) {
            float t=(x-first)/(float)(last-first);
            if(mode==3&&t>.55f)continue;
            float bend=mode==1?0:mode==2?10*t:10*4*t*(1-t);
            int y=Math.round(cy+(below?1:-1)*(8+bend));
            int shade=mode==4||t<.2f||t>.8f?195:0;
            for(int yy=y-1;yy<=y+1;yy++){gray[yy*w+x]=(byte)shade;labels[yy*w+x]=5;}
        }
        var method=OmrScoreInterpreter.class.getDeclaredMethod("hasPrintedTieArc",byte[].class,byte[].class,int.class,int.class,int.class,int.class,float.class,float.class);
        method.setAccessible(true);return (boolean)method.invoke(null,labels,gray,w,h,left,right,cy,gap);
    }
    @Test public void darkCoreAndPaleDetachedEndsJoinBelow()throws Exception {assertTrue(arc(0,true));}
    @Test public void darkCoreAndPaleDetachedEndsJoinAbove()throws Exception {assertTrue(arc(0,false));}
    @Test public void fadedStraightStrokeDoesNotJoin()throws Exception {assertFalse(arc(1,true));}
    @Test public void fadedSlopingStrokeDoesNotJoin()throws Exception {assertFalse(arc(2,true));}
    @Test public void halfReturningCurveDoesNotJoin()throws Exception {assertFalse(arc(3,true));}
    @Test public void entirelyPaleCurveLacksDarkCore()throws Exception {assertFalse(arc(4,true));}
}
