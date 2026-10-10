# OTRYX 品牌规范与吉祥物设计

![OTRYX 品牌](images/brand/otryx-banner.svg)

## 品牌定位

**OTRYX RPC** — Simple to Call. Built to Scale.

主张：**让分布式通信，简单而可靠。** 不是靠无界缓存或复杂概念换取漂亮的数字，而是依靠架构边界、受控背压、清晰契约和可重复的工程验证。

## 吉祥物 Otti

![工程水獭 Otti](images/mascot/otti-main.svg)

以已确认的原始科技水獭参考图为形象基准：温暖的棕色外形、奶油色腹部、青蓝色大眼睛、蓝色工程师马甲、友好的笑容与扳手。角色主要特质是**聪明、敏捷、协作、可靠**，避免做成严肃或具攻击性的猛兽图腾。

角色故事：在由无数服务组成的河流中，Otti 懂得理解水流、选择可靠路径，与伙伴协作，让每一次 RPC 请求精准抵达。将复杂留在框架内部，让简单留给开发者。

## 色彩系统

| 名称 | HEX | 场景 |
|---|---|---|
| 深海蓝 | #153451 | 背景、正文、信任感 |
| 科技青 | #16BFC4 | 高亮、连接、活力 |
| 水獭棕 | #9E6848 | 吉祥物皮毛 |
| 奶油白 | #F8E8BE | 面部、腹部 |
| 暖橙色 | #F2A34A | 温度、交互重点 |

## 文档图片与来源

- docs/images/brand/：Logo / Banner；
- docs/images/mascot/：吉祥物主形象与表情；
- docs/images/architecture/：以实际模块和 SPI 关系为依据的架构图；
- docs/images/flows/：调用流程和兼容演进图。

公开文档采用仓库相对链接，不使用临时外链。图片需要与源码现状保持一致。SVG 图属于可维护的设计源文件；性能数字只有经过可复现 Benchmark/Soak Evidence 才能出现在品牌图中。

**名称风险：** 已存在 Otryx Systems 的软件相关服务品牌与第三方商标记录（印度第 42 类图形/标签商标，申请号 5533429）。项目正式宣传或商业使用前应核查目标法域的商标权及图形近似风险。

**素材授权：** 原始参考水獭由用户提供，但当前仓库没有足以证明原作者及公开商用改编授权的链路；新 SVG 可作为开发期品牌概念展示，不表示已经取得独占商标权。之前 AI 概念海报中的未经实测性能数字不能用作正式宣传证据。

详见 [公开发布与品牌权利核查](publication-readiness.md)。

## 品牌素材清单（开发期概念稿）

| 图形 | 用途 | 仓库中的文件 |
|---|---|---|
| 横版 Banner | README、文档页头 | [OTRYX Banner](images/brand/otryx-banner.svg) |
| Otti 主形象 | 品牌故事、项目介绍 | [Otti SVG](images/mascot/otti-main.svg) |
| 系统架构 | 解释已实现架构 | [系统总览](images/architecture/system-overview.svg) |
| 控制面/数据面 | 解释两类负载边界 | [控制数据面](images/architecture/control-data-plane.svg) |
| Unary RPC 流程 | 解释请求主路径 | [调用生命周期](images/flows/rpc-lifecycle.svg) |

这些 SVG 是项目仓库中可编辑的概念与技术插图，主色遵循本文调色板。**仅纳入经过审核的代表性资产**，避免以大批相似海报占据用户文档。涉及准确拓扑、时序与状态的内容，以源码驱动的 Mermaid/矢量源及实际实现为准。

### 版权与公开使用限制

- 第三方原始参考图、品牌素材压缩包及其著作权链未在当前仓库得到可独立验证的授权证明，不将其视为商业宣传已获授权的素材。
- Otti 的开发期 SVG 与品牌概念不构成注册商标证据，不得暗示独占使用权。
- 商用 Logo、文档封面、社交头像和官网宣传上线前，仍需完成[商标、图片授权及 Maven Namespace 核查](publication-readiness.md)。
