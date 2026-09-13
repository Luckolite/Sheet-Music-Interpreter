// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original quadratic scoop joins a note whose printed stem is faint. */
public class PaleJoinedEntranceTest {
    CurvedEntranceStrokeTest.Page page(int thickness,boolean allPale,boolean disconnected) {
        var p=new CurvedEntranceStrokeTest.Page(true,false);
        for(int y=100;y<150;y++)p.gray[y*CurvedEntranceStrokeTest.W+341]=(byte)175;
        for(int x=292;x<=320;x++) {
            double t=(x-292)/28.;int cy=(int)Math.round(180-24*t*t);
            if(disconnected&&x>=317)continue;
            for(int dy=-thickness;dy<=thickness;dy++)p.gray[(cy+dy)*CurvedEntranceStrokeTest.W+x]=(byte)(allPale||x>=318?175:0);
        }
        return p;
    }
    @Test public void joinedCurveWithFadedStemIsNotAnAttack(){assertFalse(page(2,false,false).hasSmall());}
    @Test public void destinationPitchAndCountArePreserved(){var n=page(2,false,false).read().notes();assertEquals(1,n.size());assertEquals(2,n.get(0).staffStep());}
    @Test public void disconnectedCurveCannotUseFadedStem(){assertTrue(page(2,false,true).hasSmall());}
    @Test public void whollyPaleCurveCannotSupplyDarkCore(){assertTrue(page(2,true,false).hasSmall());}
    @Test public void broadJoinedBandIsRetained(){assertTrue(page(5,false,false).hasSmall());}
    @Test public void fadedGraceStemPreservesRealSmallNote(){
        var p=page(2,false,false);p.head(306,174,4,4,true);
        for(int y=132;y<=174;y++){p.labels[y*CurvedEntranceStrokeTest.W+310]=1;p.gray[y*CurvedEntranceStrokeTest.W+310]=(byte)175;}
        assertTrue(p.hasSmall());
    }
    @Test public void inputArraysArePreserved(){var p=page(2,false,false);var g=p.gray.clone();var l=p.labels.clone();p.read();assertArrayEquals(g,p.gray);assertArrayEquals(l,p.labels);}
}
