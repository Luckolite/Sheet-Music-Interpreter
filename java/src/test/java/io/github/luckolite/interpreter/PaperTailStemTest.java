// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original beam touching a staff rule, with separate dark artwork beyond its stem. */
public class PaperTailStemTest {
    private static final int W=200,H=300;
    private final byte[] gray=new byte[W*H],labels=new byte[W*H];
    private boolean up;
    private int y(int value){return up?H-1-value:value;}
    private void draw(int left,int right,int top,int bottom,int label) {
        for(int yy=top;yy<=bottom;yy++)for(int x=left;x<=right;x++) {
            gray[y(yy)*W+x]=0;labels[y(yy)*W+x]=(byte)label;
        }
    }
    private void setup(boolean up,int beamThickness) {
        this.up=up;Arrays.fill(gray,(byte)255);
        draw(79,81,50,130,1);
        // Beam semantics can merge into the staff-rule class; the printed beam remains thick.
        draw(65,130,131-beamThickness,130,4);
        draw(65,130,133,240,5);
    }
    private int[] trim(int end)throws Exception {
        var type=Class.forName(OmrScoreInterpreter.class.getName()+"$Component");
        var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
        var head=constructor.newInstance(180,70,90,y(50)-6,y(50)+6,80f,(float)y(50));
        var method=OmrScoreInterpreter.class.getDeclaredMethod("stemBeforePaperTail",
                byte[].class,byte[].class,int.class,int.class,type,float.class,int[].class);
        method.setAccessible(true);
        return (int[])method.invoke(null,labels,gray,W,H,head,16f,new int[]{80,y(end),up?-1:1});
    }
    @Test public void downStemStopsAtBeamBeforeArtwork()throws Exception {
        setup(false,8);assertArrayEquals(new int[]{80,130,1},trim(240));
    }
    @Test public void upStemStopsAtBeamBeforeArtwork()throws Exception {
        setup(true,8);assertArrayEquals(new int[]{80,y(130),-1},trim(240));
    }
    @Test public void semanticStemBeyondGapKeepsTheFullTrace()throws Exception {
        setup(false,8);draw(79,81,145,160,1);assertEquals(240,trim(240)[1]);
    }
    @Test public void aThinStaffRuleCannotEndTheTrace()throws Exception {
        setup(false,2);assertEquals(240,trim(240)[1]);
    }
    @Test public void missingSemanticStemDoesNotTrustArtworkEdge()throws Exception {
        setup(false,8);Arrays.fill(labels,(byte)0);assertEquals(240,trim(240)[1]);
    }
    @Test public void shorterTraceKeepsBroadInkThatCouldBeAnotherBeam()throws Exception {
        setup(false,8);assertEquals(155,trim(155)[1]);
    }
    private void shortText(boolean up) {
        this.up=up;Arrays.fill(gray,(byte)255);Arrays.fill(labels,(byte)0);
        draw(79,81,50,96,1);draw(80,130,89,96,4);draw(79,90,98,125,5);
    }
    @Test public void oneWhiteRowSeparatesLetteringAboveBeam()throws Exception {
        shortText(true);assertArrayEquals(new int[]{80,y(96),-1},trim(125));
    }
    @Test public void oneWhiteRowSeparatesLetteringBelowBeam()throws Exception {
        shortText(false);assertArrayEquals(new int[]{80,96,1},trim(125));
    }
    @Test public void grayStemInterruptionDoesNotProveDetachedText()throws Exception {
        shortText(false);for(int x=79;x<=81;x++)gray[97*W+x]=(byte)190;
        assertEquals(125,trim(125)[1]);
    }
    @Test public void continuingSemanticStemKeepsShortTrace()throws Exception {
        shortText(false);draw(79,81,104,120,1);assertEquals(125,trim(125)[1]);
    }
    @Test public void detachedOuterBeamKeepsShortTrace()throws Exception {
        shortText(false);draw(80,130,104,112,5);assertEquals(125,trim(125)[1]);
    }
    @Test public void inspectionPreservesTheSourcePixels()throws Exception {
        setup(false,8);byte[] before=gray.clone(),mask=labels.clone();trim(240);
        assertArrayEquals(before,gray);assertArrayEquals(mask,labels);
    }
}
