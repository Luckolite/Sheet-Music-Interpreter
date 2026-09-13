// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Adapted from Music Sheets: standalone package and platform-independent diagnostics.
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Recognizes printed rest counts such as the 4 above a multi-measure rest. */
final class MultiMeasureRestDetector {
    private MultiMeasureRestDetector() { }

    record RestBarCandidate(int measureIndex, MeasureRegion region) { }

    static List<MeasureNumberReconciler.NumberToken> detect(byte[] labels, byte[] gray,
                                                             int width, int height,
                                                             List<MeasureRegion> measures,
                                                             List<MeasureNumberReconciler.NumberToken> tokens) {
        if (labels == null || gray == null || width <= 0 || height <= 0
                || labels.length != width * height || gray.length != labels.length
                || measures == null || measures.isEmpty() || tokens == null)
            return List.of();
        List<RestBarCandidate> restBars = candidateRestBars(labels, gray, width, height, measures);
        if (restBars.isEmpty()) return List.of();

        List<MeasureNumberReconciler.NumberToken> readings = new ArrayList<>(tokens);
        for (RestBarCandidate candidate : restBars) {
            var count = standaloneCount(gray, width, height, candidate);
            if (count != null && aboveStaff(count, labels, gray, width, height, candidate.region())) readings.add(count);
        }
        List<MeasureNumberReconciler.NumberToken> result = new ArrayList<>();
        boolean[] claimedMeasures = new boolean[measures.size()];
        for (MeasureNumberReconciler.NumberToken token : readings) {
            // Counts above multi-measure rests are normally 2-32. Larger values are overwhelmingly
            // likely to be a tempo, copyright year, or printed system number.
            if (token.value() < 2 || token.value() > 32) continue;
            float centerX = (token.left() + token.right()) * .5f;
            float centerY = (token.top() + token.bottom()) * .5f;
            int measureIndex = containingRestBar(restBars, centerX, centerY);
            if (measureIndex < 0 || claimedMeasures[measureIndex]) continue;
            claimedMeasures[measureIndex] = true;
            result.add(token);
        }
        return List.copyOf(result);
    }

