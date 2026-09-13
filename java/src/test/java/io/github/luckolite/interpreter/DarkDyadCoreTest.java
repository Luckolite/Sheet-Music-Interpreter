// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original filled ovals with a lighter bridge joining their model masks. */
public class DarkDyadCoreTest {
    static final int W=360,H=210;
    final byte[] labels=new byte[W*H],gray=new byte[W*H];
    public DarkDyadCoreTest(){
        Arrays.fill(gray,(byte)255);
        for(int y=80;y<=144;y+=16)for(int x=20;x<340;x++){labels[y*W+x]=4;gray[y*W+x]=0;}
        for(int y=90;y<=122;y++)for(int x=190;x<=210;x++){labels[y*W+x]=2;gray[y*W+x]=(byte)160;}
        for(int y=56;y<90;y++){labels[y*W+210]=1;gray[y*W+210]=0;}
    }
    void oval(int cy,int radiusY,int value){
        for(int y=cy-radiusY;y<=cy+radiusY;y++)for(int x=190;x<=210;x++)
            if(Math.pow((x-200)/10d,2)+Math.pow((y-cy)/(double)radiusY,2)<=1)gray[y*W+x]=(byte)value;
    }
    List<ScoreNoteEvent> notes(){return OmrScoreInterpreter.extract(labels,gray,W,H,List.of(new MeasureRegion(100f/W,335f/W,50f/H,180f/H)));}
    @Test public void darkCoresRevealTwoHeadsAcrossPaleBridge(){
        oval(98,8,0);oval(114,8,0);var n=notes();assertEquals(2,n.size());
        var ys=n.stream().map(v->v.pageY()*H).sorted().toList();assertEquals(98f,ys.get(0),1.5f);assertEquals(114f,ys.get(1),1.5f);
    }
    @Test public void moderatelyDarkCoresAlsoRecover(){oval(98,8,100);oval(114,8,100);assertEquals(2,notes().size());}
    @Test public void oneOvalDoesNotSplit(){oval(106,16,0);assertEquals(1,notes().size());}
    @Test public void solidBlockDoesNotSplit(){for(int y=90;y<=122;y++)for(int x=190;x<=210;x++)gray[y*W+x]=0;assertEquals(1,notes().size());}
    @Test public void isolatedDarkBandsDoNotSupplyTwoHeads(){
        for(int y:new int[]{94,118})for(int x=190;x<=210;x++)gray[y*W+x]=0;
        assertEquals(1,notes().size());
    }
    @Test public void paleMassWithoutDarkCoresDoesNotSplit(){assertEquals(1,notes().size());}
    @Test public void sourceArraysArePreserved(){oval(98,8,0);oval(114,8,0);var l=labels.clone();var g=gray.clone();notes();assertArrayEquals(l,labels);assertArrayEquals(g,gray);}
}
