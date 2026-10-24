// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original source-free caller controls for complete filled-body ownership. */
public final class TD0335CallerOriginalTest {
  private static final int W=360,H=240,CX=210,CY=120,GAP=16;
  private static final class Scene {
    final byte[] labels=new byte[W*H],gray=new byte[W*H];
    Scene(){Arrays.fill(gray,(byte)250);}
    void p(int x,int y,int label,int shade){if(x>=0&&x<W&&y>=0&&y<H){labels[y*W+x]=(byte)label;gray[y*W+x]=(byte)shade;}}
    int[] members(){int seed=-1;for(int i=0;i<labels.length;i++)if(labels[i]==2){seed=i;break;}if(seed<0)return new int[0];boolean[] seen=new boolean[labels.length];int[] q=new int[labels.length];int read=0,end=1;q[0]=seed;seen[seed]=true;while(read<end){int p=q[read++],x=p%W,y=p/W;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){if(dx==0&&dy==0)continue;int xx=x+dx,yy=y+dy;if(xx<0||xx>=W||yy<0||yy>=H)continue;int n=yy*W+xx;if(!seen[n]&&(labels[n]&255)==2){seen[n]=true;q[end++]=n;}}}return Arrays.copyOf(q,end);}
  }
  private static Scene oval(boolean hollow,double innerCut){Scene s=new Scene();for(int y=72;y<117;y++)for(int x=CX+7;x<=CX+9;x++)s.p(x,y,1,40);for(int y=CY-8;y<=CY+8;y++)for(int x=CX-11;x<=CX+11;x++){double q=Math.pow((x-CX)/10.0,2)+Math.pow((y-CY)/7.0,2);if(q<=1&&(!hollow||q>=innerCut))s.p(x,y,2,160);}for(int x=130;x<=CX-9;x++){double t=(x-130.0)/(CX-139.0);int y=CY+(int)Math.round(3*Math.sin(t*Math.PI*2));s.p(x,y,2,35);s.p(x,y+1,2,35);}return s;}
  private static Scene joinedTwoBodies(){Scene s=new Scene();for(int y=CY-3*GAP;y<CY-3;y++)for(int x=CX+8;x<=CX+9;x++)s.p(x,y,1,40);for(int k=0;k<2;k++){int bx=CX+k*21;for(int y=CY-9;y<=CY+9;y++)for(int x=bx-13;x<=bx+13;x++){double q=Math.pow((x-bx)/10.0,2)+Math.pow((y-CY)/7.0,2);if(q<=1)s.p(x,y,2,160);}}for(int y=CY-2;y<=CY+2;y++)for(int x=CX+10;x<=CX+11;x++)s.p(x,y,2,160);return s;}
  private static Object component(int[] members)throws Exception{int l=W,t=H,r=-1,b=-1;long sx=0,sy=0;for(int p:members){int x=p%W,y=p/W;l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);sx+=x;sy+=y;}Class<?> c=Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Component");Constructor<?> k=c.getDeclaredConstructors()[0];k.setAccessible(true);return k.newInstance(members.length,l,r,t,b,(float)sx/members.length,(float)sy/members.length);}
  private static float duration(Scene s,int[] members)throws Exception{Object head=component(members);Class<?> c=head.getClass();Method m=OmrScoreInterpreter.class.getDeclaredMethod("detectUnbeamedDuration",byte[].class,byte[].class,int.class,int.class,c,float.class,int.class,java.util.List.class);m.setAccessible(true);return(float)m.invoke(null,s.labels,s.gray,W,H,head,(float)GAP,0,java.util.List.of(head));}
  private static ProposedFilledBodyCaller.Result inspect(Scene s,int[] members){return ProposedFilledBodyCaller.inspect(s.labels,s.gray,W,H,members,GAP);}
  @Test public void attachedCurvedBranchFilledBodyChangesOnlyOpenVersusFilledDecision()throws Exception{Scene s=oval(false,0);int[] full=s.members();assertEquals(360,full.length);ProposedFilledBodyCaller.Result body=inspect(s,full);assertEquals(ProposedFilledBodyCaller.State.FILLED_BODY,body.state());assertTrue(ProposedFilledBodyCaller.retainedComponentExactly(full,body));assertFalse(ProposedFilledBodyCaller.retainPriorOpenEvidence(true,body));assertEquals(1f,duration(s,full),0f);}
  @Test public void genuineHollowOvalKeepsPriorOpenDecision()throws Exception{Scene s=oval(true,.58);int[] full=s.members();ProposedFilledBodyCaller.Result body=inspect(s,full);assertNotEquals(ProposedFilledBodyCaller.State.FILLED_BODY,body.state());assertTrue(ProposedFilledBodyCaller.retainPriorOpenEvidence(true,body));assertEquals(2f,duration(s,full),0f);}
  @Test public void partialPocketCannotBecomeFilledAuthority()throws Exception{Scene s=oval(true,.18);int[] full=s.members();ProposedFilledBodyCaller.Result body=inspect(s,full);assertNotEquals(ProposedFilledBodyCaller.State.FILLED_BODY,body.state());assertTrue(ProposedFilledBodyCaller.retainPriorOpenEvidence(true,body));assertEquals(2f,duration(s,full),0f);}
  @Test public void joinedCompetingBodiesRemainUnknownAndDoNotOverrideOldResult()throws Exception{Scene s=joinedTwoBodies();int[] full=s.members();ProposedFilledBodyCaller.Result body=inspect(s,full);assertEquals(ProposedFilledBodyCaller.State.UNRESOLVED,body.state());assertTrue(ProposedFilledBodyCaller.retainedComponentExactly(full,body));assertTrue(ProposedFilledBodyCaller.retainPriorOpenEvidence(true,body));assertEquals(ScoreNoteEvent.DURATION_QUARTER,duration(s,full),0f);}
  @Test public void foreignTypedStrokeInsideBodyDoesNotMintFilledAuthority()throws Exception{Scene s=oval(false,0);for(int x=CX-12;x<=CX+12;x++)for(int y=CY-1;y<=CY+1;y++)s.p(x,y,4,35);int[] full=s.members();ProposedFilledBodyCaller.Result body=inspect(s,full);assertNotEquals(ProposedFilledBodyCaller.State.FILLED_BODY,body.state());assertTrue(ProposedFilledBodyCaller.retainPriorOpenEvidence(true,body));}
}

