// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original zigzag, oval, and stem geometry; no score-derived pixels. */
public class ZigzagOrnamentHeadTest {
    static final int W=500,H=260;
    byte[] gray=new byte[W*H],labels=new byte[W*H];
    static final List<MeasureRegion> M=List.of(new MeasureRegion(.04f,.96f,.15f,.9f));
    ZigzagOrnamentHeadTest page(int mode,boolean owner) {
        Arrays.fill(gray,(byte)255);
        for(int y=120;y<=184;y+=16)for(int x=20;x<480;x++)ink(x,y,4);
        if(owner){oval(244,152,11,7,true,2);for(int y=100;y<=152;y++)ink(255,y,1);}
        if(mode==1)oval(239,85,15,5,true,5);
        else if(mode==2){oval(229,85,7,5,true,5);oval(246,85,7,5,true,5);}
        else for(int x=224;x<=253;x++) {
            float cy=x<=234?82+(x-224)*.6f:x<=243?88-(x-234)*6f/9:82+(x-243)*.6f;
            if(mode==3&&x<238)continue;
            for(int y=Math.round(cy)-2;y<=Math.round(cy)+2;y++)ink(x,y,mode==4?2:5);
        }
        oval(246,85,8,5,false,2);
        return this;
    }
    void ink(int x,int y,int label){gray[y*W+x]=0;labels[y*W+x]=(byte)label;}
    void oval(int cx,int cy,int rx,int ry,boolean raw,int label){
        for(int y=cy-ry;y<=cy+ry;y++)for(int x=cx-rx;x<=cx+rx;x++)
            if(Math.pow((x-cx)/(double)rx,2)+Math.pow((y-cy)/(double)ry,2)<=1){labels[y*W+x]=(byte)label;if(raw)gray[y*W+x]=0;}
    }
    boolean demoted(){byte[] n=OmrScoreInterpreter.normalizeHeaderSymbols(labels,gray,W,H,M);return n[85*W+246]!=2;}
    @Test public void completeZigzagIslandIsDemoted(){assertTrue(page(0,true).demoted());}
    @Test public void oneOvalCannotSupplyRepeatedZigzag(){assertFalse(page(1,true).demoted());}
    @Test public void twoRoundHeadsCannotSupplyRepeatedZigzag(){assertFalse(page(2,true).demoted());}
    @Test public void incompleteZigzagIsPreserved(){assertFalse(page(3,true).demoted());}
    @Test public void neighboringSymbolLabelsAreRequired(){assertFalse(page(4,true).demoted());}
    @Test public void largerNoteBelowIsRequired(){assertFalse(page(0,false).demoted());}
    @Test public void realStemmedGraceHeadIsPreserved(){page(0,true);for(int y=46;y<=85;y++)ink(254,y,1);assertFalse(demoted());}
    @Test public void maskAndPixelsArePreserved(){page(0,true);var g=gray.clone();var l=labels.clone();demoted();assertArrayEquals(g,gray);assertArrayEquals(l,labels);}
}
