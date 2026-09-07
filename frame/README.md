# tiny-matrix

4×4 矩阵加速器用户设计，接入 `mpc-frame` 的 66-bit payload 接口。

## 功能

片上维护三组 4×4 寄存器堆：

| 矩阵 | 元素宽度 | 说明 |
| --- | --- | --- |
| A | 8-bit | 操作数 / 转置源 |
| B | 8-bit | 操作数 |
| C | 32-bit | 累加 / 结果 |

通过 `opcode` 启动一次操作；`busy`/`done` 指示进度。`MATMUL` 按
`(i,j,k)` 三维扫描，约 64 个有效计算周期。

## Opcode

| 编码 | 名称 | 行为 |
| --- | --- | --- |
| `0x0` | WR_A | `A[row][col] <- data` |
| `0x1` | WR_B | `B[row][col] <- data` |
| `0x2` | RD_C | `rdata <- C[row][col]` |
| `0x3` | CLEAR | A/B/C 清零 |
| `0x4` | MADD | `C[i][j] = A[i][j] + B[i][j]` |
| `0x5` | MSUB | `C[i][j] = A[i][j] - B[i][j]`（32 位环绕） |
| `0x6` | HMUL | `C[i][j] = A[i][j] * B[i][j]` |
| `0x7` | MATMUL | `C = A × B` |
| `0x8` | TRANS | `C = Aᵀ`（元素零扩展到 32 位） |
| `0x9` | FILL_A | A 全部填 `data` |
| `0xA` | FILL_B | B 全部填 `data` |
| `0xB` | RD_A | `rdata <- A[row][col]` |

`start` 在 `busy=0` 时采到 1 会锁存命令并开始执行；请在单周期脉冲后拉低，
否则完成后会立刻重触发。

## Payload 位图

输入与输出故意错开，避免 `io_oe` 与测试激励争用：

| 位域 | 方向 | 信号 |
| --- | --- | --- |
| `[7:0]` | in | `data` |
| `[9:8]` | in | `row` |
| `[11:10]` | in | `col` |
| `[15:12]` | in | `opcode` |
| `[16]` | in | `start` |
| `[48:17]` | out | `rdata` |
| `[49]` | out | `done`（单周期脉冲） |
| `[50]` | out | `busy` |
| `[65:51]` | - | 未使用（`oe=0`） |

外部 `user_io` 换算：`user_io[n+7] <-> io_*[n]`。

## 源码结构

- Chisel：`../matrix/src/matrix/`（测试在 `../matrix/test/src/matrix/`）
- 生成 RTL：`rtl/FrameMatrix.sv`（含 `IO_WIDTH` 参数）

本 package 不附带 SV unit/frame TB；功能验证走仓库根目录的 chiseltest。

在仓库根目录：

```sh
nix develop
make build   # 重新从 Chisel 生成 SV
make test    # Chisel / chiseltest
```

把本目录拷到 `mpc-frame/designs/tiny-matrix` 后，在 mpc-frame 里跑：

```sh
make user-lint DESIGN=designs/tiny-matrix
```
