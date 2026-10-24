// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import java.io.*;
import static io.github.luckolite.interpreter.ScoreBeamSource.*;
/** Original canonical/import boundary regressions: physical beams never cross page paper. */
public final class ScoreBeamPageBoundaryTest {
 static int count;
 static void check(boolean b){count++;if(!b)throw new AssertionError("Check "+count);}
 static void rejects(Runnable f){count++;try{f.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Expected boundary rejection "+count);}
 static Column c(int p,int n){return new Column(List.of(new Anchor(p,n)));}
 static Group g(String id,int p){return new Group(id,List.of(c(p,0),c(p,1)),List.of(1),List.of());}
 static byte[] crossPageFrame(boolean chord,boolean detached)throws Exception {
  var bytes=new ByteArrayOutputStream();var o=new DataOutputStream(bytes);
  o.writeInt(detached?0:1);
  if(!detached){o.writeInt(1);o.writeByte('g');o.writeInt(2);}
  if(detached)o.writeInt(1);
  o.writeInt(chord?2:1);o.writeInt(0);o.writeInt(0);
  if(chord){o.writeInt(1);o.writeInt(1);}
  if(!detached){o.writeInt(1);o.writeInt(chord?0:2);o.writeInt(2);o.writeInt(1);o.writeInt(1);o.writeInt(0);o.writeInt(0);}
  o.writeInt(0); // no retained origins; source evidence may outlive current notes
  var framed=new ByteArrayOutputStream();var out=new DataOutputStream(framed);out.writeInt(bytes.size());bytes.writeTo(out);return framed.toByteArray();
 }
 static void rejectsFrame(byte[] bytes)throws Exception {count++;try{ScoreBeamSourceWire.decode(bytes,0);}catch(IOException expected){return;}throw new AssertionError("Imported cross-page frame accepted");}
 @org.junit.Test public void canonicalAndImportedPages()throws Exception{main(new String[0]);}
 public static void main(String[]args)throws Exception {
  rejects(()->new Column(List.of(new Anchor(0,0),new Anchor(1,1))));
  rejects(()->new Group("cross",List.of(c(0,0),c(2,1)),List.of(1),List.of()));
  rejects(()->new Group("cross-hook",List.of(c(0,0),c(2,1)),List.of(1),List.of(new Hook(0,2,Direction.FORWARD))));
  var chord=new Column(List.of(new Anchor(0,0),new Anchor(0,1)));check(chord.members().size()==2);
  var left=capture(0,2,List.of(g("left",0)),List.of());var right=capture(1,2,List.of(g("right",1)),List.of());
  var joined=join(List.of(left,right));check(joined.groups().size()==2);check(joined.resolve().groups().get(0).bridgeMask(0)==1);check(joined.resolve().groups().get(1).bridgeMask(0)==1);
  check(joined.noteOrigins().get(2).get().pageIndex()==1);
  var canonical=new ScoreBeamSource(List.of(g("left",0),g("right",1)),List.of(c(7,0)),List.of());check(canonical.groups().size()==2);check(canonical.detachedColumns().get(0).members().get(0).pageIndex()==7);
  check(ScoreBeamSourceWire.decode(ScoreBeamSourceWire.encode(canonical),0).equals(canonical));
  check(left.edited(List.of(0,0,1)).resolve().groups().get(0).columns().get(0).ambiguous());
  rejectsFrame(crossPageFrame(true,false));
  rejectsFrame(crossPageFrame(false,false));
  rejectsFrame(crossPageFrame(true,true));
  System.out.println("PASS "+count+" original canonical/import physical-page assertions");
 }
}