    /** Returns note-free windows containing the heavy bar used for a multi-rest. */
    static List<RestBarCandidate> candidateRestBars(byte[] labels, byte[] gray, int width,
                                                     int height, List<MeasureRegion> measures) {
        if (labels == null || gray == null || width <= 0 || height <= 0
                || labels.length != width * height || gray.length != labels.length
                || measures == null || measures.isEmpty()) return List.of();
        int[] noteheadPixels = new int[measures.size()];
        List<MeasureRegion> noteRegions = new ArrayList<>();
        List<Integer> writtenCounts = new ArrayList<>();
        for (int index = 0; index < measures.size(); index++) {
            MeasureRegion parent = measures.get(index);
            float extent = parent.bottom() - parent.top();
            float top = Math.max(0, parent.top() - extent * .85f);
            float bottom = Math.min(1, parent.bottom() + extent * .85f);
            // Ledger heads belong to the written measure even when its region
            // contains only the staff. Stop at the gap to an adjacent staff.
            for (MeasureRegion other : measures) {
                if (other.right() <= parent.left() || other.left() >= parent.right()) continue;
                if (other.bottom() < parent.top()) top = Math.max(top, (other.bottom() + parent.top()) * .5f);
                if (other.top() > parent.bottom()) bottom = Math.min(bottom, (other.top() + parent.bottom()) * .5f);
            }
            MeasureRegion noteRegion = new MeasureRegion(parent.left(), parent.right(), top, bottom);
            noteRegions.add(noteRegion);
            noteheadPixels[index] = countLabel(labels, width, height, noteRegion,
                    OmrMeasurePostProcessor.NOTEHEAD);
            if (noteheadPixels[index] > 0) writtenCounts.add(noteheadPixels[index]);
        }
        writtenCounts.sort(Integer::compare);
        int ordinaryWrittenInk = writtenCounts.isEmpty() ? 0
                : writtenCounts.get(writtenCounts.size() / 2);
        int noteFreeLimit = Math.max(6, ordinaryWrittenInk / 14);
        List<RestBarCandidate> result = new ArrayList<>();
        for (int index = 0; index < measures.size(); index++) {
            MeasureRegion parent = measures.get(index);
            // Segmentation can classify a printed rest count as a notehead. Exclude
            // only that verified glyph above the staff, never other heads in the measure.
            MeasureNumberReconciler.NumberToken countGlyph = null;
            if (noteheadPixels[index] > noteFreeLimit && hasThickHorizontalBar(gray, width, height, parent)) {
                MeasureNumberReconciler.NumberToken count = standaloneCount(gray, width, height,
                        new RestBarCandidate(index, parent));
                if (count != null && aboveStaff(count, labels, gray, width, height, parent))
                    countGlyph = count;
            }
            // A real multi-measure rest owns an otherwise note-free visual measure. Looking only
            // at the sliding window is not enough: an ordinary written measure can contain a
            // long beam or staff-line segment in one locally empty window, and a nearby time
            // signature/OCR digit then expands that measure several times. Require the complete
            // parent measure to be note-free before treating any local heavy bar as a rest.
            if (noteheadPixels[index] - countGlyphHeads(countGlyph, labels, width, height, noteRegions.get(index)) > noteFreeLimit) continue;
            int parentLeft = clamp(Math.round(parent.left() * width), 0, width - 1);
            int parentRight = clamp(Math.round(parent.right() * width), parentLeft, width - 1);
            int parentTop = clamp(Math.round(parent.top() * height), 0, height - 1);
            int parentBottom = clamp(Math.round(parent.bottom() * height), parentTop, height - 1);
            int parentWidth = parentRight - parentLeft + 1;
            int parentHeight = parentBottom - parentTop + 1;
            int windowWidth = Math.min(parentWidth,
                    Math.max(48, Math.round(parentHeight * 2.4f)));
            int step = Math.max(1, windowWidth / 2);
            for (int windowLeft = parentLeft; windowLeft <= parentRight; windowLeft += step) {
                int windowRight = Math.min(parentRight, windowLeft + windowWidth - 1);
                if (windowRight - windowLeft + 1 < Math.min(60, windowWidth)) break;
                MeasureRegion window = new MeasureRegion(windowLeft / (float) width,
                        windowRight / (float) width, parent.top(), parent.bottom());
                int localHeads = countLabel(labels, width, height, window,
                        OmrMeasurePostProcessor.NOTEHEAD) - countGlyphHeads(countGlyph, labels, width, height, window);
                boolean thickBar = hasThickHorizontalBar(gray, width, height, window);
                if (localHeads <= noteFreeLimit && thickBar)
                    addOrMerge(result, new RestBarCandidate(index, window));
                if (windowRight == parentRight) break;
                if (windowLeft + windowWidth > parentRight)
                    windowLeft = Math.max(windowLeft, parentRight - windowWidth - step);
            }
        }
        return List.copyOf(result);
    }

    static List<Integer> candidateMeasureIndexes(byte[] labels, byte[] gray, int width, int height,
                                                  List<MeasureRegion> measures) {
        List<Integer> result = new ArrayList<>();
        for (RestBarCandidate candidate : candidateRestBars(labels, gray, width, height, measures))
            if (!result.contains(candidate.measureIndex)) result.add(candidate.measureIndex);
        return List.copyOf(result);
    }

