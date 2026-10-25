// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Literal printed beam topology, independent of duration inference and performance timing. */
public record ScoreBeamSource(
        List<Group> groups, List<Column> detachedColumns, List<Optional<Anchor>> noteOrigins) {
    public static final int MAX_NOTES = 1_000_000;
    public static final int MAX_COLUMNS = 1_000_000;

    public record Anchor(int pageIndex, int noteIndex) {
        public Anchor {
            if (pageIndex < 0 || noteIndex < 0 || noteIndex >= MAX_NOTES)
                throw new IllegalArgumentException("Invalid physical beam anchor");
        }
    }

    /** All printed heads sharing one literal stem/onset; no onset proximity heuristic. */
    public record Column(List<Anchor> members) {
        public Column {
            members = List.copyOf(members);
            if (members.isEmpty() || members.size() > 128
                    || new HashSet<>(members).size() != members.size())
                throw new IllegalArgumentException("Invalid beam column members");
            int page = members.get(0).pageIndex();
            for (Anchor member : members)
                if (member.pageIndex() != page)
                    throw new IllegalArgumentException("Beam column spans physical pages");
        }
    }

    public enum Direction { FORWARD, BACKWARD }

    public record Hook(int columnIndex, int level, Direction direction) {
        public Hook {
            Objects.requireNonNull(direction);
            if (columnIndex < 0 || columnIndex >= MAX_COLUMNS || level < 1 || level > 8)
                throw new IllegalArgumentException("Invalid printed beam hook");
        }
    }

    public record Group(String id, List<Column> columns, List<Integer> bridges, List<Hook> hooks) {
        public Group {
            Objects.requireNonNull(id);
            columns = List.copyOf(columns);
            bridges = List.copyOf(bridges);
            hooks = List.copyOf(hooks);
            if (id.isBlank() || id.length() > 256 || columns.size() < 2
                    || columns.size() > MAX_COLUMNS || bridges.size() != columns.size() - 1
                    || hooks.size() > (long) columns.size() * 8)
                throw new IllegalArgumentException("Invalid printed beam group");
            int page = columns.get(0).members().get(0).pageIndex();
            for (Column column : columns)
                if (column.members().get(0).pageIndex() != page)
                    throw new IllegalArgumentException("Beam group spans physical pages");
            for (int mask : bridges)
                if (mask < 1 || mask > 255 || (mask & 1) == 0)
                    throw new IllegalArgumentException("Invalid beam bridge levels");
            Set<Anchor> anchors = new HashSet<>();
            for (Column column : columns)
                for (Anchor anchor : column.members)
                    if (!anchors.add(anchor))
                        throw new IllegalArgumentException("Duplicate beam column ownership");
            Set<Long> hookLevels = new HashSet<>();
            for (Hook hook : hooks) {
                int c = hook.columnIndex, bit = 1 << (hook.level - 1);
                if (c >= columns.size() || !hookLevels.add((long)c * 8 + hook.level - 1)
                        || c > 0 && (bridges.get(c - 1) & bit) != 0
                        || c < bridges.size() && (bridges.get(c) & bit) != 0)
                    throw new IllegalArgumentException("Conflicting beam hook");
            }
        }
    }

    public ScoreBeamSource {
        groups = List.copyOf(groups);
        detachedColumns = List.copyOf(detachedColumns);
        noteOrigins = List.copyOf(noteOrigins);
        if (noteOrigins.size() > MAX_NOTES || groups.size() > MAX_COLUMNS)
            throw new IllegalArgumentException("Too many beam source records");
        Set<String> ids = new HashSet<>();
        Set<Anchor> anchors = new HashSet<>();
        long count = detachedColumns.size();
        for (Group group : groups) {
            if (!ids.add(group.id)) throw new IllegalArgumentException("Duplicate beam group ID");
            count += group.columns.size();
            addColumns(anchors, group.columns);
        }
        if (count > MAX_COLUMNS) throw new IllegalArgumentException("Too many beam columns");
        addColumns(anchors, detachedColumns);
        // Repeated retained origins deliberately remain ambiguous at resolution time.
    }

    private static void addColumns(Set<Anchor> anchors, List<Column> columns) {
        for (Column column : columns)
            for (Anchor anchor : column.members)
                if (!anchors.add(anchor))
                    throw new IllegalArgumentException("Ambiguous physical beam ownership");
    }

    /** Unknown notation: an empty source is never evidence that printed beams are absent. */
    public static ScoreBeamSource empty(int noteCount) {
        checkCount(noteCount);
        return new ScoreBeamSource(List.of(), List.of(), Collections.nCopies(noteCount, Optional.empty()));
    }

    private static void checkCount(int count) {
        if (count < 0 || count > MAX_NOTES) throw new IllegalArgumentException("Invalid beam note count");
    }

    public void validateNoteCount(int noteCount) {
        if (noteCount != noteOrigins.size())
            throw new IllegalArgumentException("Beam identities do not match retained score notes");
    }

    public static ScoreBeamSource capture(int pageIndex, int noteCount,
            List<Group> groups, List<Column> detachedColumns) {
        checkCount(noteCount);
        if (pageIndex < 0) throw new IllegalArgumentException("Invalid physical beam page");
        ScoreBeamSource validated = new ScoreBeamSource(groups, detachedColumns, List.of());
        List<Column> columns = new ArrayList<>(detachedColumns);
        for (Group group : validated.groups) columns.addAll(group.columns);
        for (Column column : columns)
            for (Anchor anchor : column.members)
                if (anchor.pageIndex != pageIndex || anchor.noteIndex >= noteCount)
                    throw new IllegalArgumentException("Beam endpoint outside captured page");
        List<Optional<Anchor>> origins = new ArrayList<>(noteCount);
        for (int n = 0; n < noteCount; n++) origins.add(Optional.of(new Anchor(pageIndex, n)));
        return new ScoreBeamSource(groups, detachedColumns, origins);
    }

    /** -1 denotes a new note. Repeated source owners keep their ambiguity, without aliases. */
    public ScoreBeamSource edited(List<Integer> sourceOwners) {
        if (sourceOwners.size() > MAX_NOTES) throw new IllegalArgumentException("Too many edit owners");
        List<Optional<Anchor>> origins = new ArrayList<>(sourceOwners.size());
        for (Integer owner : sourceOwners) {
            if (owner == null || owner < -1 || owner >= noteOrigins.size())
                throw new IllegalArgumentException("Invalid retained beam edit owner");
            origins.add(owner == -1 ? Optional.empty() : noteOrigins.get(owner));
        }
        return new ScoreBeamSource(groups, detachedColumns, origins);
    }

    /** Joins retained page views, coalescing identical shared facts without rewriting ownership. */
    public static ScoreBeamSource join(List<ScoreBeamSource> sources) {
        Map<String, Group> groups = new LinkedHashMap<>();
        Set<Column> detached = new LinkedHashSet<>();
        List<Optional<Anchor>> origins = new ArrayList<>();
        long total = 0;
        for (ScoreBeamSource source : sources) {
            total += source.noteOrigins.size();
            if (total > MAX_NOTES) throw new IllegalArgumentException("Too many joined beam notes");
            for (Group group : source.groups) {
                Group previous = groups.putIfAbsent(group.id, group);
                if (previous != null && !previous.equals(group))
                    throw new IllegalArgumentException("Conflicting beam group identity");
            }
            detached.addAll(source.detachedColumns);
            origins.addAll(source.noteOrigins);
        }
        return new ScoreBeamSource(List.copyOf(groups.values()), List.copyOf(detached), origins);
    }

    /** Assign page-zero local recognition to its physical page; normalize group IDs without prefix growth. */
    public ScoreBeamSource onPage(int pageIndex) {
        if (pageIndex < 0) throw new IllegalArgumentException("Invalid physical beam page");
        java.util.function.Function<Anchor, Anchor> map = a -> {
            if (a.pageIndex != 0) throw new IllegalArgumentException("Nonlocal physical beam anchor");
            return new Anchor(pageIndex, a.noteIndex);
        };
        java.util.function.Function<Column, Column> column = c -> new Column(c.members.stream().map(map).toList());
        List<Group> relocated = groups.stream().map(g -> new Group("beam-page:" + pageIndex + ":"
                        + g.columns.stream().flatMap(c -> c.members.stream()).mapToInt(Anchor::noteIndex).min().orElseThrow(),
                g.columns.stream().map(column).toList(), g.bridges, g.hooks)).toList();
        return new ScoreBeamSource(relocated, detachedColumns.stream().map(column).toList(),
                noteOrigins.stream().map(o -> o.map(map)).toList());
    }

    /** owners indexes current note slots; source retains missing chord heads and empty columns. */
    public record ResolvedColumn(Column source, List<Integer> owners, boolean ambiguous) {
        public ResolvedColumn { owners = List.copyOf(owners); }
        public boolean usable() { return !ambiguous && !owners.isEmpty(); }
    }

    public record ResolvedGroup(Group source, List<ResolvedColumn> columns) {
        public ResolvedGroup { columns = List.copyOf(columns); }
        /** Original positions only: never bridge across an omitted/ambiguous column. */
        public int bridgeMask(int index) {
            Objects.checkIndex(index, source.bridges.size());
            return columns.get(index).usable() && columns.get(index + 1).usable()
                    ? source.bridges.get(index) : 0;
        }
        public List<Hook> engravingHooks() {
            return source.hooks.stream().filter(h -> columns.get(h.columnIndex).usable()).toList();
        }
    }

    public record Resolution(List<ResolvedGroup> groups, List<ResolvedColumn> detachedColumns) {
        public Resolution {
            groups = List.copyOf(groups);
            detachedColumns = List.copyOf(detachedColumns);
        }
    }

    public Resolution resolve() {
        Map<Anchor, List<Integer>> owners = new HashMap<>();
        for (int n = 0; n < noteOrigins.size(); n++) {
            Optional<Anchor> origin = noteOrigins.get(n);
            if (origin.isPresent()) owners.computeIfAbsent(origin.get(), k -> new ArrayList<>()).add(n);
        }
        java.util.function.Function<Column, ResolvedColumn> resolve = column -> {
            List<Integer> found = new ArrayList<>();
            boolean ambiguous = false;
            for (Anchor anchor : column.members) {
                List<Integer> matches = owners.getOrDefault(anchor, List.of());
                if (matches.size() > 1) ambiguous = true;
                else found.addAll(matches);
            }
            return new ResolvedColumn(column, found, ambiguous);
        };
        return new Resolution(groups.stream().map(g -> new ResolvedGroup(g,
                g.columns.stream().map(resolve).toList())).toList(),
                detachedColumns.stream().map(resolve).toList());
    }

    /** Arrangement filtering uses an explicit owner map; omission never renumbers source columns. */
    public Resolution engravingColumns(List<Integer> sourceOwners) { return edited(sourceOwners).resolve(); }
}
