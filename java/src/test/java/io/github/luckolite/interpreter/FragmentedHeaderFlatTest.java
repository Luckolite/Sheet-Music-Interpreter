// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

/** Original header geometry with semantic-only gaps through clef and flat spines. */
public class FragmentedHeaderFlatTest {
    private HeaderFlatHeadTest.Page page(boolean splitClef,boolean splitSpine,boolean spine) {
        var p=new HeaderFlatHeadTest.Page(true);p.flat(0,spine);
        if(splitClef)for(int x=25;x<=51;x++)p.labels[160*HeaderFlatHeadTest.W+x]=4;
        if(splitSpine)for(int y=121;y<=149;y+=7)for(int x=82;x<=84;x++)p.labels[y*HeaderFlatHeadTest.W+x]=4;
        return p;
    }
    @Test public void dividedClefStillIdentifiesKeyHeader(){assertFalse(page(true,false,true).headAt(92));}
    @Test public void dividedSpineStillIdentifiesFlatBowl(){assertFalse(page(false,true,true).headAt(92));}
    @Test public void dividedClefAndSpineStillIdentifyFlatBowl(){assertFalse(page(true,true,true).headAt(92));}
    @Test public void absentAccidentalSpineStillKeepsUnprovenHead(){assertTrue(page(true,false,false).headAt(92));}
    @Test public void actualNoteAfterDividedClefSurvives(){
        var p=new HeaderFlatHeadTest.Page(true);for(int x=25;x<=51;x++)p.labels[160*HeaderFlatHeadTest.W+x]=4;
        p.note(92,140,false,false);assertTrue(p.headAt(92));
    }
    private HeaderFlatHeadTest.Page faded(boolean lowerTip){
        var p=page(true,true,true);
        for(int y=115;y<=133;y++)for(int x=82;x<=84;x++)p.gray[y*HeaderFlatHeadTest.W+x]=(byte)230;
        if(lowerTip)for(int y=130;y<=156;y++)for(int x=80;x<=102;x++){
            int at=y*HeaderFlatHeadTest.W+x;
            if(p.labels[at]==2)p.labels[at]=5;
            if(x>=85&&x<=91&&y>=146&&y<=151&&(p.gray[at]&255)<180)p.labels[at]=2;
        }
        return p;
    }
    private Object call(String name,Object...args)throws Exception{
        for(var m:OmrScoreInterpreter.class.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){
            m.setAccessible(true);return m.invoke(null,args);
        }
        throw new AssertionError("Missing method");
    }
    private boolean header(HeaderFlatHeadTest.Page p)throws Exception{
        int w=HeaderFlatHeadTest.W,h=HeaderFlatHeadTest.H;
        var heads=(List<?>)call("findComponents",p.labels,w,h,(byte)2);
        Object target=null;
        for(Object head:heads){var x=head.getClass().getDeclaredMethod("centerX");x.setAccessible(true);
            if((float)x.invoke(head)<150){target=head;break;}}
        assertNotNull(target);
        var staffs=call("findStaffs",p.labels,p.gray,w,h,HeaderFlatHeadTest.M);
        var glyphs=call("findComponents",p.labels,w,h,(byte)3);
        return (boolean)call("isHeaderFlatHead",p.labels,p.gray,w,h,target,staffs,glyphs);
    }
    @Test public void fadedPrintedSpineStillProvesHeaderFlat()throws Exception{assertTrue(header(faded(false)));}
    @Test public void lowerBowlTipDoesNotRequireWholeFlatPitchCenter()throws Exception{assertTrue(header(faded(true)));}
    @Test public void missingRawSpineCannotRemoveLowerTip()throws Exception{
        var p=faded(true);for(int y=115;y<=133;y++)for(int x=82;x<=84;x++)p.gray[y*HeaderFlatHeadTest.W+x]=(byte)255;
        assertFalse(header(p));
    }
    @Test public void flatPaperShadeCannotSupplySpine()throws Exception{
        var p=faded(true);for(int y=110;y<=155;y++)for(int x=70;x<=108;x++){
            int at=y*HeaderFlatHeadTest.W+x;if((p.gray[at]&255)>180)p.gray[at]=(byte)230;
        }
        assertFalse(header(p));
    }
    @Test public void fadedHeaderRecoveryPreservesSourceArrays(){
        var p=faded(true);var l=p.labels.clone();var g=p.gray.clone();p.headAt(88);
        assertArrayEquals(l,p.labels);assertArrayEquals(g,p.gray);
    }
}
