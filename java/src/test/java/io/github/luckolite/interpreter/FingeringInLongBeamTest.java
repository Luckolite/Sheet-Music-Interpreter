// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
/** Original four-note beam with independently drawn finger numerals. */
public class FingeringInLongBeamTest {
 private byte[] page(boolean four,boolean below,boolean bracket) {
  byte[] g=SeparateBeamGroupsTest.ink(true,false),digit=new byte[g.length];Arrays.fill(digit,(byte)255);
  SeparateBeamGroupsTest.numeral(digit);int shift=below?105:0;
  for(int y=60;y<82;y++)for(int x=180;x<192;x++)if(digit[y*400+x]==0)g[(y+shift)*400+x]=0;
  for(int y=90;y<=140;y++)g[y*400+130]=0;
  for(int y=137;y<=143;y++)for(int x=121;x<=131;x++)g[y*400+x]=0;
  if(four) {
   String[] shape={"....##.","...###.","..#.##.",".##.##.","##..##.","##..##.","#######","....##.","....##.","....##.","....##."};
   for(int y=0;y<shape.length;y++)for(int x=0;x<7;x++)if(shape[y].charAt(x)=='#')
    for(int yy=0;yy<2;yy++)for(int xx=0;xx<2;xx++)g[(60+shift+y*2+yy)*400+80+x*2+xx]=0;
  }
  if(bracket){for(int x=153;x<=177;x++)g[(70+shift)*400+x]=0;for(int x=194;x<=220;x++)g[(70+shift)*400+x]=0;}
  return g;
 }
 private List<Integer> detect(byte[] g) {
  var notes=new ArrayList<ScoreNoteEvent>();for(float x:new float[]{126,156,186,216})notes.add(new ScoreNoteEvent(0,x/400,0,0,1,140f/240,false,0,1,2,0,1));
  return TripletRhythmDetector.apply(notes,List.of(new MeasureRegion(0,1,.2f,.6f)),g,400,240).stream().map(ScoreNoteEvent::tupletDivisor).toList();
 }
 @Test public void nearbyFourAndOppositeBeamProveFingering(){assertEquals(List.of(1,1,1,1),detect(page(true,true,false)));}
 @Test public void fourNoteBeamAloneDoesNotDisproveATriplet(){assertEquals(List.of(1,3,3,3),detect(page(false,true,false)));}
 @Test public void explicitBracketOverridesFingerContext(){assertEquals(List.of(1,3,3,3),detect(page(true,true,true)));}
 @Test public void numeralOnBeamSideStillDenotesATriplet(){assertEquals(List.of(1,3,3,3),detect(page(true,false,false)));}
}