    /**
     * ML Kit commonly ignores an isolated one-character OCR crop. Recognize only the distinctive
     * printed 2/4/8/9 shapes above a heavy bar, including mislabeled count ink.
     */
    static MeasureNumberReconciler.NumberToken standaloneCount(byte[] gray, int width, int height,
                                                                 RestBarCandidate candidate) {
        if (gray == null || gray.length != width * height || candidate == null) return null;
        MeasureRegion region = candidate.region;
        int regionTop = clamp(Math.round(region.top() * height), 0, height - 1);
        int regionBottom = clamp(Math.round(region.bottom() * height), regionTop, height - 1);
        int regionHeight = regionBottom - regionTop + 1;
        int left = clamp(Math.round(region.left() * width), 0, width - 1);
        int right = clamp(Math.round(region.right() * width), left, width - 1);
        int top = clamp(Math.round(regionTop - regionHeight * .95f), 0, height - 1);
        int bottom = clamp(Math.round(regionTop + regionHeight * .30f), top, height - 1);
        int localWidth = right - left + 1, localHeight = bottom - top + 1;
        boolean[] visited = new boolean[localWidth * localHeight];
        int[] stack = new int[visited.length];
        MeasureNumberReconciler.NumberToken best = null;
        float restCenterX = (left + right) * .5f;
        float bestDistance = Float.MAX_VALUE;
        for (int origin = 0; origin < visited.length; origin++) {
            int originX = origin % localWidth, originY = origin / localWidth;
            if (visited[origin]
                    || (gray[(top + originY) * width + left + originX] & 0xff) > 165) continue;
            int stackSize = 0;
            stack[stackSize++] = origin;
            visited[origin] = true;
            int area = 0, minX = right, maxX = left, minY = bottom, maxY = top;
            while (stackSize > 0) {
                int current = stack[--stackSize];
                int localX = current % localWidth, localY = current / localWidth;
                int x = left + localX, y = top + localY;
                area++;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nextX = localX + dx, nextY = localY + dy;
                    if ((dx == 0 && dy == 0) || nextX < 0 || nextX >= localWidth
                            || nextY < 0 || nextY >= localHeight) continue;
                    int next = nextY * localWidth + nextX;
                    if (!visited[next]
                            && (gray[(top + nextY) * width + left + nextX] & 0xff) <= 165) {
                        visited[next] = true;
                        stack[stackSize++] = next;
                    }
                }
            }
            int value = looksLikeEight(gray, width, regionHeight, minX, maxX, minY, maxY) ? 8
                    : looksLikeFour(gray, width, regionHeight, area,
                    minX, maxX, minY, maxY) ? (looksLikeNine(gray, width, regionHeight,
                    minX, maxX, minY, maxY) ? 9 : 4)
                    : looksLikeNine(gray, width, regionHeight, minX, maxX, minY, maxY) ? 9
                    : looksLikeTwo(gray, width, regionHeight, area,
                    minX, maxX, minY, maxY) ? 2 : 0;
            if (value == 0 || !isolatedCountGlyph(gray,width,height,minX,maxX,minY,maxY)) continue;
            float distance = Math.abs((minX + maxX) * .5f - restCenterX);
            // A time-signature 4 can sit at the left edge of the opening measure. The count is
            // centered above the proven heavy rest bar, so evaluate every glyph and retain the
            // closest one rather than returning the first scan-order match.
            if (distance > localWidth * .36f || distance >= bestDistance) continue;
            bestDistance = distance;
            best = new MeasureNumberReconciler.NumberToken(value, minX / (float) width,
                    minY / (float) height, maxX / (float) width, maxY / (float) height);
        }
        return best;
    }

    private static int countGlyphHeads(MeasureNumberReconciler.NumberToken token, byte[] labels,
                                       int width, int height, MeasureRegion region) {
        if (token == null) return 0;
        float left = Math.max(region.left(), token.left()), right = Math.min(region.right(), token.right());
        float top = Math.max(region.top(), token.top()), bottom = Math.min(region.bottom(), token.bottom());
        return left >= right || top >= bottom ? 0 : countLabel(labels, width, height,
                new MeasureRegion(left, right, top, bottom), OmrMeasurePostProcessor.NOTEHEAD);
    }

    private static boolean aboveStaff(MeasureNumberReconciler.NumberToken token, byte[] labels, byte[] gray,
                                      int width, int height, MeasureRegion region) {
        int left = Math.round(region.left() * width), right = Math.min(width - 1, Math.round(region.right() * width));
        for (int y = Math.round(region.top() * height); y < Math.min(height, Math.round(region.bottom() * height)); y++) {
            int ink = 0;
            for (int x = left; x <= right; x++) if (labels[y * width + x] == OmrMeasurePostProcessor.STAFF) ink++;
            if (ink > (right - left) * .55f) return token.bottom() * height < y;
        }
        // The model can omit every staff rule in a silent measure. Require a
        // complete printed five-line group rather than accepting an isolated bar.
        int top=clamp(Math.round(region.top()*height),0,height-1);
        int bottom=clamp(Math.round(region.bottom()*height),top,height-1);
        left=clamp(left,0,width-1);right=clamp(right,left,width-1);
        int w=right-left+1,h=bottom-top+1;
        byte[] crop=new byte[w*h];
        for(int y=0;y<h;y++)System.arraycopy(gray,(top+y)*width+left,crop,y*w,w);
        int first=RawStaffLineDetector.detect(crop,w,h).stream()
                .mapToInt(RawStaffLineDetector.StaffLines::top).min().orElse(-1);
        return first>=0&&token.bottom()*height<top+first;
    }

    /** An eight has two enclosed white bowls stacked vertically; a notehead has only one. */
    private static boolean looksLikeEight(byte[] gray, int width, int regionHeight,
                                          int left, int right, int top, int bottom) {
        int w = right - left + 1, h = bottom - top + 1;
        if (h < regionHeight * .13f || h > regionHeight * .48f || w < h * .4f || w > h * .95f) return false;
        boolean[] visited = new boolean[w * h];
        int[] stack = new int[w * h];
        List<Float> centers = new ArrayList<>();
        for (int origin = 0; origin < visited.length; origin++) {
            if (visited[origin] || (gray[(top + origin / w) * width + left + origin % w] & 255) <= 165) continue;
            int size = 0, area = 0, sumY = 0; boolean edge = false;
            stack[size++] = origin; visited[origin] = true;
            while (size > 0) {
                int current = stack[--size], x = current % w, y = current / w;
                area++; sumY += y; edge |= x == 0 || x == w - 1 || y == 0 || y == h - 1;
                for (int next : new int[]{current - 1, current + 1, current - w, current + w}) {
                    if (next < 0 || next >= visited.length || Math.abs(next % w - x) + Math.abs(next / w - y) != 1
                            || visited[next] || (gray[(top + next / w) * width + left + next % w] & 255) <= 165) continue;
                    visited[next] = true; stack[size++] = next;
                }
            }
            if (!edge && area >= w * h * .035f) centers.add(sumY / (float) area / h);
        }
        return centers.size() == 2 && centers.get(0) < .45f && centers.get(1) > .55f;
    }

    /** Standalone single-digit recognition must not read a letter or one digit from adjacent text. */
    private static boolean isolatedCountGlyph(byte[] gray,int width,int height,int left,int right,int top,int bottom) {
        int h=bottom-top+1,margin=Math.max(2,Math.round(h*.3f));
        for(int direction:new int[]{-1,1}) {
            int ink=0;
            for(int y=Math.max(0,top);y<=Math.min(height-1,bottom);y++)
                for(int dx=1;dx<=margin;dx++) {
                    int x=direction<0?left-dx:right+dx;
                    if(x>=0&&x<width&&(gray[y*width+x]&255)<=165)ink++;
                }
            if(ink>Math.max(2,Math.round(h*h*.035f)))return false;
        }
        return true;
    }

    /** A nine has one upper bowl and a right-hand tail that curves back left below it. */
    private static boolean looksLikeNine(byte[] gray,int width,int regionHeight,
                                         int left,int right,int top,int bottom) {
        int w=right-left+1,h=bottom-top+1;
        if(h<regionHeight*.13f||h>regionHeight*.48f||w<h*.4f||w>h*.95f)return false;
        boolean[] seen=new boolean[w*h];int[] queue=new int[w*h];
        int holes=0;float holeY=0;
        for(int i=0;i<seen.length;i++) {
            if(seen[i]||(gray[(top+i/w)*width+left+i%w]&255)<=165)continue;
            int read=0,size=1,area=0,sumY=0;boolean edge=false;queue[0]=i;seen[i]=true;
            while(read<size) {
                int at=queue[read++],x=at%w,y=at/w;area++;sumY+=y;
                edge|=x==0||x==w-1||y==0||y==h-1;
                for(int next:new int[]{at-1,at+1,at-w,at+w}) {
                    if(next<0||next>=seen.length||seen[next]
                            ||Math.abs(next%w-x)+Math.abs(next/w-y)!=1
                            ||(gray[(top+next/w)*width+left+next%w]&255)<=165)continue;
                    seen[next]=true;queue[size++]=next;
                }
            }
            if(!edge&&area>=w*h*.035f){holes++;holeY=sumY/(float)area/h;}
        }
        if(holes!=1||holeY>.45f)return false;
        int spine=0,bottomWidth=0;
        for(int x=Math.round(w*.55f);x<w;x++) {
            int count=0;
            for(int y=Math.round(h*.3f);y<=Math.round(h*.8f);y++)
                if((gray[(top+y)*width+left+x]&255)<=165)count++;
            spine=Math.max(spine,count);
        }
        for(int y=Math.round(h*.78f);y<h;y++) {
            int first=w,last=-1;
            for(int x=0;x<w;x++)if((gray[(top+y)*width+left+x]&255)<=165){first=Math.min(first,x);last=x;}
            if(first<w*.4f)bottomWidth=Math.max(bottomWidth,last-first+1);
        }
        return spine>=h*.45f&&bottomWidth>=w*.6f;
    }

    /** Kept as a narrow compatibility seam for the original four-shape regression tests. */
    static MeasureNumberReconciler.NumberToken standaloneFour(byte[] gray, int width, int height,
                                                                RestBarCandidate candidate) {
        MeasureNumberReconciler.NumberToken token = standaloneCount(gray, width, height, candidate);
        return token != null && token.value() == 4 ? token : null;
    }

    private static boolean looksLikeFour(byte[] gray, int width, int regionHeight, int area,
                                         int minX, int maxX, int minY, int maxY) {
        int glyphWidth = maxX - minX + 1, glyphHeight = maxY - minY + 1;
        if (glyphHeight < regionHeight * .13f || glyphHeight > regionHeight * .48f
                || glyphWidth < regionHeight * .045f || glyphWidth > regionHeight * .28f
                || glyphWidth < glyphHeight * .30f || glyphWidth > glyphHeight * 1.10f)
            return false;
        float fill = area / (float) (glyphWidth * glyphHeight);
        if (fill < .14f || fill > .66f) return false;
        int[] rows = new int[glyphHeight];
        int[] columns = new int[glyphWidth];
        int upperLeft = 0, lowerLeft = 0;
        for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
            if ((gray[y * width + x] & 0xff) > 165) continue;
            int localX = x - minX, localY = y - minY;
            rows[localY]++;
            columns[localX]++;
            if (localX < glyphWidth * .55f && localY < glyphHeight * .58f) upperLeft++;
            if (localX < glyphWidth * .45f && localY > glyphHeight * .72f) lowerLeft++;
        }
        int rightSpine = 0;
        for (int x = Math.max(0, Math.round(glyphWidth * .52f)); x < glyphWidth; x++)
            rightSpine = Math.max(rightSpine, columns[x]);
        int middleCrossbar = 0;
        for (int y = Math.max(0, Math.round(glyphHeight * .36f));
             y <= Math.min(glyphHeight - 1, Math.round(glyphHeight * .74f)); y++)
            middleCrossbar = Math.max(middleCrossbar, rows[y]);
        int topBar = 0;
        for (int y = 0; y < Math.max(1, Math.round(glyphHeight * .28f)); y++)
            topBar = Math.max(topBar, rows[y]);
        return rightSpine >= glyphHeight * .58f
                && middleCrossbar >= glyphWidth * .50f
                && topBar < glyphWidth * .68f
                && upperLeft >= Math.max(2, Math.round(area * .10f))
                && lowerLeft <= Math.max(2, Math.round(area * .16f));
    }

    private static boolean looksLikeTwo(byte[] gray, int width, int regionHeight, int area,
                                         int minX, int maxX, int minY, int maxY) {
        int glyphWidth = maxX - minX + 1, glyphHeight = maxY - minY + 1;
        if (glyphHeight < regionHeight * .13f || glyphHeight > regionHeight * .48f
                || glyphWidth < regionHeight * .045f || glyphWidth > regionHeight * .34f
                || glyphWidth < glyphHeight * .34f || glyphWidth > glyphHeight * 1.12f)
            return false;
        float fill = area / (float) (glyphWidth * glyphHeight);
        if (fill < .13f || fill > .66f) return false;
        int[] rows = new int[glyphHeight];
        int upperRight = 0, lowerLeft = 0, lowerDiagonal = 0;
        for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
            if ((gray[y * width + x] & 0xff) > 165) continue;
            int localX = x - minX, localY = y - minY;
            rows[localY]++;
            if (localX >= glyphWidth * .52f && localY <= glyphHeight * .48f) upperRight++;
            if (localX <= glyphWidth * .48f && localY >= glyphHeight * .52f) lowerLeft++;
            if (localX <= glyphWidth * .42f && localY >= glyphHeight * .52f
                    && localY <= glyphHeight * .82f) lowerDiagonal++;
        }
        int topStroke = 0, bottomStroke = 0, rightSpine = 0;
        for (int y = 0; y <= Math.min(glyphHeight - 1,
                Math.round(glyphHeight * .30f)); y++) topStroke = Math.max(topStroke, rows[y]);
        for (int y = Math.max(0, Math.round(glyphHeight * .70f)); y < glyphHeight; y++)
            bottomStroke = Math.max(bottomStroke, rows[y]);
        for (int x = Math.max(0, Math.round(glyphWidth * .58f)); x < glyphWidth; x++) {
            int run = 0;
            for (int y = minY; y <= maxY; y++) {
                run = (gray[y * width + minX + x] & 255) <= 165 ? run + 1 : 0;
                rightSpine = Math.max(rightSpine, run);
            }
        }
        return topStroke >= glyphWidth * .46f
                && bottomStroke >= glyphWidth * .52f
                && rightSpine < glyphHeight * .90f
                && upperRight >= Math.max(2, Math.round(area * .10f))
                && lowerLeft >= Math.max(2, Math.round(area * .10f))
                && lowerDiagonal >= Math.max(2, Math.round(area * .06f));
    }

    private static void addOrMerge(List<RestBarCandidate> result, RestBarCandidate candidate) {
        for (int index = 0; index < result.size(); index++) {
            RestBarCandidate existing = result.get(index);
            if (existing.measureIndex != candidate.measureIndex) continue;
            float overlap = Math.min(existing.region.right(), candidate.region.right())
                    - Math.max(existing.region.left(), candidate.region.left());
            float narrower = Math.min(existing.region.right() - existing.region.left(),
                    candidate.region.right() - candidate.region.left());
            if (overlap < narrower * .35f) continue;
            result.set(index, new RestBarCandidate(candidate.measureIndex,
                    new MeasureRegion(Math.min(existing.region.left(), candidate.region.left()),
                            Math.max(existing.region.right(), candidate.region.right()),
                            candidate.region.top(), candidate.region.bottom())));
            return;
        }
        result.add(candidate);
    }

    private static int containingRestBar(List<RestBarCandidate> candidates, float x, float y) {
        for (RestBarCandidate candidate : candidates) {
            MeasureRegion region = candidate.region;
            float regionHeight = region.bottom() - region.top();
            if (x >= region.left() && x <= region.right()
                    && y >= region.top() - regionHeight * .95f
                    && y <= region.bottom()) return candidate.measureIndex;
        }
        return -1;
    }

    private static int countLabel(byte[] labels, int width, int height, MeasureRegion region,
                                  byte wanted) {
        int left = clamp(Math.round(region.left() * width), 0, width - 1);
        int right = clamp(Math.round(region.right() * width), left, width - 1);
        int top = clamp(Math.round(region.top() * height), 0, height - 1);
        int bottom = clamp(Math.round(region.bottom() * height), top, height - 1);
        int count = 0;
        for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++)
            if (labels[y * width + x] == wanted) count++;
        return count;
    }

    /** Staff lines are long but thin. A multi-rest's centered bar stays dark for several rows. */
    private static boolean hasThickHorizontalBar(byte[] gray, int width, int height,
                                                  MeasureRegion region) {
        int left = clamp(Math.round(region.left() * width), 0, width - 1);
        int right = clamp(Math.round(region.right() * width), left, width - 1);
        int top = clamp(Math.round(region.top() * height), 0, height - 1);
        int bottom = clamp(Math.round(region.bottom() * height), top, height - 1);
        int regionWidth = right - left + 1, regionHeight = bottom - top + 1;
        if (regionWidth < 12 || regionHeight < 8) return false;
        int requiredRun = Math.max(8, Math.round(regionWidth * .34f));
        int requiredThickness = Math.max(3, Math.min(7, Math.round(regionHeight * .035f)));
        int searchTop = top + Math.round(regionHeight * .18f);
        int searchBottom = bottom - Math.round(regionHeight * .12f);
        int consecutive = 0;
        for (int y = searchTop; y <= searchBottom; y++) {
            int longest = 0, current = 0, paleGap = 0;
            for (int x = left; x <= right; x++) {
                if ((gray[y * width + x] & 0xff) <= 150) {
                    current += paleGap + 1;
                    paleGap = 0;
                    longest = Math.max(longest, current);
                } else if (current > 0 && paleGap < 1) {
                    paleGap++;
                } else {
                    current = 0;
                    paleGap = 0;
                }
            }
            consecutive = longest >= requiredRun ? consecutive + 1 : 0;
            if (consecutive >= requiredThickness) return true;
        }
        return false;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
