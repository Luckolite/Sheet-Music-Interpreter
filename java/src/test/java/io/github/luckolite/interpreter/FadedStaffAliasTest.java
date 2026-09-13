// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original page geometry: pale complete rules surrounding a compressed semantic group. */
public class FadedStaffAliasTest {
    static final int W=800,H=1100;
    private byte[][] page(int shade,boolean complete,boolean shadow,boolean shortRules) {
        byte[][] a={new byte[W*H],new byte[W*H]};Arrays.fill(a[1],(byte)255);
        for(int top:new int[]{100,300,600,900}) {
            int semanticGap=top==600?11:15;
            for(int line=0;line<5;line++)for(int x=40;x<760;x++) {
                a[0][(top+line*semanticGap)*W+x]=OmrMeasurePostProcessor.STAFF;
                if(top!=600)a[1][(top+line*15)*W+x]=0;
            }
        }
        if(shadow)for(int y=595;y<=665;y++)for(int x=40;x<760;x++)a[1][y*W+x]=(byte)190;
        for(int line=0;line<(complete?5:4);line++)for(int x=40;x<(shortRules?260:760);x++)
            a[1][(600+line*15)*W+x]=(byte)shade;
        return a;
    }
    private float[] geometry(byte[][] a)throws Exception {
        var m=OmrScoreInterpreter.class.getDeclaredMethod("findStaffs",byte[].class,byte[].class,int.class,int.class,List.class);
        m.setAccessible(true);
        List<MeasureRegion> regions=new ArrayList<>();for(int y:new int[]{100,300,600,900})regions.add(new MeasureRegion(.05f,.95f,(y-15f)/H,(y+80f)/H));
        for(Object s:(List<?>)m.invoke(null,a[0],a[1],W,H,regions)) {
            var top=s.getClass().getDeclaredField("top");top.setAccessible(true);
            if(top.getFloat(s)<580||top.getFloat(s)>620)continue;
            var gap=s.getClass().getDeclaredField("pitchGap");gap.setAccessible(true);
            var bottom=s.getClass().getDeclaredField("pitchBottom");bottom.setAccessible(true);
            return new float[]{bottom.getFloat(s),gap.getFloat(s)};
        }
        throw new AssertionError("Missing target staff");
    }
    @Test public void completePaleRulesReplaceCompressedAlias()throws Exception {
        assertArrayEquals(new float[]{660,15},geometry(page(195,true,false,false)),.1f);
    }
    @Test public void missingOuterRuleDoesNotChangeStaffScale()throws Exception {
        assertEquals(11,geometry(page(195,false,false,false))[1],.1f);
    }
    @Test public void broadShadowCannotProveFiveRules()throws Exception {
        assertEquals(11,geometry(page(195,true,true,false))[1],.1f);
    }
    @Test public void shortParallelStrokesDoNotChangeStaffScale()throws Exception {
        assertEquals(11,geometry(page(195,true,false,true))[1],.1f);
    }
    @Test public void nearlyWhiteRulesDoNotChangeStaffScale()throws Exception {
        assertEquals(11,geometry(page(240,true,false,false))[1],.1f);
    }
    @Test public void preservesInputMasksAndPixels()throws Exception {
        byte[][] a=page(195,true,false,false);byte[] labels=a[0].clone(),gray=a[1].clone();
        geometry(a);assertArrayEquals(labels,a[0]);assertArrayEquals(gray,a[1]);
    }
}
