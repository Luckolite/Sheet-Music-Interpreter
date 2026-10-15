// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Bounded optical candidates; callers must independently prove staff and musical anchors. */
final class PrintedPedalGlyphs {
    record Mark(int kind, int left, int top, int right, int bottom, float score) {}

    private record Part(int l, int t, int r, int b, int ink) {}

    private final PortableOrnamentGlyphs templates = new PortableOrnamentGlyphs();

    PrintedPedalGlyphs(byte[] down, int dw, int dh, byte[] up, int uw, int uh) {
        templates.add(down, dw, dh, 1, false);
        templates.add(up, uw, uh, 2, false);
    }

    static PrintedPedalGlyphs load(java.io.InputStream stream) throws java.io.IOException {
        var input = new java.io.DataInputStream(Objects.requireNonNull(stream));
        if (input.readInt() != 0x50444731 || input.readInt() != 2)
            throw new java.io.IOException("Invalid pedal template header");
        byte[][] pixels = new byte[2][];
        int[] widths = new int[2], heights = new int[2];
        for (int i = 0; i < 2; i++) {
            int kind = input.readInt(), width = input.readInt(), height = input.readInt();
            if (kind != i + 1 || width < 8 || width > 512 || height < 8 || height > 256)
                throw new java.io.IOException("Invalid pedal template dimensions or kind");
            widths[i] = width;
            heights[i] = height;
            pixels[i] = new byte[width * height];
            input.readFully(pixels[i]);
        }
        if (input.read() != -1) throw new java.io.IOException("Trailing pedal template bytes");
        return new PrintedPedalGlyphs(
                pixels[0], widths[0], heights[0], pixels[1], widths[1], heights[1]);
    }

    record OwnedMark(Mark mark, PlayingTechniqueDetector.Staff staff) {}

