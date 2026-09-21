// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic engraving, independent of commercial scans and model predictions. */
public class IndependentPitchAuditTest {
    private Object construct(String name,Object... args)throws Exception {
        var type=Class.forName(OmrScoreInterpreter.class.getName()+"$"+name);
        var c=type.getDeclaredConstructors()[0];c.setAccessible(true);return c.newInstance(args);
    }
    private Object call(String name,Object... args)throws Exception {
        for(var m:OmrScoreInterpreter.class.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(null,args);}
        throw new IllegalArgumentException(name);
    }
    private Object head(int x,int y)throws Exception{return construct("Component",200,x-10,x+10,y-7,y+7,(float)x,(float)y);}
    private static class Page {
        final int w=400,h=280;final byte[] labels=new byte[w*h],gray=new byte[w*h];
        Page(){Arrays.fill(gray,(byte)255);}
        void rect(int x,int y,int width,int height,byte label){for(int yy=y;yy<y+height;yy++)for(int xx=x;xx<x+width;xx++){gray[yy*w+xx]=0;labels[yy*w+xx]=label;}}
        void rules(int top,int gap){for(int i=0;i<5;i++)rect(20,top+i*gap,360,1,(byte)4);}
        void oval(int x,int y,boolean hollow){for(int yy=y-8;yy<=y+8;yy++)for(int xx=x-10;xx<=x+10;xx++){double d=Math.pow((xx-x)/10.,2)+Math.pow((yy-y)/8.,2);if(d<=1){labels[yy*w+xx]=2;gray[yy*w+xx]=(byte)(hollow&&d<.4?255:0);}}}
    }
    @Test public void beamBelowStaffCannotMoveTheFiveRuleReference()throws Exception {
        Page p=new Page();p.rules(80,16);p.rect(20,156,360,9,(byte)5);
        var staff=new RawStaffLineDetector.StaffLines(new int[]{96,112,128,144,160},16);
        assertNull(call("printedStaffPitch",p.gray,p.w,p.h,staff));
        assertNotNull(call("printedStaffPitch",p.gray,p.w,p.h,new RawStaffLineDetector.StaffLines(new int[]{80,96,112,128,144},16)));
    }
    @Test public void localPrintedSpacingRestoresLedgerPitchOnContractedSemanticStaff()throws Exception {
        Page p=new Page();p.rules(80,16);
        var staff=construct("Staff",84f,144f,15f);
        float[] pitch=(float[])call("localStaffPitch",p.labels,p.gray,p.w,p.h,staff,head(180,56));
        assertEquals(16,pitch[1],.01);assertEquals(11,Math.round((pitch[0]-56)/(pitch[1]*.5f)));
    }
    @Test public void isolatedBeamEdgeCannotRecalibrateFadedStaff()throws Exception {
        Page p=new Page();p.rect(20,149,360,2,(byte)4);
        float[] pitch=(float[])call("localStaffPitch",p.labels,p.gray,p.w,p.h,construct("Staff",80f,144f,16f),head(180,56));
        assertArrayEquals(new float[]{144,16},pitch,0);
    }
    @Test public void fourFadedRulesCanCorrectSkewWithoutUsingASingleBeamEdge()throws Exception {
        Page p=new Page();p.rules(80,16);
        for(int i=0;i<p.gray.length;i++)if(p.gray[i]==0)p.gray[i]=(byte)195;
        float[] pitch=(float[])call("localStaffPitch",p.labels,p.gray,p.w,p.h,construct("Staff",84f,148f,16f),head(180,56));
        assertEquals(144,pitch[0],.01);assertEquals(16,pitch[1],.01);
    }
    private boolean bass(boolean upper,boolean lower,boolean tail)throws Exception {
        Page p=new Page();p.rules(80,16);
        p.rect(100,83,20,22,(byte)3);
        if(upper)p.rect(126,86,5,5,(byte)3);
        if(lower)p.rect(126,102,5,5,(byte)5);
        if(tail)p.rect(100,117,5,14,(byte)5);
        var body=construct("Component",250,100,120,83,105,110f,94f);
        return (boolean)call("rawBassClef",body,p.gray,p.w,p.h,construct("Staff",80f,144f,16f));
    }
    @Test public void bassClefSurvivesTailAndDotInAnotherSemanticClass()throws Exception{assertTrue(bass(true,true,true));}
    @Test public void bassRecoveryRequiresBothDotsAndTheDescendingTail()throws Exception{assertFalse(bass(true,false,true));assertFalse(bass(true,true,false));}
    private boolean flat(boolean curved)throws Exception {
        Page p=new Page();int area=0;long sx=0,sy=0;
        for(int y=0;y<30;y++)for(int x=0;x<14;x++) {
            boolean ink=curved ? x>=Math.max(0,(y-9)/2)&&x<=Math.max(0,(y-9)/2)+2 : x<3||(y>=15&&y<28&&x>=9)||(y>=15&&y<18)||(y>=27&&y<30);
            if(ink){p.rect(100+x,80+y,1,1,(byte)5);area++;sx+=100+x;sy+=80+y;}
        }
        var c=construct("Component",area,100,113,80,109,sx/(float)area,sy/(float)area);
        return (boolean)call("isFlatGlyph",p.labels,p.w,p.h,construct("AccidentalCandidate",c,(byte)5),16f);
    }
    @Test public void slurTailIsNotAFlatButARealSpineAndBowlAre()throws Exception {assertFalse(flat(true));assertTrue(flat(false));}
    @Test public void shortBowlStrokeCannotCountAsAnotherKeySignatureSpine()throws Exception {
        Page p=new Page();p.rules(80,16);
        p.rect(100,75,3,32,(byte)3);p.rect(111,91,2,18,(byte)3);
        p.rect(125,51,3,32,(byte)3);
        assertEquals(2,call("countFlatSpines",p.labels,p.gray,p.w,p.h,95f,140f,construct("Staff",80f,144f,16f),false));
    }
    @Test public void mixedUnisonAndHeldThirdRemainThreeVoicesWithoutPlayingTheirDots() {
        Page p=new Page();p.rules(80,16);
        p.rect(190,95,2,50,(byte)1);p.rect(194,127,2,65,(byte)1);p.rect(190,95,60,4,(byte)5);
        p.oval(180,144,false);p.oval(204,128,true);p.oval(204,144,true);
        p.rect(188,141,9,5,(byte)2);
        p.rect(224,118,5,5,(byte)5);p.rect(224,134,5,5,(byte)2);
        var notes=OmrScoreInterpreter.extract(p.labels,p.gray,p.w,p.h,List.of(new MeasureRegion(.05f,.95f,.2f,.8f)));
        assertEquals(3,notes.size());assertEquals(List.of(0,0,2),notes.stream().map(ScoreNoteEvent::staffStep).sorted().toList());
        assertEquals(2,notes.stream().filter(n->n.unbeamedDurationBeats()==2).count());
        assertTrue(notes.stream().filter(n->n.beamCount()>0).allMatch(n->n.augmentationDots()==0));
    }

