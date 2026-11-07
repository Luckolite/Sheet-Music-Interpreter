# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Export paper_field_v1 using PyTorch 2.1.2 and ONNX 1.17.0 (opset 17)."""
import hashlib
from pathlib import Path
import torch
from paper_field import ExtremaField

def pooling_export():
    # The pinned architecture uses only unpadded 8x8/8 pooling. Standard ONNX
    # ceil_mode handles dynamic trailing cells without the old exporter's
    # static-shape padding calculation. Those cells average valid pixels only.
    def average(graph, value, *arguments):
        return graph.op("AveragePool", value, kernel_shape_i=[8, 8], strides_i=[8, 8],
                        pads_i=[0, 0, 0, 0], ceil_mode_i=1, count_include_pad_i=0)

    def maximum(graph, value, *arguments):
        return graph.op("MaxPool", value, kernel_shape_i=[8, 8], strides_i=[8, 8],
                        pads_i=[0, 0, 0, 0], ceil_mode_i=1)

    torch.onnx.register_custom_op_symbolic("aten::avg_pool2d", average, 17)
    torch.onnx.register_custom_op_symbolic("aten::max_pool2d", maximum, 17)



def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--checkpoint', type=Path, default=Path(__file__).resolve().parents[1] / 'models/paper_field_v1.pt')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    expected = '7e9a4b16ca5c1aeda56080c69ac5e63d7a00de56860a6d265d8bed578eb8971d'
    if hashlib.sha256(args.checkpoint.read_bytes()).hexdigest() != expected:
        raise ValueError('Checkpoint checksum mismatch')
    model = ExtremaField().eval()
    model.load_state_dict(torch.load(args.checkpoint, map_location='cpu', weights_only=True)['state_dict'])
    pooling_export()
    torch.onnx.export(model, torch.zeros((1, 2, 320, 320)), str(args.output),
        input_names=['features'], output_names=['paper', 'contrast'], opset_version=17,
        dynamic_axes={name: {2: 'height', 3: 'width'} for name in ('features', 'paper', 'contrast')})
    print(hashlib.sha256(args.output.read_bytes()).hexdigest())

if __name__ == '__main__':
    main()
