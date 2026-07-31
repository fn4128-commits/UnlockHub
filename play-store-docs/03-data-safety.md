# 03 · 数据安全表单 — UnlockHub

> 提交位置：**Play Console → Policy and programs / App content → Data safety**
> 表单答案必须与 02 隐私政策一致，否则易被驳回。

---

## 第 1 步：是否收集或共享用户数据？
**Yes（是）** — UnlockHub 把签到记录同步到自有后端（Cloudflare）。

## 第 2 步：数据类型逐类预填

| 数据类型 | 是否收集 | 是否共享 | 用途 | 是否必需 | 说明 |
|---|---|---|---|---|---|
| **Personal info → Name** | 是 | 否* | App functionality | 必需 | 用户填写的昵称（display name）|
| **Personal info → Email address** | **是** | 否* | **App functionality（账号管理）** | 必需 | 注册时填写。**仅用于区分同名同密码的账号与账号找回；不验证真实性、不发送任何邮件、不用于营销** |
| **Personal info → 其他 ID / 联系人 handle** | 是 | 否* | App functionality | 必需 | 用户自填 handle（昵称或标识）|
| **App activity → 首次解锁/不活跃事件时间戳** | 是 | 否* | App functionality | 必需 | 核心签到数据 |
| **Device or other IDs → 安装标识** | 是 | 否* | App functionality | 必需 | 设备端生成的 install ID |
| **其他用户内容 → 备忘录内容** | **否** | 否 | — | — | **不收集**：备忘录（含提醒、私密备忘）仅存于手机本机，云同步已移除 |
| **Location（精确/大致）** | **否** | 否 | — | — | 路线/地点功能仅在**本机**读取位置用于触发提醒，**不上传后端** |

\* "共享(Shared)" 在 Play 定义中指转移给**第三方公司**。数据存到你自己的 Cloudflare 后端属于"你自己处理"，一般算 **Collected 不算 Shared**。TODO(用户确认后端是否有其他第三方接收方)。

## 第 3 步：每类通用回答

- **Is this data encrypted in transit?** **Yes** — 已移除明文流量，默认后端为 HTTPS Cloudflare Worker。
- **Can users request data deletion?** 应回答 **Yes**，并提供删除渠道 URL/邮箱（与 02 一致，TODO）。
- **数据用途**：全部为 **App functionality**（无 Analytics / Advertising）。

## SDK 对照速查
- 本项目**未接入** AdMob / Firebase Analytics / Crashlytics（仅访问自有 Cloudflare 后端）。若之后接入，需按 references/compliance 的速查补勾 Device ID / Diagnostics 等。

---

### 需用户确认的 TODO
1. 后端是否有第三方接收方（决定 Shared 与否）。目前只有自建 Cloudflare 后端 → 应为 **否**。
2. 删除申请渠道（邮箱或网页 URL），与 02 隐私政策保持一致。

已确认（无需再问）：
- 传输加密：**Yes**（明文流量已移除）。
- 位置：**不收集**（仅本机使用，不上传）。
- 备忘录内容：**不收集**（云同步已移除，v0.9.1）。
- 邮箱：**收集**，但不验证、不发信，仅用于账号区分与找回。
- 自动删除：账号连续 **5 年无活动**（App 无签到、网页无查看）自动清除，需在 Data safety 的
  "Data retention / deletion" 说明中与隐私政策口径一致。