    @Test public void separatedMeterStrokesCannotIncreaseFlatCount()throws Exception {
        Page p=new Page();p.rules(80,16);
        p.rect(100,75,3,32,(byte)3);p.rect(117,51,3,32,(byte)3);
        p.rect(165,80,5,56,(byte)5);
        assertEquals(2,call("countFlatSpines",p.labels,p.gray,p.w,p.h,95f,190f,construct("Staff",80f,144f,16f),false));
    }
    @Test public void wholeLowerMeterDigitIsRejectedBeforeSplittingItsBowls()throws Exception {
        Page p=new Page();p.rules(80,16);p.rect(100,81,4,28,(byte)5);
        var staff=construct("Staff",80f,144f,16f);
        var clef=construct("Component",500,35,65,50,170,50f,110f);
        var digit=construct("Component",300,96,119,112,143,108f,128f);
        p.rect(96,112,24,32,(byte)2);
        assertTrue((boolean)call("isHeaderMeterDigit",p.labels,p.gray,p.w,p.h,digit,List.of(staff),List.of(clef)));
        p.rect(100,88,12,9,(byte)2);
        assertFalse((boolean)call("isHeaderMeterDigit",p.labels,p.gray,p.w,p.h,digit,List.of(staff),List.of(clef)));
    }
    @Test public void smallClefCurlIsRejectedOnlyWithinTheClefExtent()throws Exception {
        var staff=construct("Staff",80f,144f,16f);
        var clef=construct("Component",500,35,75,50,174,55f,115f);
        var curl=construct("Component",30,76,84,155,161,80f,158f);
        assertTrue((boolean)call("isTrebleCurlFragment",curl,List.of(staff),List.of(clef)));
        var note=construct("Component",30,100,108,155,161,104f,158f);
        assertFalse((boolean)call("isTrebleCurlFragment",note,List.of(staff),List.of(clef)));
    }
    @Test public void flatSlurFragmentIsRejectedButShortGraceStemProtectsNote()throws Exception {
        Page p=new Page();var fragment=construct("Component",40,100,111,120,125,105f,122f);
        p.rect(100,120,12,6,(byte)2);
        assertTrue((boolean)call("flatStemlessFragment",p.gray,p.w,p.h,fragment,16f));
        p.rect(111,94,1,31,(byte)1);
        assertFalse((boolean)call("flatStemlessFragment",p.gray,p.w,p.h,fragment,16f));
    }
    @Test public void regionalPrintedRulesRecoverSpacingOnASkewedScan()throws Exception {
        Page p=new Page();
        for(int x=20;x<380;x++)for(int line=0;line<5;line++)p.rect(x,80+line*16+x/150,1,2,(byte)4);
        float[] pitch=(float[])call("regionalStaffPitch",p.gray,p.w,p.h,construct("Staff",96f,148f,13f));
        assertNotNull(pitch);assertEquals(16,pitch[1],.2);assertEquals(148,pitch[0],3);
    }
    @Test public void localFlatRepeatingAKeyPitchDoesNotExtendTheSignature()throws Exception {
        Page p=new Page();p.rules(80,16);
        p.rect(100,75,3,32,(byte)3);p.rect(120,51,3,32,(byte)3);
        p.rect(144,107,3,32,(byte)3);
        assertEquals(2,call("countFlatSpines",p.labels,p.gray,p.w,p.h,95f,160f,construct("Staff",80f,144f,16f),false));
    }
    @Test public void sevenSplitFlatSpinesKeepTheirPrintedOrder()throws Exception {
        Page p=new Page();p.rules(80,16);
        int[] tops={75,51,83,59,91,67,99};
        for(int i=0;i<tops.length;i++)p.rect(100+i*18,tops[i],3,32,(byte)3);
        assertEquals(7,call("countFlatSpines",p.labels,p.gray,p.w,p.h,95f,220f,construct("Staff",80f,144f,16f),false));
    }
}
