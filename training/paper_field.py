# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original paper-field architecture and paired scan augmentation."""
import math
import cv2
import numpy as np
import torch
from torch import nn
from torch.nn import functional as F

class ExtremaField(nn.Module):
    """Predict smooth illumination and contrast; no symbol-sized decoder."""
    def __init__(self):
        super().__init__()
        self.layers = nn.Sequential(
            nn.Conv2d(6, 24, 3, padding=1), nn.ReLU(),
            nn.Conv2d(24, 32, 3, padding=2, dilation=2), nn.ReLU(),
            nn.Conv2d(32, 32, 3, padding=4, dilation=4), nn.ReLU(),
            nn.Conv2d(32, 24, 3, padding=2, dilation=2), nn.ReLU(),
            nn.Conv2d(24, 2, 1))
        nn.init.zeros_(self.layers[-1].weight)
        self.layers[-1].bias.data.copy_(torch.tensor([1.5, 0.]))

    def forward(self, x):
        coarse = torch.cat((F.avg_pool2d(x, 8, ceil_mode=True),
                            F.max_pool2d(x, 8, ceil_mode=True),
                            -F.max_pool2d(-x, 8, ceil_mode=True)), dim=1)
        fields = F.interpolate(self.layers(coarse), size=x.shape[-2:],
                               mode="bilinear", align_corners=False)
        return .23 + .77 * fields[:, :1].sigmoid(), .35 + .65 * fields[:, 1:].sigmoid()


def synthetic_scan(clean, rng, style):
    """Vary illumination and print contrast without moving or erasing symbols."""
    height, width = clean.shape
    y, x = np.mgrid[:height, :width].astype(np.float32)
    x, y = x / max(1, width - 1), y / max(1, height - 1)
    angle = rng.uniform(-math.pi, math.pi)
    axis = x * math.cos(angle) + y * math.sin(angle)
    paper = np.full(clean.shape, rng.uniform(230, 254), np.float32)
    if style == "fold":
        centre = rng.uniform(float(axis.min()), float(axis.max()))
        paper -= rng.uniform(45, 135) * np.exp(-((axis - centre) / rng.uniform(.045, .18)) ** 2)
    elif style == "gradient":
        slope = (axis - axis.min()) / max(.01, float(np.ptp(axis)))
        paper -= rng.uniform(40, 125) * slope
    elif style == "stain":
        for _ in range(int(rng.integers(1, 4))):
            blob = ((x - rng.uniform(0, 1)) / rng.uniform(.1, .4)) ** 2
            blob += ((y - rng.uniform(0, 1)) / rng.uniform(.1, .4)) ** 2
            paper -= rng.uniform(25, 80) * np.exp(-blob)
    elif style == "texture":
        texture = cv2.resize(rng.normal(0, 1, (12, 12)).astype(np.float32), (width, height),
                             interpolation=cv2.INTER_CUBIC)
        paper += rng.uniform(8, 23) * texture
        paper -= rng.uniform(15, 65) * x
    elif style != "clean":
        raise ValueError(style)
    paper = np.clip(paper, 65, 255)
    if style == "clean":
        return clean.copy(), np.ones_like(clean), np.ones_like(clean)
    contrast = np.full(clean.shape, rng.uniform(.5, 1), np.float32)
    if rng.random() < .4:
        contrast *= .75 + .25 * x
    scan = paper * (1 - contrast * (1 - clean / 255))
    scan += rng.normal(0, rng.uniform(.2, 2), clean.shape)
    return scan.clip(0, 255).astype(np.float32), paper / 255, contrast
