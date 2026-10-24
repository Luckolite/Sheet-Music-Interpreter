// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/**
 * Private caller bridge: preserves the complete semantic component while attaching a bounded
 * filled-body proposal. A proposal may suppress an existing open-head route only when the
 * proposed body itself has no complete counter; otherwise the caller's prior evidence survives.
 */
final class ProposedFilledBodyCaller {
  enum State { FILLED_BODY, HOLLOW_BODY, UNRESOLVED }
  record Result(State state, LobeExtentProposal.Core core, int[] fullMembers) {}

  private ProposedFilledBodyCaller() {}

  static Result inspect(byte[] labels, byte[] gray, int width, int height, int[] fullMembers, float gap) {
    if (fullMembers == null) return new Result(State.UNRESOLVED, null, new int[0]);
    int[] retained = fullMembers.clone();
    LobeExtentProposal.Core core = LobeExtentProposal.derive(labels, gray, width, height, retained, gap);
    if (core == null) return new Result(State.UNRESOLVED, null, retained);
    boolean open = WholeHeadPocketEvidence.proved(gray, width, height,
        core.left(), core.top(), core.right(), core.bottom(), gap);
    boolean ambiguousOpening = WholeHeadPocketEvidence.hasEnclosedOpening(gray, width, height,
        core.left(), core.top(), core.right(), core.bottom(), core.centerX(), core.centerY(), gap);
    return new Result(open ? State.HOLLOW_BODY : ambiguousOpening ? State.UNRESOLVED : State.FILLED_BODY, core, retained);
  }

  /** Preserve the pre-existing whole-head decision unless a complete filled-body proposal exists. */
  static boolean retainPriorOpenEvidence(boolean priorOpen, Result result) {
    return priorOpen && (result == null || result.state() != State.FILLED_BODY);
  }

  static boolean retainedComponentExactly(int[] original, Result result) {
    return original != null && result != null && Arrays.equals(original, result.fullMembers());
  }
}


