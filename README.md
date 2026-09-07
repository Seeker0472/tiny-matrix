# tiny-matrix

4×4 矩阵加速器，面向 `mpc-frame` 接口。

## 快速开始

```sh
nix develop     # 提供 mill / firtool / bash
make test       # Chisel 单元测试（14 项）
make build      # 从 Chisel 生成 frame/rtl/FrameMatrix.sv
make clean      # 清掉 out/
```

把 `frame/` 整目录拷进 `mpc-frame/designs/tiny-matrix/` 。

## 仓库布局

| 路径 | 内容 |
| --- | --- |
| `matrix/src/matrix/` | Chisel RTL（`MatrixAlu` / `MatrixCore` / `FrameMatrix`） |
| `matrix/test/src/matrix/` | chiseltest 套件 |
| `frame/` | 给 mpc-frame 的用户设计包（`design.json`、RTL） |

## Payload 位图（66-bit）

| 位域 | 方向 | 信号 |
| --- | --- | --- |
| `[7:0]` | in | `data` |
| `[9:8]` | in | `row` |
| `[11:10]` | in | `col` |
| `[15:12]` | in | `opcode` |
| `[16]` | in | `start` |
| `[48:17]` | out | `rdata` |
| `[49]` | out | `done` |
| `[50]` | out | `busy` |

输入与输出错开，避免 `io_oe` 与测试激励争用。详细 opcode 见 `frame/README.md`。
