// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class TabPerformanceTest {
    List<TablatureDecoder.Fret> parse(String s){return TabNotation.parse(s,90,150,140,0);}
    @Test public void bendTargetIsNotASecondPluckedNote(){var fs=parse("7b9r7");assertEquals(1,fs.size());assertEquals(7,fs.get(0).fret());assertEquals(TabEffect.BEND_RELEASE,TabEffect.kind(fs.get(0).marks()));assertEquals(2,TabEffect.delta(fs.get(0).marks()));}
    @Test public void hammerAndPullRemainSeparatePitchesWithSoftAttack(){var fs=parse("5h7p5");assertEquals(3,fs.size());assertEquals(TabEffect.HAMMER,TabEffect.kind(fs.get(1).marks()));assertEquals(TabEffect.PULL,TabEffect.kind(fs.get(2).marks()));assertTrue(TabEffect.gain(fs.get(1).marks())<1);}
    @Test public void slideStartsAtPreviousPitchWithoutChangingTarget(){var f=parse("14/16").get(1);assertEquals(16,f.fret());assertEquals(-2,TabEffect.pitchOffset(f.marks(),0,1),0);assertEquals(0,TabEffect.pitchOffset(f.marks(),.4,1),0);}
    @Test public void vibratoCombinesWithBend(){var f=parse("7b9~").get(0);assertTrue((f.marks()&TabEffect.VIBRATO)!=0);assertEquals(2,TabEffect.pitchOffset(f.marks(),1,1),.001);}
    @Test public void malformedAndUnsupportedFretSyntaxIsRejected(){for(String s:List.of("1412","7b5","7b9r6","5p7","7h5","<3>","O"))assertTrue(s,parse(s).isEmpty());}
    @Test public void naturalHarmonicUsesNodePitch(){assertEquals(19,TabEffect.harmonicOffset(parse("<7>").get(0).fret()));assertEquals(24,TabEffect.harmonicOffset(5));}
    @Test public void effectBitsSurviveScoreSerializationContract(){int marks=parse("5h7").get(1).marks();var n=new ScoreNoteEvent(0,.2f,0,0,1).withArticulations(marks);assertEquals(marks,n.articulations());assertEquals(marks,n.withOctaveShift(-1).articulations());}
    @Test public void bendReleaseReturnsToOriginalPitch(){int m=TabEffect.encode(TabEffect.BEND_RELEASE,2);assertEquals(0,TabEffect.pitchOffset(m,0,2),0);assertEquals(2,TabEffect.pitchOffset(m,1,2),0);assertEquals(0,TabEffect.pitchOffset(m,2,2),0);}
    TablatureDecoder.Staff staff(List<TablatureDecoder.Fret> f){return new TablatureDecoder.Staff(140,20,-1,f,List.of(20f,480f));}
    TablatureDecoder.Word word(String s,float x,float y){return new TablatureDecoder.Word(s,(x-6)/500,(y-6)/400,(x+6)/500,(y+6)/400);}
    @Test public void explicitDottedDurationsAndRestsPreserveSilentTime(){var tabs=TablatureDecoder.withWords(List.of(staff(List.of())),List.of(word("5",100,140),word("Q.",100,105),word("𝄾",200,105),word("7",300,140),word("H",300,105)),500,400);var score=TablatureDecoder.apply(new ScorePageInterpretation(List.of(),List.of()),tabs,500,400);assertEquals(2,score.notes().size());assertEquals(1,score.notes().get(0).augmentationDots());assertEquals(.5,score.notes().get(0).followingRestBeats(),0);assertEquals(2,score.notes().get(1).unbeamedDurationBeats(),0);assertEquals(2,ScoreNoteTiming.beatInMeasure(score.notes().get(1),score.notes(),4),.01);}
    @Test public void durationLetterOnAStringIsNotRhythmEvidence(){var tabs=TablatureDecoder.withWords(List.of(staff(List.of())),List.of(word("5",100,140),word("E",100,160)),500,400);assertEquals(0,tabs.get(0).frets().get(0).duration(),0);}
    @Test public void stemAndTwoBeamsGiveSixteenthWithoutReadingStringRulesAsBeams(){byte[] g=new byte[500*400];Arrays.fill(g,(byte)255);for(int y=140;y<=240;y+=20)for(int x=20;x<480;x++)g[y*500+x]=0;for(int y=254;y<=295;y++)g[y*500+100]=0;for(int y:new int[]{284,294})for(int x=100;x<=145;x++)g[y*500+x]=0;var t=TabNotation.rasterRhythm(List.of(staff(List.of(new TablatureDecoder.Fret(100,140,0,5)))),g,500,400);assertEquals(2,t.get(0).frets().get(0).beams());}
    @Test public void simpleTabsStayUnknownInsteadOfAssumingQuarters(){byte[] g=new byte[500*400];Arrays.fill(g,(byte)255);var t=TabNotation.rasterRhythm(List.of(staff(parse("5"))),g,500,400);assertEquals(0,t.get(0).frets().get(0).duration(),0);assertEquals(0,t.get(0).frets().get(0).beams());}
}
