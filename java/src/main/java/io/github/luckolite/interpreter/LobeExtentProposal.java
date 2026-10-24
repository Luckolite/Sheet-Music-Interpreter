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
}



