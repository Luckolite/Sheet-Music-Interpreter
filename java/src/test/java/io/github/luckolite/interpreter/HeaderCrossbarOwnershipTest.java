// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original fragmented header ink and independent small-note controls. */
public class HeaderCrossbarOwnershipTest {
    static final int W=600,H=280;
    static final List<MeasureRegion> M=List.of(new MeasureRegion(.02f,.98f,.1f,.9f));
    byte[] labels=new byte[W*H],gray=new byte[W*H];
    HeaderCrossbarOwnershipTest page(boolean clef,boolean connector,boolean following,int offset) {
        Arrays.fill(gray,(byte)255);
        for(int y=100;y<=164;y+=16)for(int x=12;x<588;x++)ink(x,y,4,0);
        if(clef)for(int y=68;y<=191;y++)for(int x=28;x<=54;x++)
            if(x<31||x>51||y<71||y>188)ink(x,y,3,0);
        if(connector)for(int y=80+offset;y<=120+offset;y++)for(int x=93;x<=94;x++)ink(x,y,5,220);
        for(int cy:new int[]{92+offset,108+offset})for(int y=cy-2;y<=cy+2;y++)for(int x=86;x<=102;x++)ink(x,y,x>=90&&x<=98?2:5,0);
        if(following){
            for(int y=125;y<=139;y++)for(int x=139;x<=161;x++)
                if(Math.pow((x-150)/11d,2)+Math.pow((y-132)/7d,2)<=1)ink(x,y,2,0);
            for(int y=84;y<=132;y++)ink(161,y,1,0);
        }
        return this;
    }
    void ink(int x,int y,int label,int shade){labels[y*W+x]=(byte)label;gray[y*W+x]=(byte)shade;}
    byte[] normalized(){return OmrScoreInterpreter.normalizeHeaderSymbols(labels,gray,W,H,M);}
    boolean removed(){var n=normalized();return n[92*W+94]!=2&&n[108*W+94]!=2;}
    @Test public void connectedSymbolInProvenHeaderIsDemoted(){assertTrue(page(true,true,true,0).removed());}
    @Test public void missingClefCannotEstablishOwnership(){assertFalse(page(false,true,true,0).removed());}
    @Test public void disconnectedBarsCannotEstablishOneGlyph(){assertFalse(page(true,false,true,0).removed());}
    @Test public void firstPlayedNoteIsRequired(){assertFalse(page(true,true,false,0).removed());}
    @Test public void wrongPitchSlotIsPreserved(){page(true,true,true,16);var n=normalized();assertEquals(2,n[108*W+94]);assertEquals(2,n[124*W+94]);}
    @Test public void stemmedSmallChordIsPreserved(){page(true,true,true,0);for(int y=44;y<=108;y++)ink(98,y,1,175);assertFalse(removed());}
    @Test public void shorterStackedHeaderIsNotAClef(){page(true,true,true,0);for(int y=0;y<H;y++)if(y<96||y>169)for(int x=28;x<=54;x++){labels[y*W+x]=0;gray[y*W+x]=(byte)255;}assertFalse(removed());}
    @Test public void followingNotePixelsAreUnchanged(){page(true,true,true,0);var n=normalized();for(int y=125;y<=139;y++)for(int x=139;x<=161;x++)assertEquals(labels[y*W+x],n[y*W+x]);}
    @Test public void sourceArraysArePreserved(){page(true,true,true,0);var l=labels.clone();var g=gray.clone();normalized();assertArrayEquals(l,labels);assertArrayEquals(g,gray);}
    @Test public void normalizationIsIdempotent(){page(true,true,true,0);var once=normalized();assertArrayEquals(once,OmrScoreInterpreter.normalizeHeaderSymbols(once,gray,W,H,M));}
}
