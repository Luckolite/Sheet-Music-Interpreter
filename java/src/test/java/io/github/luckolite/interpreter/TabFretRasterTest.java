// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import org.junit.Test;import java.util.*;import static org.junit.Assert.*;
public class TabFretRasterTest {
    static final int W=600,H=260;static final float TOP=50,GAP=25;
    List<TablatureDecoder.Staff> tabs(){return List.of(new TablatureDecoder.Staff(TOP,GAP,-1,List.of(),List.of(20f,580f)));}
    byte[] page(int paper){byte[] p=new byte[W*H];Arrays.fill(p,(byte)paper);for(int s=0;s<6;s++)for(int x=20;x<=580;x++)p[(50+s*25)*W+x]=(byte)190;return p;}
    void rect(byte[] p,int x,int y,int width,int height){for(int yy=y;yy<y+height;yy++)for(int xx=x;xx<x+width;xx++)p[yy*W+xx]=0;}
    void zero(byte[] p,int x,int y){rect(p,x,y-9,3,18);rect(p,x+10,y-9,3,18);rect(p,x,y-9,13,3);rect(p,x,y+6,13,3);}
    @Test public void isolatesSeparateOpenStringsAndDoesNotInventAnExtraFret(){var g=page(255);zero(g,100,75);zero(g,150,75);var crops=TabFretRaster.crops(g,W,H,tabs());assertEquals(2,crops.size());assertEquals(1,crops.get(0).string());assertTrue(crops.get(0).right()<crops.get(1).left());}
    @Test public void grayPaperIsNotTreatedAsAVerticalStem(){var g=page(220);zero(g,100,75);assertEquals(1,TabFretRaster.crops(g,W,H,tabs()).size());}
    @Test public void earlyChordInsideTheBarIsNotAColumnOfStringNames(){var g=page(255);for(int s=0;s<6;s++)zero(g,70,50+s*25);assertEquals(6,TabFretRaster.crops(g,W,H,tabs()).size());}
    @Test public void cleaningRemovesTheStringThroughAnOpenFretButPreservesItsSides(){var g=page(255);zero(g,100,75);var clean=TabFretRaster.clean(g,W,H,tabs());assertEquals(255,clean[75*W+106]&255);assertEquals(0,clean[75*W+101]&255);assertEquals(190,g[75*W+106]&255);}
    @Test public void longRhythmStemIsNotAOneOnTheNextString(){var g=page(255);rect(g,150,147,4,80);assertTrue(TabFretRaster.crops(g,W,H,tabs()).isEmpty());}
    @Test public void barlineBesideFretDoesNotJoinItAsADigit(){var g=page(255);zero(g,308,100);rect(g,299,50,3,126);var t=List.of(new TablatureDecoder.Staff(TOP,GAP,-1,List.of(),List.of(20f,300f,580f)));var crops=TabFretRaster.crops(g,W,H,t);assertEquals(1,crops.size());assertTrue(crops.get(0).left()>302);}
    TablatureDecoder.Word word(String s,int x,int y,int width){return new TablatureDecoder.Word(s,(x-width*.5f)/W,(y-8f)/H,(x+width*.5f)/W,(y+8f)/H);}
    @Test public void repeatedOcrVotesProduceOneNoteAtOneCrop(){var c=List.of(new TabFretRaster.Crop(100,66,114,84,1,75,25));var good=word("3",107,75,14);var bad=word("9",107,75,14);var out=TabFretRaster.reconcile(tabs(),c,List.of(bad,good),List.of(good,good),W,H);assertEquals(1,out.size());assertEquals("3",out.get(0).text());}
    @Test public void croppedSingleDigitIsNotReplacedByAWholeRowNumber(){var c=List.of(new TabFretRaster.Crop(100,66,114,84,1,75,25));var out=TabFretRaster.reconcile(tabs(),c,List.of(word("2",107,75,14)),List.of(word("22",110,75,55)),W,H);assertEquals("2",out.get(0).text());}
    @Test public void excludesAboveStaffMeasureNumbers(){var c=List.of(new TabFretRaster.Crop(100,66,114,84,1,75,25));var out=TabFretRaster.reconcile(tabs(),c,List.of(),List.of(word("18",107,48,14)),W,H);assertTrue(out.isEmpty());}
    @Test public void openFretHasOneLongCentralCounter(){var g=page(255);zero(g,100,75);var clean=TabFretRaster.clean(g,W,H,tabs());assertTrue(TabFretRaster.openFret(clean,W,new TabFretRaster.Crop(100,66,113,84,1,75,25)));}
    @Test public void upperLoopOfNineIsNotAnOpenFret(){var g=page(255);zero(g,100,70);rect(g,110,70,3,20);assertFalse(TabFretRaster.openFret(TabFretRaster.clean(g,W,H,tabs()),W,new TabFretRaster.Crop(100,61,113,90,1,75,25)));}
    @Test public void denseTallScreenshotControlIsNotAFret(){var g=page(255);rect(g,400,60,30,100);assertTrue(TabFretRaster.crops(g,W,H,tabs()).isEmpty());}
    @Test public void confirmedHammerOnIsPreservedAfterFretIsolation(){var cs=List.of(new TabFretRaster.Crop(96,66,108,84,1,75,25),new TabFretRaster.Crop(112,66,124,84,1,75,25));var out=TabFretRaster.reconcile(tabs(),cs,List.of(word("7",102,75,12),word("9",118,75,12)),List.of(word("7h9",110,75,24)),W,H);assertEquals(1,out.size());assertEquals("7h9",out.get(0).text());}
    @Test public void noOpeningBarlineDoesNotDiscardTheFirstMeasure(){var fs=List.of(new TablatureDecoder.Fret(100,75,1,3),new TablatureDecoder.Fret(350,75,1,5),new TablatureDecoder.Fret(550,75,1,7));var t=List.of(new TablatureDecoder.Staff(TOP,GAP,-1,fs,List.of(200f,450f)));var score=TablatureDecoder.apply(new ScorePageInterpretation(List.of(),List.of()),t,W,H);assertEquals(3,score.notes().size());assertEquals(3,score.measures().size());}
    @Test public void lighterBarlinePassAddsDivisionsWithoutLosingDarkOnes(){var g=page(255);rect(g,20,50,2,126);rect(g,580,50,2,126);for(int y=50;y<=175;y++)g[y*W+300]=(byte)225;var t=TablatureDecoder.detect(g,W,H);assertEquals(1,t.size());assertTrue(t.get(0).bars().stream().anyMatch(x->Math.abs(x-300)<2));}
}
