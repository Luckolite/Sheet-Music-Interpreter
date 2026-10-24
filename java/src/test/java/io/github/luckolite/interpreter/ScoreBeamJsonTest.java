// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import static io.github.luckolite.interpreter.ScoreBeamSource.*;
/** Original synthetic JSON contract test, independent of recognition pixels. */
public final class ScoreBeamJsonTest {
 private static int checks;
 private static void check(boolean v){checks++;if(!v)throw new AssertionError("Check "+checks);}
 private static String json(Object value)throws Exception{return Main.json(value);}
 @org.junit.Test public void recordAndLifecycle() throws Exception { main(new String[0]); }
 public static void main(String[]args)throws Exception{
  var c0=new Column(List.of(new Anchor(9,0),new Anchor(9,1)));
  var c1=new Column(List.of(new Anchor(9,2)));
  var source=capture(9,4,List.of(new Group("synthetic-\"\\\nα",List.of(c0,c1),List.of(5),List.of(new Hook(0,2,Direction.BACKWARD)))),List.of(new Column(List.of(new Anchor(9,3))))).edited(List.of(0,-1,2,2));
  String expected="{\"groups\":[{\"id\":\"synthetic-\\\"\\\\\\u000aα\",\"columns\":[{\"members\":[{\"pageIndex\":9,\"noteIndex\":0},{\"pageIndex\":9,\"noteIndex\":1}]},{\"members\":[{\"pageIndex\":9,\"noteIndex\":2}]}],\"bridges\":[5],\"hooks\":[{\"columnIndex\":0,\"level\":2,\"direction\":\"BACKWARD\"}]}],\"detachedColumns\":[{\"members\":[{\"pageIndex\":9,\"noteIndex\":3}]}],\"noteOrigins\":[{\"pageIndex\":9,\"noteIndex\":0},null,{\"pageIndex\":9,\"noteIndex\":2},{\"pageIndex\":9,\"noteIndex\":2}]}";
  check(json(source).equals(expected));
  check(source.noteOrigins().get(1).isEmpty());
  check(source.noteOrigins().get(2).equals(source.noteOrigins().get(3)));
  var notes=java.util.stream.IntStream.range(0,4).mapToObj(i->new ScoreNoteEvent(0,i*.1f,2+i,0,1)).toList();
  var old=new ScorePageInterpretation(List.of(new MeasureRegion(.1f,.9f,.1f,.3f)),notes);
  check(json(old).contains("\"beamSource\":{\"groups\":[],\"detachedColumns\":[],\"noteOrigins\":[null,null,null,null]}"));
  var page=old.withBeamSource(source);check(json(page).contains("\"beamSource\":"+expected));
  var edited=page.withNotes(List.of(notes.get(3),notes.get(0),notes.get(1)),List.of(3,0,-1));
  check(edited.beamSource().groups().equals(source.groups()));
  check(edited.beamSource().detachedColumns().equals(source.detachedColumns()));
  check(edited.beamSource().noteOrigins().equals(List.of(Optional.of(new Anchor(9,2)),Optional.of(new Anchor(9,0)),Optional.empty())));
  check(json(edited).contains("\"noteOrigins\":[{\"pageIndex\":9,\"noteIndex\":2},{\"pageIndex\":9,\"noteIndex\":0},null]"));
  check(json(page).contains("BACKWARD"));
  check(json(source).equals(expected));
  System.out.println("PASS "+checks+" original typed beam JSON/lifecycle assertions");
 }
}
