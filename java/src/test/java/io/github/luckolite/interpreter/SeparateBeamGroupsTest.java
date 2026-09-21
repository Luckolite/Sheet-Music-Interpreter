// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
/** Original beam, stem, staff and numeral drawings. */
public class SeparateBeamGroupsTest {
 static final int W=400,H=240;
 static byte[] ink(boolean continuous,boolean down){
  byte[] g=new byte[W*H];Arrays.fill(g,(byte)255);
  for(int x=120;x<=240;x++)for(int d=-2;d<=2;d++){
   if(continuous||x<=160||x>=190)g[(90+d)*W+x]=0;
  }
  for(int x:new int[]{160,190,220})for(int y=90;y<=140;y++)g[y*W+x]=0;
  for(int hx:new int[]{156,186,216})for(int y=137;y<=143;y++)for(int x=hx-5;x<=hx+5;x++)g[y*W+x]=0;
  for(int y:new int[]{100,112,124,136})for(int x=0;x<W;x++)g[y*W+x]=0;
  if(down){byte[] f=new byte[g.length];for(int y=0;y<H;y++)System.arraycopy(g,y*W,f,(H-1-y)*W,W);return f;}return g;
 }
 @Test public void detectsDistinctOutwardBeamsAcrossStaffRules(){assertTrue(SeparateBeamGroups.between(ink(false,false),W,H,156,140,186,140,12));}
 @Test public void downStemsHaveTheSameBoundary(){assertTrue(SeparateBeamGroups.between(ink(false,true),W,H,156,99,186,99,12));}
 @Test public void continuousBeamIsNotABoundary(){assertFalse(SeparateBeamGroups.between(ink(true,false),W,H,156,140,186,140,12));}
 @Test public void absentBeamEvidenceCannotRejectFlaggedTuplet(){byte[] g=new byte[W*H];Arrays.fill(g,(byte)255);assertFalse(SeparateBeamGroups.between(g,W,H,156,140,186,140,12));}
 static void numeral(byte[] g){String[] a={"..#######...", ".##########.", "###......###", "####.....###", "####.....###", "####.....###", ".##.....####", ".......####.", "......####..", "....#####...", "....#####...", "....#####...", "......####..", ".......####.", "##.....####.", "###....####.", "###....####.", "###....####.", ".###....###.", "..########..", "..########..", "....####...."};for(int y=0;y<a.length;y++)for(int x=0;x<a[y].length();x++)if(a[y].charAt(x)=='#')g[(60+y)*W+180+x]=0;}
 static List<ScoreNoteEvent> detect(byte[] g){var notes=new ArrayList<ScoreNoteEvent>();for(float x:new float[]{156,186,216})notes.add(new ScoreNoteEvent(0,x/W,0,0,1,140f/H,false,0,1,2,0,1));return TripletRhythmDetector.apply(notes,List.of(new MeasureRegion(0,1,.2f,.6f)),g,W,H);}
 @Test public void fingeringDoesNotRegroupSeparateBeams(){byte[] g=ink(false,false);numeral(g);assertTrue(detect(g).stream().allMatch(n->n.tupletDivisor()==1));}
 @Test public void numeralOverContinuousBeamStillScalesTriplet(){byte[] g=ink(true,false);numeral(g);assertTrue(detect(g).stream().allMatch(n->n.tupletDivisor()==3));}
 @Test public void explicitBracketPermitsSeparateBeams(){byte[] g=ink(false,false);numeral(g);for(int x=153;x<=177;x++)g[70*W+x]=0;for(int x=194;x<=220;x++)g[70*W+x]=0;assertTrue(detect(g).stream().allMatch(n->n.tupletDivisor()==3));}
}
