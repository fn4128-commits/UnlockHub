# 03 · 数据安全表单 — UnlockHub

> 提交位置：**Play Console → Policy and programs / App content → Data safety**
> 表单答案必须与 02 隐私政策一致，否则易被驳回。

---

## 第 1 步：是否收集或共享用户数据？
**Yes（是）** — UnlockHub 把签到记录同步到自有后端（Cloudflare）。

## 第 2 步：数据类型逐类预填

| 数据类型 | 是否收集 | 是否共享 | 用途 | 是否必需 | 说明 |
|---|---|---|---|---|---|
| **Personal info → Name** | 是 | 否* | App functionality | 必需 | 用户填写的 display name |
| **Personal info → 其他 ID / 联系人 handle** | 是 | 否* | App functionality | 必需 | 用户自填 handle（可能是昵称/手机号/邮箱，取决于用户输入）|
| **App activity → 首次解锁/不活跃事件时间戳** | 是 | 否* | App functionality | 必需 | 核心签到数据 |
| **Device or other IDs → 安装标识** | 是 | 否* | App functionality | 必需 | 设备端生成的 install ID |
| **App activity / 其他用户内容 → 备忘录内容** | 视情况 | 否* | App functionality | 可选 | 仅当使用**备忘录同步**时才上传；纯本地备忘录不勾 |
| **Location（精确/大致）** | TODO(用户确认) | — | App functionality | 可选 | 仅当路线/地点功能会**上传**位置时才勾选；若只在本机使用则**不勾** |

\* "共享(Shared)" 在 Play 定义中指转移给**第三方公司**。数据存到你自己的 Cloudflare 后端属于"你自己处理"，一般算 **Collected 不算 Shared**。TODO(用户确认后端是否有其他第三方接收方)。

## 第 3 步：每类通用回答

- **Is this data encrypted in transit?** **Yes** — 已移除明文流量，默认后端为 HTTPS Cloudflare Worker。
- **Can users request data deletion?** 应回答 **Yes**，并提供删除渠道 URL/邮箱（与 02 一致，TODO）。
- **数据用途**：全部为 **App functionality**（无 Analytics / Advertising）。

## SDK 对照速查
- 本项目**未接入** AdMob / Firebase Analytics / Crashlytics（仅访问自有 Cloudflare 后端）。若之后接入，需按 references/compliance 的速查补勾 Device ID / Diagnostics 等。

---

### 需用户确认的 TODO
1. 位置数据是否上传后端（决定 Location 是否勾选）。
2. 是否使用备忘录同步（决定"备忘录内容"是否勾选）。
3. 后端是否有第三方接收方（决定 Shared 与否）。
（传输加密已确认 Yes：明文流量已移除。）
