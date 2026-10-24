// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import static io.github.luckolite.interpreter.ScoreBeamSource.*;
/** Original synthetic lifecycle regression; no source score material. */
public final class ScoreBeamRetentionProceduralTest {
 private static int count;
 private static void check(boolean x) { count++; if(!x) throw new AssertionError("Check "+count); }
 private static Column c(int n) { return new Column(List.of(new Anchor(8,n))); }
 public static void main(String[] args) {
  var notes=List.of(new ScoreNoteEvent(0,.1f,2,0,1,.5f,false,0,1),new ScoreNoteEvent(0,.4f,3,0,1,.5f,false,0,1),new ScoreNoteEvent(2,.1f,4,0,1,.5f,false,0,1),new ScoreNoteEvent(2,.4f,5,0,1,.5f,false,0,1));
  var beam=capture(8,4,List.of(new Group("first",List.of(c(0),c(1)),List.of(3),List.of()),new Group("last",List.of(c(2),c(3)),List.of(1),List.of())),List.of());
  var m=new MeasureRegion(.1f,.9f,.1f,.3f);
  var source=new ScorePageInterpretation(List.of(m,m,m),notes).withBeamSource(beam);
  var remapped=CourtesyMeasureRemap.remove(source,1);
  check(remapped.beamSource()==beam);
  check(remapped.notes().get(2).measureIndex()==1);
  check(remapped.beamSource().resolve().groups().get(1).columns().get(0).owners().equals(List.of(2)));
  var identity=ScoreNavigationProjection.project(source,ScoreNavigationPlan.create(3,List.of()),new ScoreNavigationProjection.Defaults(0,120,4,4));
  check(identity==source);
  var directions=List.of(new ScorePlaybackDirection(0,ScorePlaybackDirection.Kind.REPEAT_START),new ScorePlaybackDirection(3,ScorePlaybackDirection.Kind.REPEAT_END));
  var plan=ScoreNavigationPlan.create(3,directions);
  check(plan.navigationApplied());
  var repeated=ScoreNavigationProjection.projectWithOwnership(source,plan,new ScoreNavigationProjection.Defaults(0,120,4,4));
  check(repeated.score().notes().size()==8);
  check(repeated.score().beamSource().noteOrigins().size()==8);
  check(repeated.score().beamSource().groups().equals(beam.groups()));
  check(repeated.score().beamSource().noteOrigins().get(0).equals(repeated.score().beamSource().noteOrigins().get(4)));
  check(repeated.score().beamSource().noteOrigins().get(0).get().equals(new Anchor(8,0)));
  check(repeated.score().beamSource().resolve().groups().get(0).columns().get(0).ambiguous());
  check(repeated.score().beamSource().resolve().groups().get(0).bridgeMask(0)==0);
  check(repeated.owners().get(0).sourceIndex()==0 && repeated.owners().get(4).sourceIndex()==0);
  check(repeated.owners().get(0).occurrence()!=repeated.owners().get(4).occurrence());
  var selected=source.withNotes(List.of(notes.get(3),notes.get(2)),List.of(3,2));
  check(selected.beamSource().groups().size()==2);
  check(selected.beamSource().resolve().groups().get(0).bridgeMask(0)==0);
  check(selected.beamSource().resolve().groups().get(1).columns().get(0).owners().equals(List.of(1)));
  check(selected.beamSource().resolve().groups().get(1).bridgeMask(0)==1);
  check(source.beamSource().resolve().groups().get(0).bridgeMask(0)==3);
  System.out.println("PASS "+count+" original retention lifecycle assertions");
 }
 @org.junit.Test public void originalProceduralControls() throws Exception { main(new String[0]); }
}
