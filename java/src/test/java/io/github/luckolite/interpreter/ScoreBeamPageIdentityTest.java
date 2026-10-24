// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import static io.github.luckolite.interpreter.ScoreBeamSource.*;
/** Original identical-page lifecycle identity regression. IDs remain opaque input data. */
public final class ScoreBeamPageIdentityTest {
 static int checks;static void check(boolean x){checks++;if(!x)throw new AssertionError("Check "+checks);}
 static Column c(int n){return new Column(List.of(new Anchor(0,n)));}
 static void rejects(Runnable f){checks++;try{f.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Expected local-page rejection");}
 @org.junit.Test public void pageIdentity()throws Exception{main(new String[0]);}
 public static void main(String[]args)throws Exception{
  var group=new Group("x".repeat(256),List.of(c(4),c(2),c(6)),List.of(5,1),List.of(new Hook(2,2,Direction.FORWARD)));
  var local=capture(0,8,List.of(group),List.of(c(7)));
  var a=local.onPage(0);var b=local.onPage(1);
  check(a.groups().get(0).id().equals("beam-page:0:2"));check(b.groups().get(0).id().equals("beam-page:1:2"));
  check(a.onPage(0).equals(a));check(a.onPage(0).onPage(0).groups().get(0).id().length()<40);
  check(a.onPage(1).equals(b));
  check(a.groups().get(0).columns().equals(group.columns()));check(a.groups().get(0).bridges().equals(group.bridges()));check(a.groups().get(0).hooks().equals(group.hooks()));
  var joined=join(List.of(a,b));check(joined.groups().size()==2);check(joined.noteOrigins().size()==16);
  check(joined.noteOrigins().get(2).get().equals(new Anchor(0,2)));check(joined.noteOrigins().get(10).get().equals(new Anchor(1,2)));
  check(joined.resolve().groups().get(0).bridgeMask(0)==5);check(joined.resolve().groups().get(1).bridgeMask(0)==5);
  var edited=local.edited(List.of(6,4,-1));check(edited.onPage(1).groups().get(0).id().equals(b.groups().get(0).id()));
  check(edited.onPage(1).resolve().groups().get(0).bridgeMask(0)==0);check(edited.onPage(1).resolve().groups().get(0).bridgeMask(1)==0);
  var duplicates=local.edited(List.of(4,4,2,6));check(duplicates.onPage(1).groups().get(0).id().equals(b.groups().get(0).id()));check(duplicates.onPage(1).resolve().groups().get(0).columns().get(0).ambiguous());
  rejects(()->b.onPage(2));rejects(()->joined.onPage(0));rejects(()->joined.onPage(1));
  var opaque=new ScoreBeamSource(List.of(new Group("beam-page:0:777",group.columns(),group.bridges(),group.hooks())),local.detachedColumns(),local.noteOrigins());
  check(opaque.onPage(1).equals(b)); // Imported matching prefix cannot change relocation policy.
  check(local.groups().get(0).id().length()==256);check(local.noteOrigins().get(2).get().pageIndex()==0);
  System.out.println("PASS "+checks+" original page namespace assertions");
 }
}
