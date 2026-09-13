// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original pale rules test local scale recovery against an inaccurate seed. */
public class FadedLocalRuleTest {
    static final int W=420,H=260;
    final byte[] labels=new byte[W*H],gray=new byte[W*H];
    public FadedLocalRuleTest(){
        Arrays.fill(gray,(byte)255);
        for(int line=0;line<5;line++)for(int x=10;x<W-10;x++){
            labels[(100+line*18)*W+x]=4;gray[(104+line*17)*W+x]=(byte)220;
        }
    }
    private Object make(String name,Object...args)throws Exception{
        var c=Class.forName(OmrScoreInterpreter.class.getName()+"$"+name).getDeclaredConstructors()[0];c.setAccessible(true);return c.newInstance(args);
    }
    float spacing()throws Exception{
        Object staff=make("Staff",100f,172f,18f),head=make("Component",120,193,207,73,85,200f,79f);
        for(var m:OmrScoreInterpreter.class.getDeclaredMethods())if(m.getName().equals("localStaffPitch")){
            m.setAccessible(true);return ((float[])m.invoke(null,labels,gray,W,H,staff,head))[1];
        }
        throw new AssertionError("Missing local pitch method");
    }
    @Test public void fivePaleRulesCorrectLocalSpacing()throws Exception{assertEquals(17f,spacing(),.15f);}
    @Test public void somewhatDarkerPaleRulesAlsoRecover()throws Exception{
        for(int i=0;i<gray.length;i++)if((gray[i]&255)==220)gray[i]=(byte)210;
        assertEquals(17f,spacing(),.15f);
    }
    @Test public void missingRuleDoesNotRecalibrate()throws Exception{
        for(int x=0;x<W;x++)gray[138*W+x]=(byte)255;assertEquals(18f,spacing(),.15f);
    }
    @Test public void oneSidedRulesDoNotRecalibrate()throws Exception{
        for(int y=0;y<H;y++)Arrays.fill(gray,y*W,y*W+200,(byte)255);assertEquals(18f,spacing(),.15f);
    }
    @Test public void insufficientContrastDoesNotRecalibrate()throws Exception{
        for(int i=0;i<gray.length;i++)if((gray[i]&255)==255)gray[i]=(byte)225;
        assertEquals(18f,spacing(),.15f);
    }
    @Test public void missingSemanticRuleDoesNotRecalibrate()throws Exception{
        for(int x=0;x<W;x++)labels[100*W+x]=0;assertEquals(18f,spacing(),.15f);
    }
    @Test public void sourceArraysRemainUnchanged()throws Exception{
        var l=labels.clone();var g=gray.clone();spacing();assertArrayEquals(l,labels);assertArrayEquals(g,gray);
    }
    @Test public void broadShadeBandsDoNotRecalibrate()throws Exception{
        for(int line=0;line<5;line++)for(int y=99+line*17;y<=109+line*17;y++)
            for(int x=10;x<W-10;x++)gray[y*W+x]=(byte)220;
        assertEquals(18f,spacing(),.15f);
    }
    @Test public void generatedPaleTextureDoesNotRecalibrate()throws Exception{
        Random random=new Random(61309);
        for(int y=80;y<200;y++)for(int x=10;x<W-10;x++)gray[y*W+x]=(byte)(210+random.nextInt(46));
        assertEquals(18f,spacing(),.15f);
    }
}