    List<OwnedMark> detect(
            List<MeasureRegion> measures,
            List<PlayingTechniqueDetector.Staff> staffs,
            byte[] gray,
            int width,
            int height) {
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != (long) width * height
                || staffs.size() > 512) return List.of();
        var result = new ArrayList<OwnedMark>();
        for (var staff : staffs) {
            float gap = staff.gap();
            if (!Float.isFinite(gap)
                    || gap < 6
                    || gap > Math.min(width, height) / 8f
                    || !Float.isFinite(staff.top() + staff.bottom())
                    || staff.top() >= staff.bottom()
                    || staff.index() < 0
                    || staff.count() <= staff.index()) continue;
            if (StaffPitchTrack.forPrintedSlurs(
                            gray, width, height, staff.top(), staff.bottom(), gap)
                    == null) continue;
            float center = (staff.top() + staff.bottom()) * .5f / height, left = 1, right = 0;
            for (var measure : measures)
                if (center >= measure.top() && center <= measure.bottom()) {
                    left = Math.min(left, measure.left());
                    right = Math.max(right, measure.right());
                }
            if (right <= left) continue;
            int top = Math.max(0, (int) (staff.bottom() + gap * .6f)),
                    bottom = Math.min(height, (int) Math.ceil(staff.bottom() + gap * 8));
            for (var other : staffs)
                if (other.top() > staff.bottom())
                    bottom = Math.min(bottom, (int) (other.top() - gap * .6f));
            int x0 = Math.max(0, (int) (left * width - gap * 2)),
                    x1 = Math.min(width, (int) Math.ceil(right * width + gap * 2));
            for (var mark : detect(gray, width, height, x0, top, x1, bottom, gap))
                result.add(new OwnedMark(mark, staff));
        }
        return List.copyOf(result);
    }

    List<Mark> detect(
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            float gap) {
        if (gray == null
                || gray.length != (long) width * height
                || width < 1
                || height < 1
                || !Float.isFinite(gap)
                || gap < 6
                || left < 0
                || top < 0
                || right > width
                || bottom > height
                || left >= right
                || top >= bottom) return List.of();
        int w = right - left, h = bottom - top;
        if ((long) w * h > 2000000) return List.of();
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        var parts = new ArrayList<Part>();
        for (int seed = 0; seed < seen.length; seed++) {
            if (seen[seed] || (gray[(top + seed / w) * width + left + seed % w] & 255) > 175)
                continue;
            int take = 0, count = 1, l = w, r = 0, t = h, b = 0;
            queue[0] = seed;
            seen[seed] = true;
            while (take < count) {
                int n = queue[take++], x = n % w, y = n / w;
                l = Math.min(l, x);
                r = Math.max(r, x);
                t = Math.min(t, y);
                b = Math.max(b, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                        int k = yy * w + xx;
                        if (!seen[k] && (gray[(top + yy) * width + left + xx] & 255) <= 175) {
                            seen[k] = true;
                            queue[count++] = k;
                        }
                    }
            }
            if (count >= 2 && r - l < gap * 5 && b - t < gap * 3)
                parts.add(new Part(left + l, top + t, left + r + 1, top + b + 1, count));
            if (parts.size() > 4096) return List.of();
        }
        var marks = new ArrayList<Mark>();
        boolean[] grouped = new boolean[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            if (grouped[i]) continue;
            var group = new ArrayList<Integer>();
            group.add(i);
            grouped[i] = true;
            for (int p = 0; p < group.size(); p++) {
                var a = parts.get(group.get(p));
                for (int j = 0; j < parts.size(); j++) {
                    if (grouped[j]) continue;
                    var b = parts.get(j);
                    int dx = Math.max(0, Math.max(a.l - b.r, b.l - a.r)),
                            dy = Math.max(0, Math.max(a.t - b.b, b.t - a.b));
                    if (dx <= gap * .65f && dy <= gap * .45f) {
                        grouped[j] = true;
                        group.add(j);
                    }
                }
            }
            int l = width, r = 0, t = height, b = 0;
            for (int id : group) {
                var p = parts.get(id);
                l = Math.min(l, p.l);
                r = Math.max(r, p.r);
                t = Math.min(t, p.t);
                b = Math.max(b, p.b);
            }
            if (b - t < gap * .8f || b - t > gap * 3 || r - l < gap * .7f || r - l > gap * 5)
                continue;
            var match =
                    templates.templateMatch(
                            gray, width, new PortableNoteOrnaments.Bounds(l, t, r, b));
            // Compensate only the antialiased component rim, never relocate the symbol.
            if (match.score() >= .45f) {
                int inset = Math.min(3, Math.max(1, Math.round(gap * .18f)));
                for (int dl = 0; dl <= inset; dl++)
                    for (int dt = 0; dt <= inset; dt++)
                        for (int dr = 0; dr <= inset; dr++)
                            for (int db = 0; db <= inset; db++) {
                                if (r - l - dl - dr < gap * .7f || b - t - dt - db < gap * .8f)
                                    continue;
                                var aligned =
                                        templates.templateMatch(
                                                gray,
                                                width,
                                                new PortableNoteOrnaments.Bounds(
                                                        l + dl, t + dt, r - dr, b - db));
                                if (aligned.kind() == match.kind()
                                        && aligned.score() > match.score()) match = aligned;
                            }
            }
            if (match.kind() > 0 && match.score() >= .45f && match.score() < .60f) {
                var recovered = contrastMatch(gray, width, l, t, r, b);
                if (recovered.kind() == match.kind()
                        && recovered.score() >= .65f
                        && recovered.margin() >= .10f) match = recovered;
            }
            if (match.kind() > 0 && match.score() >= .60f)
                marks.add(new Mark(match.kind(), l, t, r, b, match.score()));
        }
        return List.copyOf(marks);
    }

    /** A bounded, already shape-supported faded symbol may recover local paper/ink contrast. */
    private PortableOrnamentGlyphs.Match contrastMatch(
            byte[] gray, int width, int left, int top, int right, int bottom) {
        int w = right - left, h = bottom - top, count = w * h;
        int[] tones = new int[256];
        for (int y = top; y < bottom; y++)
            for (int x = left; x < right; x++) tones[gray[y * width + x] & 255]++;
        int low = 255, high = 255, total = 0;
        for (int v = 0; v < 256; v++) {
            total += tones[v];
            if (total >= Math.max(4, count * .05)) {
                low = v;
                break;
            }
        }
        total = 0;
        for (int v = 0; v < 256; v++) {
            total += tones[v];
            if (total >= count * .9) {
                high = v;
                break;
            }
        }
        if (low > 175 || high < 200 || high - low < 80)
            return new PortableOrnamentGlyphs.Match(0, 0, 0);
        byte[] normalized = new byte[count];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                normalized[y * w + x] =
                        (byte)
                                Math.max(
                                        0,
                                        Math.min(
                                                255,
                                                Math.round(
                                                        ((gray[(top + y) * width + left + x] & 255)
                                                                        - low)
                                                                * 255f
                                                                / (high - low))));
        return templates.templateMatch(normalized, w, new PortableNoteOrnaments.Bounds(0, 0, w, h));
    }
}
