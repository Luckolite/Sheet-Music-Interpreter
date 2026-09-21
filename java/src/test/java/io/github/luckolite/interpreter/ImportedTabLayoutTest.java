// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
/** Original raster rules covering imported tab layouts, without private fixtures. */
public class ImportedTabLayoutTest {
 static final int W=600,H=700;
 byte[] blank(){var g=new byte[W*H];Arrays.fill(g,(byte)255);return g;}
 void row(byte[] g,int top,int gap,int lines,int shade,boolean dashed){
  for(int n=0;n<lines;n++)for(int x=20;x<580;x++)
   if(!dashed||x%10<8)g[(top+n*gap)*W+x]=(byte)shade;
 }
 @Test public void enlargedSixStringSpacingIsSupported(){var g=blank();row(g,40,75,6,0,false);var s=TablatureDecoder.detect(g,W,H);assertEquals(1,s.size());assertEquals(75,s.get(0).gap(),0);}
 @Test public void faintRulesAreRecovered(){var g=blank();row(g,100,20,6,242,false);assertEquals(1,TablatureDecoder.detect(g,W,H).size());}
 @Test public void dashedTextTabsAreSupported(){var g=blank();row(g,60,70,6,0,true);assertEquals(1,TablatureDecoder.detect(g,W,H).size());}
 @Test public void repeatedThresholdsDoNotDuplicateRows(){var g=blank();row(g,100,20,6,0,false);assertEquals(1,TablatureDecoder.detect(g,W,H).size());}
 @Test public void fivePaleRulesAreNotTabs(){var g=blank();row(g,100,20,5,242,false);assertTrue(TablatureDecoder.detect(g,W,H).isEmpty());}
 @Test public void darkStaffAndShorterBeamAreNotTabs(){var g=blank();row(g,100,20,5,0,false);for(int x=20;x<480;x++)g[200*W+x]=0;assertTrue(TablatureDecoder.detect(g,W,H).isEmpty());}
 @Test public void faintInterruptedRulesNeedVerticalBoundaries(){var g=blank();row(g,100,20,6,215,false);for(int x=250;x<345;x++)g[100*W+x]=(byte)255;assertTrue(TablatureDecoder.detect(g,W,H).isEmpty());for(int x:new int[]{20,579})for(int y=100;y<=200;y++)g[y*W+x]=(byte)215;assertEquals(1,TablatureDecoder.detect(g,W,H).size());}
 @Test public void partialDarkRowCannotPairWithFollowingTab(){var g=blank();row(g,100,20,6,0,false);for(int x=20;x<580;x++)g[100*W+x]=(byte)230;row(g,300,20,6,0,false);var s=TablatureDecoder.detect(g,W,H);assertEquals(2,s.size());assertEquals(-1,s.get(1).standardTop(),0);}
 @Test public void paleBarlinesStillSplitMeasures(){var g=blank();row(g,100,20,6,235,false);for(int x:new int[]{20,300,579})for(int y=100;y<=200;y++)g[y*W+x]=(byte)235;assertEquals(3,TablatureDecoder.detect(g,W,H).get(0).bars().size());}
}
