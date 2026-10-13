// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Exact source hairpin endpoints and fractional clocks.
(() => {
  'use strict';
  function requireValue(condition, message) {
    if (!condition) throw new Error(message);
  }
  function integer(value) {
    requireValue(typeof value !== 'number' || Number.isSafeInteger(value), 'Inexact numeric integer');
    const text = String(value).trim();
    requireValue(/^[+-]?\d{1,128}$/.test(text), 'Expected bounded integer');
    return BigInt(text);
  }
  function gcd(a, b) {
    a = a < 0n ? -a : a;
    while (b) [a, b] = [b, a % b];
    return a;
  }
  class Fraction {
    constructor(numerator, denominator = 1n) {
      let n = integer(numerator), d = integer(denominator);
      requireValue(d !== 0n, 'Zero fraction denominator');
      if (d < 0n) { n = -n; d = -d; }
      const factor = gcd(n, d);
      this.n = n / factor;
      this.d = d / factor;
      Object.freeze(this);
    }
    static from(value) {
      if (value instanceof Fraction) return value;
      requireValue(typeof value !== 'number' || Number.isSafeInteger(value), 'Inexact numeric integer');
      const pieces = String(value).split('/');
      requireValue(pieces.length <= 2, 'Malformed fraction');
      return new Fraction(pieces[0], pieces.length === 2 ? pieces[1] : 1n);
    }
    add(value) { const f = Fraction.from(value); return new Fraction(this.n*f.d+f.n*this.d, this.d*f.d); }
    subtract(value) { const f = Fraction.from(value); return new Fraction(this.n*f.d-f.n*this.d, this.d*f.d); }
    multiply(value) { const f = Fraction.from(value); return new Fraction(this.n*f.n, this.d*f.d); }
    compare(value) { const f = Fraction.from(value), delta = this.n*f.d-f.n*this.d; return delta < 0n ? -1 : delta > 0n ? 1 : 0; }
    toString() { return `${this.n}/${this.d}`; }
  }
  const zero = new Fraction(0n);
  const children = (node, name) => Array.from(node.children).filter(c => c.localName === name);
  function one(node, name, fallback) {
    const nodes = children(node, name);
    requireValue(nodes.length <= 1, `Ambiguous ${name}`);
    return nodes.length ? nodes[0].textContent.trim() : fallback;
  }
  function plan(document, mapping) {
    requireValue(document.documentElement?.localName === 'score-partwise'
      && !document.getElementsByTagName('parsererror').length, 'Expected valid partwise MusicXML');
    // Mapping must be supplied by a separately verified import identity check.
    // Never infer a native staff from a MusicXML staff number alone.
    requireValue(Array.isArray(mapping) && mapping.length > 0, 'Verified staff mapping required');
    const staffMap = new Map(), nativeIds = new Set(), mappedParts = new Set();
    const key = (part, staff) => JSON.stringify([part, staff]);
    for (const entry of mapping) {
      const {partId, sourceStaff, nativeStaff} = entry;
      requireValue([partId, sourceStaff, nativeStaff].every(v => typeof v === 'string' && v.length > 0), 'Invalid staff identity');
      requireValue(!staffMap.has(key(partId, sourceStaff)) && !nativeIds.has(nativeStaff), 'Ambiguous staff mapping');
      staffMap.set(key(partId, sourceStaff), nativeStaff);
      nativeIds.add(nativeStaff); mappedParts.add(partId);
    }
    const spans = [], lengths = Object.create(null), partIds = new Set();
    for (const part of children(document.documentElement, 'part')) {
      const partId = part.getAttribute('id');
      requireValue(partId && !partIds.has(partId) && mappedParts.has(partId), 'Unmapped or duplicate part');
      partIds.add(partId);
      let divisions = null, meter = null;
      const active = new Map(), partLengths = [];
      const measures = children(part, 'measure');
      requireValue(measures.length > 0, 'Part has no measures');
      for (let mi = 0; mi < measures.length; mi++) {
        const measure = measures[mi];
        let cursor = zero, maximum = zero;
        for (const node of Array.from(measure.children)) {
          if (node.localName === 'attributes') {
            const value = one(node, 'divisions', null);
            if (value !== null) { divisions = integer(value); requireValue(divisions > 0n, 'Invalid divisions'); }
            const times = children(node, 'time');
            requireValue(times.length <= 1, 'Multiple staff meters need explicit support');
            if (times.length) {
              const beats = one(times[0], 'beats', null), beatType = one(times[0], 'beat-type', null);
              requireValue(beats !== null && beatType !== null, 'Unsupported meter');
              const terms = beats.split('+').map(integer), denominator = integer(beatType);
              requireValue(terms.every(v => v > 0n) && denominator > 0n, 'Invalid meter');
              meter = new Fraction(terms.reduce((a,b) => a+b, 0n), denominator);
            }
          } else if (['note', 'backup', 'forward'].includes(node.localName)) {
            requireValue(divisions !== null, 'Duration before divisions');
            if (node.localName === 'note'
              && (children(node, 'chord').length || children(node, 'grace').length)) continue;
            const duration = new Fraction(integer(one(node, 'duration', '')), 4n*divisions);
            requireValue(duration.compare(zero) > 0, 'Nonpositive duration');
            cursor = node.localName === 'backup' ? cursor.subtract(duration) : cursor.add(duration);
            requireValue(cursor.compare(zero) >= 0, 'Backup before measure start');
            if (cursor.compare(maximum) > 0) maximum = cursor;
          } else if (node.localName === 'direction') {
            const wedges = children(node, 'direction-type').flatMap(type => children(type, 'wedge'));
            if (!wedges.length) continue;
            requireValue(divisions !== null && wedges.length === 1, 'Ambiguous wedge direction');
            const wedge = wedges[0], sourceStaff = one(node, 'staff', '1');
            const sid = staffMap.get(key(partId, sourceStaff));
            requireValue(sid !== undefined, 'Unmapped wedge staff');
            const point = cursor.add(new Fraction(integer(one(node, 'offset', '0')), 4n*divisions));
            const number = integer(wedge.getAttribute('number') || '1'), kind = wedge.getAttribute('type');
            requireValue(number >= 1n && number <= 16n, 'Wedge number outside MusicXML number-level range');
            // Number-level distinguishes concurrent objects within a part in
            // document order, including objects on different staves.
            const wedgeKey = number.toString();
            if (kind === 'crescendo' || kind === 'diminuendo') {
              requireValue(!active.has(wedgeKey), 'Ambiguous overlapping wedge number');
              active.set(wedgeKey, {staff: sid, sourceNumber: number.toString(), startMeasure: mi, start: point,
                importStartMeasure: mi, importStart: cursor.toString(),
                subtype: kind === 'crescendo' ? 0 : 1});
            } else if (kind === 'stop') {
              requireValue(active.has(wedgeKey), 'Unmatched wedge stop');
              requireValue(active.get(wedgeKey).staff === sid, 'Cross-staff wedge requires explicit endpoint mapping');
              spans.push({...active.get(wedgeKey), endMeasure: mi, end: point,
                importEndMeasure: mi, importEnd: cursor.toString()});
              active.delete(wedgeKey);
            } else throw new Error('Unsupported wedge continuation');
          }
        }
        requireValue(meter !== null, 'Meter required');
        const length = measure.getAttribute('implicit') === 'yes' ? maximum : meter;
        requireValue(length.compare(zero) > 0 && maximum.compare(length) <= 0, 'Invalid measure extent');
        partLengths.push(length);
      }
      requireValue(active.size === 0, 'Unclosed wedge');
      for (const entry of mapping) if (entry.partId === partId) lengths[entry.nativeStaff] = partLengths;
    }
    requireValue(partIds.size === mappedParts.size, 'Mapping references missing part');
    for (const span of spans) {
      const lens = lengths[span.staff];
      if (span.start.compare(lens[span.startMeasure]) === 0) { span.startMeasure++; span.start = zero; }
      if (span.end.compare(lens[span.endMeasure]) === 0 && span.endMeasure+1 < lens.length) { span.endMeasure++; span.end = zero; }
      requireValue(span.startMeasure <= span.endMeasure && span.startMeasure < lens.length, 'Unrepresentable wedge boundary');
      requireValue(span.start.compare(zero) >= 0 && span.start.compare(lens[span.startMeasure]) < 0
        && span.end.compare(zero) >= 0 && span.end.compare(lens[span.endMeasure]) <= 0, 'Wedge outside measure');
      requireValue(span.startMeasure < span.endMeasure || span.start.compare(span.end) < 0, 'Nonpositive wedge span');
    }
    return {
      spans: spans.map(s => ({...s, start: s.start.toString(), end: s.end.toString()})),
      lengths: Object.fromEntries(Object.entries(lengths).map(([sid, values]) => [sid, values.map(f => f.toString())]))
    };
  }
  globalThis.HairpinSource = Object.freeze({Fraction, plan});
})();
