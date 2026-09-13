// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic engraving, including a bold two with a raised lower serif. */
public class RestNumeralRecoveryTest {
    private static class Page {
        final int w=400,h=240;final byte[] gray=new byte[w*h],labels=new byte[w*h];
        final MeasureRegion region=new MeasureRegion(.2f,.8f,.35f,.85f);
        Page(boolean bar,boolean three) {
            Arrays.fill(gray,(byte)200);
            for(int line=0;line<5;line++)rect(40,100+line*16,320,1,4);
            if(bar){rect(110,128,180,12,1);rect(110,122,2,24,1);rect(288,122,2,24,1);}
            for(int y=0;y<24;y++)for(int x=0;x<15;x++) {
                boolean ink=y<4&&x>=2&&x<=13 || y>=4&&y<=13&&x>=10
                        || y>=14&&y<=19&&x>=10-(y-14)*2&&x<=14-(y-14)*2
                        || y>=20 || y>=17&&x>=11;
                if(three)ink=x>=11 || (y<4||y>=10&&y<=13||y>=20)&&x>=2;
                if(ink)rect(193+x,67+y,1,1,2);
            }
        }
        void rect(int x,int y,int width,int height,int label) {
            for(int yy=y;yy<y+height;yy++)for(int xx=x;xx<x+width;xx++) {
                gray[yy*w+xx]=0;labels[yy*w+xx]=(byte)label;
            }
        }
        List<MeasureNumberReconciler.NumberToken> detect(List<MeasureNumberReconciler.NumberToken> ocr) {
            return MultiMeasureRestDetector.detect(labels,gray,w,h,List.of(region),ocr);
        }
    }
    @Test public void boldTwoIsRecoveredEvenWhenItsInkWasLabeledAsANote() {
        Page p=new Page(true,false);var counts=p.detect(List.of());
        assertEquals(1,counts.size());assertEquals(2,counts.get(0).value());
        assertEquals(2,MeasureNumberReconciler.reconcile(List.of(p.region),List.of(),counts).size());
    }
    @Test public void anActualContinuousThreeSpineIsNotATwo() {
        Page p=new Page(true,true);var counts=p.detect(List.of());
        assertEquals(1,counts.size());assertEquals(3,counts.get(0).value());
    }
    @Test public void aCountNeedsTheHeavyRestBar() {
        Page p=new Page(false,false);assertTrue(p.detect(List.of()).isEmpty());
    }
    @Test public void realNoteInkElsewherePreventsExpansion() {
        Page p=new Page(true,false);p.rect(145,154,12,8,2);
        assertTrue(p.detect(List.of()).isEmpty());
    }
    @Test public void callerOcrKeepsPriorityAndTheSameBarIsNotExpandedTwice() {
        Page p=new Page(true,false);
        var count=new MeasureNumberReconciler.NumberToken(4,.48f,.28f,.52f,.38f);
        assertEquals(List.of(count),p.detect(List.of(count)));
    }
    @Test public void automaticCountRequiresAStaffBelowIt() {
        Page p=new Page(true,false);
        for(int i=0;i<p.labels.length;i++)if(p.labels[i]==4)p.labels[i]=0;
        assertTrue(p.detect(List.of()).isEmpty());
    }
    @Test public void aGlyphWithinTextIsNotAnIsolatedRestCount() {
        Page p=new Page(true,false);p.rect(211,70,3,18,5);
        assertTrue(p.detect(List.of()).isEmpty());
    }
}
