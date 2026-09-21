# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Separate ink from dark paper with bounded working memory and local contrast."""
import numpy as np


def prepare_grayscale(gray):
    if not isinstance(gray, np.ndarray) or gray.ndim != 2 or gray.dtype != np.uint8:
        raise ValueError('Expected a 2D uint8 grayscale array')
    height, width = gray.shape
    if not width or not height or gray.size > 20_000_000:
        raise ValueError('Invalid grayscale page dimensions')
    if np.count_nonzero(gray < 150) < gray.size * .65:
        return gray
    radius = max(3, int(width * 15 / 2048 + .5))
    span = radius * 2 + 1
    columns = np.zeros(width, np.int64)
    squares = np.zeros(width, np.int64)
    for dy in range(-radius, radius + 1):
        row = gray[min(height - 1, max(0, dy))].astype(np.int64)
        columns += row
        squares += row * row
    output = np.empty_like(gray)
    area = float(span * span)
    for y in range(height):
        sums = np.concatenate(([0], np.cumsum(np.pad(columns, (radius, radius), mode='edge'))))
        sum_squares = np.concatenate(([0], np.cumsum(np.pad(squares, (radius, radius), mode='edge'))))
        mean = (sums[span:] - sums[:-span]) / area
        deviation = np.sqrt(np.maximum(0, (sum_squares[span:] - sum_squares[:-span]) / area - mean * mean))
        output[y] = np.where(gray[y] < mean * (1 + .3 * (deviation / 128 - 1)), 0, 255)
        before = gray[max(0, y - radius)].astype(np.int64)
        after = gray[min(height - 1, y + radius + 1)].astype(np.int64)
        columns += after - before
        squares += after * after - before * before
    return output
