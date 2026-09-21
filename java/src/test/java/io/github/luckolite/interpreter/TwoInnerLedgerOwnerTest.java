// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original two-staff examples with three printed ledger rules. */
public class TwoInnerLedgerOwnerTest {
    static RemoteLedgerEvidenceTest.Page page(boolean space,boolean hollow,int innerRules,boolean wide){
        var p=new RemoteLedgerEvidenceTest.Page(true,false,true,true,false);
        for(int y=48;y<128;y++)for(int x=70;x<150;x++){p.gray[y*480+x]=(byte)255;p.labels[y*480+x]=0;}
        for(int y=16;y<=48;y+=8)p.rule(14,465,y,4);
        p.head(350,32,6,4,false);p.stem(356,32,18);
        int headY=space?72:80;
        for(int i=0;i<=innerRules;i++){
            int y=80+i*16;p.rule(wide?14:86,wide?465:124,y,5);
        }
        p.head(105,headY,11,7,hollow);if(!hollow)p.stem(94,headY,120);
        for(int i=0;i<=innerRules;i++)for(int x=wide?14:86;x<=(wide?465:124);x++)p.gray[(80+i*16)*480+x]=0;
        return p;
    }
    @Test public void twoInnerRulesAssignHighLineToDistantStaff(){var n=page(false,false,2,false).remote();assertNotNull(n);assertEquals(14,n.staffStep());}
    @Test public void nearestSpaceLedgerIsNotRequiredInInnerCount(){var n=page(true,false,2,false).remote();assertNotNull(n);assertEquals(15,n.staffStep());}
    @Test public void hollowHeadUsesSamePrintedOwnership(){var n=page(false,true,2,false).remote();assertNotNull(n);assertEquals(14,n.staffStep());}
    @Test public void oneInnerRuleCannotProveTheDistantStaff(){var n=page(false,false,1,false).remote();assertTrue(n==null||n.staffStep()!=14);}
    @Test public void longBeamRulesCannotProveTheDistantStaff(){var n=page(false,false,2,true).remote();assertTrue(n==null||n.staffStep()!=14);}
    @Test public void ownershipDoesNotChangeInputPixels(){var p=page(true,false,2,false);var l=p.labels.clone();var g=p.gray.clone();p.remote();assertArrayEquals(l,p.labels);assertArrayEquals(g,p.gray);}
    @Test public void nearbyStaffOutweighsOtherChordTonesLedgers(){
        var p=new RemoteLedgerEvidenceTest.Page(true,false,true,true,false);
        for(int y=0;y<128;y++)for(int x=0;x<480;x++){p.gray[y*480+x]=(byte)255;p.labels[y*480+x]=0;}
        for(int y=0;y<=64;y+=16)p.rule(14,465,y,4);
        p.head(350,32,11,7,false);p.stem(361,32,8);
        for(int y:new int[]{80,96,112})p.rule(86,124,y,5);
        p.head(105,112,11,7,false);p.stem(94,112,160);
        var n=p.remote();assertNotNull(n);assertEquals(10,n.staffStep());
    }
    @Test public void ledgerStemReachingDistantStaffBeatsNearbyStaff() {
        var p=new RemoteLedgerEvidenceTest.Page(true,false,true,true,false);
        for(int y=0;y<128;y++)for(int x=0;x<480;x++){p.gray[y*480+x]=(byte)255;p.labels[y*480+x]=0;}
        for(int y=0;y<=64;y+=16)p.rule(14,465,y,4);
        p.head(350,32,11,7,false);p.stem(361,32,8);
        for(int y:new int[]{80,96,112})p.rule(86,124,y,5);
        p.head(105,112,11,7,false);p.stem(116,112,40);
        var n=p.remote();assertNotNull(n);assertEquals(-6,n.staffStep());
    }
}
