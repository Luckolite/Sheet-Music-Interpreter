// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
/** Original independently owned flags and unrelated nearby notation. */
public class OwnedFlagSizeOriginalTest {
 private ScoreNoteEvent read(String ornament){int w=340,h=220;byte[]g=new byte[w*h],l=new byte[w*h];Arrays.fill(g,(byte)255);for(int y:new int[]{80,94,108,122,136})for(int x=20;x<320;x++){g[y*w+x]=60;l[y*w+x]=1;}int cx=120,cy=122,rx=10,ry=6,shaft=130;for(int y=cy-ry;y<=cy+ry;y++)for(int x=cx-rx;x<=cx+rx;x++)if((x-cx)*(x-cx)/(double)(rx*rx)+(y-cy)*(y-cy)/(double)(ry*ry)<=1){l[y*w+x]=2;g[y*w+x]=(byte)(x>=118&&x<=122&&y>=120&&y<=124?240:50);}for(int y=66;y<=122;y++)for(int x=shaft-1;x<=shaft;x++){if(ornament.equals("broken")&&y>=100&&y<=112)continue;g[y*w+x]=35;l[y*w+x]=3;}
 if(Set.of("owned","detached","broken","foreign").contains(ornament)){int shift=ornament.equals("detached")?12:ornament.equals("foreign")?35:0;for(int d=0;d<=29;d++){int x=shaft+shift+Math.round(12*(float)Math.sin(Math.PI*d/29));for(int dx=0;dx<2;dx++){g[(66+d)*w+x+dx]=35;l[(66+d)*w+x+dx]=3;}}if(ornament.equals("foreign"))for(int y=66;y<=125;y++){g[y*w+shaft+35]=35;l[y*w+shaft+35]=3;}}
 if(ornament.equals("slur"))for(int x=145;x<=200;x++){int y=92+Math.round((x-172)*(x-172)*.008f);g[y*w+x]=35;l[y*w+x]=5;}
 if(ornament.equals("hairpin"))for(int x=145;x<=195;x++){int off=(x-145)/7;for(int y:new int[]{100-off,100+off}){g[y*w+x]=35;l[y*w+x]=5;}}
 if(ornament.equals("rest"))for(int d=0;d<=30;d++){int x=165+Math.round(9*(float)Math.sin(Math.PI*d/30));g[(68+d)*w+x]=35;l[(68+d)*w+x]=3;}
 byte[]lg=l.clone(),gg=g.clone();var p=OmrScoreInterpreter.analyze(l,g,w,h,List.of(new MeasureRegion(.08f,.94f,.30f,.76f)));assertArrayEquals(lg,l);assertArrayEquals(gg,g);return p.notes().stream().min(Comparator.comparingDouble(n->Math.abs(n.pageY()-122f/h)+Math.abs(n.positionInMeasure()-(120f/w-.08f)/.86f))).orElseThrow();}
 private void half(String mode){var n=read(mode);assertEquals(mode,0,n.beamCount());assertTrue(mode,n.unbeamedDurationBeats()>=2);}
 @Test public void oversizedPaleHeadWithCompleteOwnedFlagIsEighth(){var n=read("owned");assertEquals(1,n.beamCount());assertEquals(0,n.unbeamedDurationBeats(),0);}
 @Test public void halfWithoutFlagStaysSustained(){half("none");}
 @Test public void detachedHookCannotShortenHalf(){half("detached");}
 @Test public void brokenShaftCannotBorrowFlag(){half("broken");}
 @Test public void slurDoesNotShortenHalf(){half("slur");}
 @Test public void hairpinDoesNotShortenHalf(){half("hairpin");}
 @Test public void restDoesNotShortenHalf(){half("rest");}
 @Test public void foreignStemFlagDoesNotShortenHalf(){half("foreign");}
}
