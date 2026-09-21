// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import org.junit.Test;import java.util.*;import static org.junit.Assert.*;
public class EmptyMeasureTabTest {
 private List<TablatureDecoder.Staff> tabs(float paired){return List.of(new TablatureDecoder.Staff(140,20,paired,List.of(new TablatureDecoder.Fret(100,140,0,0),new TablatureDecoder.Fret(300,160,1,3)),List.of(20f,250f,480f)));}
 @Test public void numberOnlyMeasuresDoNotDiscardStandaloneFrets(){
  var empty=new ScorePageInterpretation(List.of(new MeasureRegion(.1f,.9f,.3f,.7f)),List.of());
  var result=TablatureDecoder.apply(empty,tabs(-1),500,400);
  assertEquals(2,result.notes().size());assertEquals(2,result.measures().size());assertEquals(0,result.notes().get(0).measureIndex());assertEquals(1,result.notes().get(1).measureIndex());
 }
 @Test public void actualNotationIsPreserved(){
  var note=new ScoreNoteEvent(0,.2f,0,0,1,.2f,false,0,0,0,1);
  var score=new ScorePageInterpretation(List.of(new MeasureRegion(.1f,.9f,.1f,.3f)),List.of(note));
  assertEquals(score,TablatureDecoder.apply(score,tabs(-1),500,400));
 }
 @Test public void pairedEmptyStaffDoesNotInventRhythm(){
  var score=new ScorePageInterpretation(List.of(new MeasureRegion(.1f,.9f,.1f,.3f)),List.of());
  assertEquals(score,TablatureDecoder.apply(score,tabs(40),500,400));
 }

}
