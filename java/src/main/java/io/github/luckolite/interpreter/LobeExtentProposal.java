// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Source-free ownership proposal. Core is a geometric hypothesis, not note admission. */
final class LobeExtentProposal {
  private LobeExtentProposal() {}
  record Core(int left,int top,int right,int bottom,float centerX,float centerY) {}
  static Core derive(byte[] labels, byte[] gray, int w, int h, int[] members, float gap) {
    return InteriorThicknessOwnership.derive(labels,gray,w,h,members,gap);
  }
  static Core derive(byte[] labels, byte[] gray, int w, int h, int[] members, float gap,StringBuilder trace) {
    return InteriorThicknessOwnership.derive(labels,gray,w,h,members,gap,trace);
  }

    /** Every genuine parent leaf participates. A returned ellipse remains a geometric proposal. */
    static java.util.Map<Object, Core> derivePartition(
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            ExactComponentMembers.ProducerPartition partition,
            float gap,
            StringBuilder trace) {
        return InteriorThicknessOwnership.derivePartition(
                labels, gray, w, h, partition, gap, trace);
    }

    /** External shafts must bind to a previously qualified source parent; mutual claims do not seed authority. */
    static java.util.Map<Object, Core> deriveCohort(
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            java.util.List<ExactComponentMembers.ProducerPartition> parents,
            float gap,
            StringBuilder trace) {
        return InteriorThicknessOwnership.deriveCohort(labels, gray, w, h, parents, gap, trace);
    }
}



