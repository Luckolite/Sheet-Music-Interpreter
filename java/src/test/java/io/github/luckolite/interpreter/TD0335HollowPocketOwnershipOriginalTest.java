// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Procedural body-local counter and foreign-loop ownership controls. */
public final class TD0335HollowPocketOwnershipOriginalTest {
  private static final int W = 320, H = 240, CX = 160, CY = 120;

  private static final class Scene {
    final byte[] labels = new byte[W * H];
    final byte[] gray = new byte[W * H];
    Scene() { Arrays.fill(gray, (byte) 250); }
    void ink(int x, int y, int role, int shade) {
      if (x >= 0 && x < W && y >= 0 && y < H) {
        labels[y * W + x] = (byte) role;
        gray[y * W + x] = (byte) shade;
      }
    }
  }

  private static Scene body(boolean hollow, boolean foreignLoop) {
    Scene s = new Scene();
    double tilt = .22, rx = 13.0, ry = 7.0;
    for (int y = CY - 10; y <= CY + 10; y++) {
      for (int x = CX - 16; x <= CX + 16; x++) {
        double u = x - CX - tilt * (y - CY), v = y - CY;
        double q = u * u / (rx * rx) + v * v / (ry * ry);
        double hu = x - (CX + 1.0) - .34 * (y - CY), hv = y - CY;
        boolean opening = hu * hu / (2.5 * 2.5) + hv * hv / (1.7 * 1.7) < 1.0;
        if (q <= 1.0 && (!hollow || !opening))
          s.ink(x, y, 2, (v > .2 && q < .58) ? 55 : 110);
      }
    }
    int root = (int) Math.round(CX + tilt * (-7) + 8);
    for (int y = CY - 48; y <= CY - 7; y++)
      for (int x = root - 1; x <= root; x++) s.ink(x, y, 1, 35);
    if (foreignLoop) {
      int px = CX - 29;
      for (int y = CY - 7; y <= CY + 7; y++) {
        for (int x = px - 11; x <= px + 11; x++) {
          double q = Math.pow((x - px) / 10.0, 2) + Math.pow((y - CY) / 7.0, 2);
          if (q <= 1.0 && q >= .64) s.ink(x, y, 2, 45);
        }
      }
      for (int x = CX - 13; x >= px + 10; x--) {
        double t = (CX - 13 - x) / (double) (CX - 13 - px - 10);
        int y = CY + (int) Math.round(4 * Math.sin(t * Math.PI * 1.5));
        for (int dy = -1; dy <= 1; dy++) s.ink(x, y + dy, 2, 45);
      }
    }
    return s;
  }

  private static int[] members(Scene s) {
    int seed = -1;
    for (int i = 0; i < s.labels.length; i++) if (s.labels[i] == 2) { seed = i; break; }
    assertTrue(seed >= 0);
    boolean[] seen = new boolean[s.labels.length];
    int[] queue = new int[s.labels.length];
    int read = 0, end = 1; queue[0] = seed; seen[seed] = true;
    while (read < end) {
      int p = queue[read++], x = p % W, y = p / W;
      for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
        if (dx == 0 && dy == 0) continue;
        int xx = x + dx, yy = y + dy;
        if (xx < 0 || xx >= W || yy < 0 || yy >= H) continue;
        int n = yy * W + xx;
        if (!seen[n] && (s.labels[n] & 255) == 2) { seen[n] = true; queue[end++] = n; }
      }
    }
    return Arrays.copyOf(queue, end);
  }

  @Test public void ownShaftHollowBodyWithSmallTiltedOpeningKeepsPriorOpenEvidence() {
    Scene s = body(true, false); int[] full = members(s);
    var result = ProposedFilledBodyCaller.inspect(s.labels, s.gray, W, H, full, 16);
    assertNotNull(result);
    assertArrayEquals(full, result.fullMembers());
    assertNotEquals(ProposedFilledBodyCaller.State.FILLED_BODY, result.state());
    assertTrue(ProposedFilledBodyCaller.retainPriorOpenEvidence(true, result));
  }

  @Test public void attachedForeignLoopPocketDoesNotUndoFilledOwnShaftBody() {
    Scene s = body(false, true); int[] full = members(s);
    var result = ProposedFilledBodyCaller.inspect(s.labels, s.gray, W, H, full, 16);
    assertEquals(ProposedFilledBodyCaller.State.FILLED_BODY, result.state());
    assertArrayEquals("complete semantic component retained", full, result.fullMembers());
    assertFalse(ProposedFilledBodyCaller.retainPriorOpenEvidence(true, result));
  }
}
