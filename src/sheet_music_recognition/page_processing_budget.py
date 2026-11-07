# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Bound native decoding time by the accepted analysis raster workload."""


def decoder_timeout_seconds(width, height):
    """Keep ordinary pages at six minutes; allow up to ten for larger rasters.

    The decoder accepts at most twenty million pixels. Additional time preserves
    the same resolution, model tiles and musical OCR passes on taller pages.
    This remains a hard deadline, rather than extending an unresponsive process.
    """
    if (type(width) is not int or type(height) is not int
            or width < 1 or height < 1 or width * height > 20_000_000):
        raise ValueError("Invalid decoder raster dimensions")
    extra_pixels = max(0, width * height - 8_000_000)
    return 360 + (extra_pixels + 49_999) // 50_000
