// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original generated header geometry; no score images or extracted glyphs. */
public class DamagedHeaderRecoveryTest {
    @Test public void clefSplitAcrossTwoClassesStillLocatesTheKey() {
        var f=new JoinedSignatureSharpTest();f.row(100,4,0,false);
        for(int y=70;y<=180;y++)f.ink(56,y,3);
        for(int y=125;y<=180;y++)for(int x=30;x<=56;x++)f.labels[y*640+x]=(byte)(y==125?0:5);
        assertEquals(List.of(4),f.keys());
    }
    @Test public void missingAccidentalMaskUsesPrintedInkBesideVerifiedSymbols() {
        var f=new JoinedSignatureSharpTest();f.row(100,4,0,false);
        for(int y=102;y<=146;y++)for(int x=93;x<=108;x++)f.labels[y*640+x]=0;
        assertEquals(List.of(4),f.keys());
    }
    @Test public void blankSourceDoesNotInventAMissingAccidental() {
        var f=new JoinedSignatureSharpTest();f.row(100,3,0,false);
        assertEquals(List.of(3),f.keys());
    }
    @Test public void adjacentSystemsLedgerNoteCannotShortenTheKeyHeader() {
        var f=new JoinedSignatureSharpTest();f.row(100,4,0,false);f.row(310,4,0,false);
        for(int y=210;y<=222;y++)for(int x=93;x<=109;x++)
            if((x-101)*(x-101)/64d+(y-216)*(y-216)/36d<=1)f.ink(x,y,2);
        assertEquals(List.of(4),f.keys());
    }
    @Test public void splitNumeralsOutsideTheStaffCannotBecomeAClef() {
        var f=new JoinedSignatureSharpTest();f.row(100,4,0,false);
        for(int y=70;y<=180;y++)f.ink(56,y,3);
        for(int y=125;y<=180;y++)for(int x=30;x<=56;x++)f.labels[y*640+x]=(byte)(y==125?0:5);
        // The split shapes have no printed staff beside them, as with exercise numbers.
        for(int y=95;y<=169;y++)for(int x=70;x<=74;x++)f.gray[y*640+x]=(byte)255;
        assertEquals(List.of(),f.keys());
    }
    @Test public void missingPrefixCannotProveAReducedRepeatedKey() {
        var f=new JoinedSignatureSharpTest();f.row(100,4,0,false);f.row(310,4,0,false);
        for(int y=280;y<=360;y++)for(int x=70;x<=110;x++) {
            f.labels[y*640+x]=0;f.gray[y*640+x]=(byte)255;
        }
        assertEquals(List.of(4),f.keys());
    }
    @Test public void clippedSharpTailInSymbolClassStillPreservesTheKey() {
        var f=new JoinedSignatureSharpTest();f.row(100,4,0,false);f.row(310,3,0,true);
        for(int y=304;y<=348;y++)for(int x=135;x<=150;x++)
            if(f.labels[y*640+x]==3)f.labels[y*640+x]=5;
        assertEquals(List.of(4),f.keys());
    }
    private int ordered(float[][] values,int ordinate)throws Exception {
        var m=OmrScoreInterpreter.class.getDeclaredMethod("orderedFlatSpines",List.class,float.class,int.class);
        m.setAccessible(true);return (Integer)m.invoke(null,Arrays.asList(values),16f,ordinate);
    }
    @Test public void flatEndpointsSurviveAnExtendedUpperShaft() throws Exception {
        float[][] spines={{80,100,40,120},{100,76,40,96},{120,94,68,128}};
        assertEquals(2,ordered(spines,1));assertEquals(3,ordered(spines,3));
    }
    @Test public void endpointsDoNotCountSameHeightStemsAsAFlatRun() throws Exception {
        assertEquals(1,ordered(new float[][]{{80,100,40,120},{100,100,40,120}},3));
    }
}
