# 02 · 隐私政策 — UnlockHub

> ⚠️ **必须托管到公开可访问的 URL**（GitHub Pages / 你的官网均可），然后填入
> **Play Console → Policy and programs / App content → Privacy policy**。
> 下面英文版是提交/托管用的正文；中文版供你自查，二者内容必须一致。

---

## 英文版（用于托管） / EN — hosted version

```
Privacy Policy for UnlockHub

Effective date: TODO(用户：填写生效日期，如 2026-07-07)
Developer: TODO(用户：你的对外开发者名称)
Contact: TODO(用户：支持邮箱)

1. Introduction
UnlockHub ("the app", "we") is a personal utility hub with three features:
a daily family check-in, memos with reminders, and phone automations. This
policy explains what data the app handles and why.

2. Information We Collect
When you use UnlockHub, the app collects and syncs the following to our
backend so features work and your chosen contact can receive check-ins:
- An install identifier generated on your device.
- A display name (nickname) you enter.
- An email address you enter when you create an account. It is used only to
  tell accounts apart when two accounts happen to share the same nickname and
  password, and during account recovery. We never verify the address and we
  never send any email to it.
- A contact handle you enter (this may be a nickname or an identifier that you
  choose to type in).
- Timestamps of your first daily phone unlock and inactivity events.

Your memos are NOT collected. Memo content, including reminders and private
memos, stays on your device and is never uploaded.

If you enable the optional routine features, the app may access device
location and Bluetooth state. This happens entirely on the device to trigger
local reminders; location is never uploaded to our backend.

3. How We Use Information
- To deliver daily check-in signals and inactivity alerts to your contact.
- To generate weekly activity summaries.
We do not use your data for advertising and do not sell it.

4. Third-Party Services
- Backend hosting: Cloudflare Workers and Cloudflare D1 store the check-in
  records described above. See Cloudflare's privacy policy:
  https://www.cloudflare.com/privacypolicy/
TODO(用户：若之后接入分析/崩溃 SDK，在此补充)

5. Data Retention & Deletion
Check-in records are retained on the backend until deleted.
Inactive accounts are removed automatically: if an account has no activity for
5 years — no check-in recorded from the app and no viewing on the status page —
the account and all of its records are deleted permanently.
To request deletion of your data, contact TODO(用户：删除申请邮箱或网页 URL).
TODO(用户：需按 Google 要求同时提供"应用内删除账号"入口和"网页版删除申请 URL"，见 05 清单。)

6. Security
Data is transmitted to the backend over an encrypted HTTPS connection.

7. Children's Privacy
UnlockHub is not directed to children under 13. We do not knowingly collect
data from children.

8. Changes to This Policy
We may update this policy and will change the effective date above.

9. Contact Us
TODO(用户：支持邮箱)
```

---

## 中文对照版（自查用）

- **收集内容**：安装标识、你填写的昵称、**注册时填写的邮箱**（仅用于区分同名同密码的账号与账号找回，**不验证、不发信**）、你填写的联系人 handle、每日首次解锁与不活跃事件的时间戳。启用路线功能时在**本机**访问位置与蓝牙状态，位置**不上传**。
- **不收集**：**备忘录内容一律不上传**（含提醒与私密备忘），只存在手机本机。私密备忘的 PIN 以加盐哈希存于本机，永不上传。
- **保留期**：账号连续 **5 年无任何活动**（App 无签到记录、网页端无查看）将自动删除账号及其全部记录。
- **用途**：让签到功能跨设备可用、把签到与不活跃告警送达联系人、生成周报。**无广告、不出售数据。**
- **第三方**：Cloudflare Workers + D1（后端托管）。
- **保留与删除**：记录保留至被删除；提供删除申请渠道（TODO）。
- **传输安全**：✅ 已走 HTTPS 加密传输（已移除明文流量）。
- **儿童**：不面向 13 岁以下。

> 需要用户确认的关键点已用 TODO 标出，尤其是：**位置是否上传、删除渠道、明文传输改造**。这三项直接影响 03 数据安全表单的填写。
