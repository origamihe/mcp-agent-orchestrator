# 第三方软件声明 (Third-Party Notices)

本项目 (mcp-agent-orchestrator) 使用以下第三方开源软件。各软件的许可证条款仅适用于其各自的代码，不影响本项目的 Apache 2.0 许可证。

---

## AivisSpeech-Engine

- **名称**：AivisSpeech-Engine
- **版本**：master 分支
- **仓库**：https://github.com/AivisProject/AivisSpeech-Engine
- **许可证**：GNU Lesser General Public License v3.0 (LGPL v3)
- **版权**：Copyright (c) AivisProject Contributors
- **用途**：日语 TTS（文本转语音）语音合成引擎
- **修改**：无修改，原样使用

### LGPL v3 合规声明

根据 LGPL v3 第 4 条，本项目将 AivisSpeech-Engine 作为独立库使用（通过网络 API 调用），未将其代码与本项目代码合并编译。AivisSpeech-Engine 的完整源码和许可证文件保留在其原始目录 `AivisSpeech-Engine-master/` 中。

用户可自行替换或升级 AivisSpeech-Engine 版本，而无需修改本项目代码。

---

## NapCatQQ

- **名称**：NapCatQQ
- **仓库**：https://github.com/NapNeko/NapCatQQ
- **许可证**：NapNeko 自定义许可证
- **版权**：Copyright (c) NapNeko Contributors
- **用途**：基于 NTQQ 的 QQ Bot 协议适配层
- **修改**：无修改，原样使用

### NapCatQQ 许可证合规声明

NapCatQQ 采用 NapNeko 自定义许可证，**禁止基于 NapCat 代码进行未经授权的消息推送功能开发**。本项目仅通过 NapCat 提供的 OneBot 标准协议接口与其通信，不修改 NapCat 源码，不基于 NapCat 代码进行二次开发。

NapCatQQ 的完整源码和许可证文件可通过其官方仓库获取。本项目不重新分发 NapCatQQ 的源码或二进制文件。

---

## 其他 Node.js 依赖（NapCat 运行时）

NapCat 的 `node_modules/` 中包含以下 MIT 许可的依赖（部分列举）：

| 包名 | 许可证 |
|------|--------|
| express | MIT |
| ws | MIT |
| body-parser | MIT |
| cookie | MIT |
| debug | MIT |
| iconv-lite | MIT |
| 及其他 | MIT / ISC |

这些依赖由 NapCat 的 `package.json` 管理，其许可证文件位于各自的 `node_modules/<package>/LICENSE` 目录中。

---

## 完整许可证文本

### Apache License 2.0（本项目）

参见项目根目录下的 [LICENSE](LICENSE) 文件。

### LGPL v3（AivisSpeech-Engine）

参见 `AivisSpeech-Engine-master/LICENSE` 文件，或访问：
https://www.gnu.org/licenses/lgpl-3.0.html

### NapNeko 自定义许可证（NapCatQQ）

参见 NapCatQQ 官方仓库：https://github.com/NapNeko/NapCatQQ

---

> 如有任何许可证合规问题，请通过 GitHub Issues 联系我们。