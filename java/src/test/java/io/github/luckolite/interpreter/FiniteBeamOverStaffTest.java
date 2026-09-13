// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
/** Original finite beam bodies over continuing staff rules; no score pixels. */
public class FiniteBeamOverStaffTest {
    static final int W=360,H=200;
    final byte[] gray=new byte[W*H],labels=new byte[W*H];
    void rect(int left,int right,int top,int bottom,int label){for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){gray[y*W+x]=0;labels[y*W+x]=(byte)label;}}
    void setup(){Arrays.fill(gray,(byte)255);for(int y=84;y<=148;y+=16)rect(0,W-1,y,y+1,4);}
    int count()throws Exception {
        var st=Class.forName(OmrScoreInterpreter.class.getName()+"$Staff");var sc=st.getDeclaredConstructor(float.class,float.class,float.class);sc.setAccessible(true);
        var method=OmrScoreInterpreter.class.getDeclaredMethod("thickNonHeadBands",byte[].class,byte[].class,int.class,int.class,int.class,int.class,int.class,st);method.setAccessible(true);
        return (int)method.invoke(null,gray,labels,W,H,170,76,105,sc.newInstance(84f,148f,16f));
    }
    @Test public void beamOverStaffAndSecondaryHookAreTwoBands()throws Exception {setup();rect(80,250,79,85,5);rect(160,184,91,97,5);assertEquals(2,count());}
    @Test public void ordinaryStaffDoesNotBecomeABeam()throws Exception {setup();assertEquals(0,count());}
    @Test public void oneBeamAwayFromStaffRemainsOne()throws Exception {setup();rect(80,250,91,97,5);assertEquals(1,count());}
    @Test public void uniformlyThickStaffIsStillNotABeam()throws Exception {setup();rect(0,W-1,79,85,4);assertEquals(0,count());}
    @Test public void softBeamEndpointRetainsBothBands()throws Exception {
        setup();rect(80,250,79,85,5);rect(79,79,82,84,5);rect(160,184,91,97,5);assertEquals(2,count());
    }
    @Test public void onlyOneVisibleEndCannotOverrideStaffSuppression()throws Exception {
        setup();rect(0,250,79,85,4);assertEquals(0,count());
    }
    @Test public void whitePaperBeyondTheBandDoesNotProveAContinuingRule()throws Exception {
        setup();for(int yy=84;yy<=85;yy++)for(int xx=0;xx<W;xx++)gray[yy*W+xx]=(byte)255;
        rect(80,250,79,85,5);assertEquals(0,count());
    }
    @Test public void aLongTaperDoesNotProveAFiniteBeam()throws Exception {
        setup();rect(80,250,79,85,5);rect(65,79,82,84,5);assertEquals(0,count());
    }
    @Test public void aThinSlurOverTheRuleDoesNotBecomeABeam()throws Exception {
        setup();rect(80,250,82,84,5);assertEquals(0,count());
    }
    @Test public void sourcePixelsRemainUnchanged()throws Exception {
        setup();rect(80,250,79,85,5);rect(160,184,91,97,5);
        var g=gray.clone();var l=labels.clone();count();assertArrayEquals(g,gray);assertArrayEquals(l,labels);
    }
}
