// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import static io.github.luckolite.interpreter.ScoreBeamSource.*;

/** Original synthetic topology fixtures: no score scans, transcription, or private gold. */
public final class ScoreBeamSourceProceduralTest {
    private static int checks;
    private static Anchor a(int page, int note) { return new Anchor(page, note); }
    private static Column c(int... notes) {
        return new Column(Arrays.stream(notes).mapToObj(n -> a(0, n)).toList());
    }
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Check " + checks);
    }
    private static void rejects(Runnable action) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection " + checks);
    }
    public static void main(String[] args) {
        Group group = new Group("synthetic", List.of(c(0,1),c(2),c(3),c(4)),
                List.of(3,1,5), List.of(new Hook(1,3,Direction.FORWARD)));
        ScoreBeamSource source = capture(0,6,List.of(group),List.of(c(5)));
        check(source.resolve().groups().get(0).bridgeMask(0)==3);
        check(source.resolve().groups().get(0).bridgeMask(2)==5); // sparse levels accepted
        check(source.resolve().detachedColumns().get(0).owners().equals(List.of(5)));
        var partial = source.edited(List.of(1,2,3,4,5)).resolve().groups().get(0);
        check(partial.columns().get(0).owners().equals(List.of(0)));
        check(partial.bridgeMask(0)==3); // chord member omission retains stem
        var missing = source.edited(List.of(0,1,3,4,5)).resolve().groups().get(0);
        check(missing.columns().size()==4);
        check(!missing.columns().get(1).usable());
        check(missing.bridgeMask(0)==0 && missing.bridgeMask(1)==0 && missing.bridgeMask(2)==5);
        check(missing.source().bridges().equals(List.of(3,1,5)));
        check(missing.engravingHooks().isEmpty());
        var ambiguous = source.edited(List.of(0,1,2,2,3,4,5,-1)).resolve().groups().get(0);
        check(ambiguous.columns().get(1).ambiguous());
        check(ambiguous.bridgeMask(0)==0 && ambiguous.bridgeMask(1)==0);
        check(ambiguous.engravingHooks().isEmpty());
        check(source.edited(List.of(-1)).noteOrigins().equals(List.of(Optional.empty())));
        check(source.engravingColumns(List.of(4,3,2,1,0)).groups().get(0).columns().get(0).owners().equals(List.of(4,3)));
        var next = capture(1,2,List.of(new Group("next",List.of(new Column(List.of(a(1,0))),
                new Column(List.of(a(1,1)))),List.of(1),List.of())),List.of());
        var joined = join(List.of(source,next));
        check(joined.noteOrigins().get(6).get().equals(a(1,0)));
        check(joined.resolve().groups().get(1).columns().get(0).owners().equals(List.of(6)));
        check(source.onPage(4).noteOrigins().get(2).get().equals(a(4,2)));
        check(source.onPage(4).groups().get(0).columns().get(0).members().get(0).equals(a(4,0)));
        check(empty(4).groups().isEmpty() && empty(4).noteOrigins().stream().allMatch(Optional::isEmpty));
        rejects(() -> source.validateNoteCount(5));
        rejects(() -> empty(-1));
        rejects(() -> new Column(List.of(a(0,0),a(0,0))));
        rejects(() -> new Group("",group.columns(),group.bridges(),group.hooks()));
        rejects(() -> new Group("bad",List.of(c(0),c(1)),List.of(2),List.of()));
        rejects(() -> new Group("bad",List.of(c(0),c(1)),List.of(257),List.of()));
        rejects(() -> new Group("bad",List.of(c(0),c(1)),List.of(),List.of()));
        rejects(() -> new Group("bad",List.of(c(0),c(1)),List.of(1),List.of(new Hook(0,1,Direction.FORWARD))));
        rejects(() -> new Group("bad",List.of(c(0),c(1)),List.of(1),List.of(new Hook(0,2,Direction.FORWARD),new Hook(0,2,Direction.BACKWARD))));
        rejects(() -> new Group("bad",List.of(c(0),c(1)),List.of(1),List.of(new Hook(2,2,Direction.FORWARD))));
        rejects(() -> new ScoreBeamSource(List.of(group,group),List.of(),source.noteOrigins()));
        rejects(() -> new ScoreBeamSource(List.of(group),List.of(c(0)),source.noteOrigins()));
        rejects(() -> capture(1,6,List.of(group),List.of()));
        rejects(() -> capture(0,4,List.of(group),List.of()));
        rejects(() -> source.edited(List.of(6)));
        rejects(() -> source.edited(List.of(-2)));
        rejects(() -> source.onPage(4).onPage(5));
        // Shared source facts coalesce; repeated current origins remain ambiguous.
        var repeatedViews = join(List.of(source, source));
        check(repeatedViews.groups().equals(source.groups()));
        check(repeatedViews.detachedColumns().equals(source.detachedColumns()));
        check(repeatedViews.noteOrigins().size() == source.noteOrigins().size() * 2);
        var repeatedGroup = repeatedViews.resolve().groups().get(0);
        check(repeatedGroup.columns().stream().allMatch(c -> c.ambiguous() && !c.usable()));
        for (int bridge = 0; bridge < repeatedGroup.source().bridges().size(); bridge++)
            check(repeatedGroup.bridgeMask(bridge) == 0);
        check(repeatedGroup.engravingHooks().isEmpty());
        var mutable = new ArrayList<Anchor>(List.of(a(0,9)));
        var immutable = new Column(mutable); mutable.clear();
        check(immutable.members().size()==1);
        // Page overlay constructors/copy methods preserve topology and reject stale note counts.
        var page = new ScorePageInterpretation(List.of(), List.of());
        check(page.beamSource().noteOrigins().isEmpty());
        var emptySource = capture(0,0,List.of(),List.of());
        page = page.withBeamSource(emptySource);
        check(page.withOpeningPickup(false).beamSource()==emptySource);
        check(page.withMultiRestGroups(List.of()).beamSource()==emptySource);
        check(page.withPlaybackDirections(List.of()).beamSource()==emptySource);
        check(page.withExpressiveEvents(List.of()).beamSource()==emptySource);
        check(page.withSlurSource(ScoreSlurSource.empty(0)).beamSource()==emptySource);
        check(page.withTremoloSource(ScoreTremoloSource.empty(0)).beamSource()==emptySource);
        final var finalPage = page;
        rejects(() -> finalPage.withBeamSource(empty(1)));
        check(page.withNotes(List.of(),List.of()).beamSource().noteOrigins().isEmpty());
        System.out.println("PASS " + checks + " original procedural assertions");
    }
 @org.junit.Test public void originalControls() throws Exception { main(new String[0]); }
}
