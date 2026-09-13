// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original three made from bars, with optional incorrect notehead segmentation. */
public class ThreeRestCountTest {
    private static final int W=400,H=240;
    private final byte[] labels=new byte[W*H],gray=new byte[W*H];
    private final MeasureRegion region=new MeasureRegion(.2f,.8f,.35f,.85f);
    private void box(int left,int top,int right,int bottom,int label) {
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++) {
            labels[y*W+x]=(byte)label;gray[y*W+x]=0;
        }
    }
    private void page(int digitLabel,boolean bar) {
        Arrays.fill(gray,(byte)255);
        for(int y=100;y<=164;y+=16)box(40,y,360,y,4);
        if(bar) {box(110,128,290,139,1);box(110,122,112,145,1);box(288,122,290,145,1);}
        box(207,61,211,92,digitLabel);
        for(int y:new int[]{61,75,89})box(190,y,211,y+3,digitLabel);
    }
    private List<MeasureNumberReconciler.NumberToken> counts() {
        return MultiMeasureRestDetector.detect(labels,gray,W,H,List.of(region),List.of());
    }
    @Test public void recognizesThreeAboveHeavyBar() {
        page(5,true);assertEquals(3,counts().get(0).value());
    }
    @Test public void mislabeledThreeExpandsThreeSilentMeasures() {
        page(2,true);var counts=counts();assertEquals(1,counts.size());assertEquals(3,counts.get(0).value());
        var measures=MeasureNumberReconciler.reconcile(List.of(region),List.of(),counts);
        assertEquals(3,measures.size());
        assertTrue(OmrScoreInterpreter.analyze(labels,gray,W,H,measures).notes().isEmpty());
    }
    @Test public void ordinaryWrittenNoteStillPreventsRest() {
        page(2,true);box(145,102,157,111,2);assertTrue(counts().isEmpty());
    }
    @Test public void threeWithoutHeavyBarIsNotRestCount() {
        page(5,false);assertTrue(counts().isEmpty());
    }
    @Test public void callerPixelsRemainUnchanged() {
        page(2,true);var a=labels.clone();var b=gray.clone();counts();
        assertArrayEquals(a,labels);assertArrayEquals(b,gray);
    }
}
