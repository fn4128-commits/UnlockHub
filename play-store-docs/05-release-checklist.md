# 05 · 发布前检查清单 — UnlockHub

> 当前版本：`com.jinxin.unlockhub` · versionName 0.5.2 · versionCode 16 · **targetSdk 36** · minSdk 26
> ✅ 标记 = 本次已由代码改动完成；⬜/TODO = 仍需你操作。

## A. 技术准备
- [x] ✅ **targetSdk/compileSdk = 36**（已在 `app/build.gradle` 改好）。→ 仍需**装 SDK 36 并真机回归**（Android 16 返回手势、full-screen intent、16KB 对齐等）。
- [x] ✅ **关闭明文流量**：已移除 `usesCleartextTraffic`；默认后端为 HTTPS Cloudflare Worker。→ 数据安全表单可答"encrypted in transit = Yes"。
- [x] ✅ **包名统一**：applicationId 与 namespace 均为 `com.jinxin.unlockhub`（源码已迁移）。
- [x] ✅ **release 签名配置**已加入 `build.gradle`（读 `keystore.properties`）。→ 仍需你**创建上传密钥并写 keystore.properties**（见桌面指南 01）。
- [ ] ⬜ **以 .aab 构建**：`./gradlew bundleRelease`（见桌面指南 01）。
- [ ] versionCode/versionName 已记录到 06 备案表（每次上传须自增 versionCode）。
- [ ] 启用 **Play App Signing**；密钥库与密码安全备份（丢失无法更新应用）。
- [ ] 移除调试日志/测试后门；R8/ProGuard（当前 minify 关闭）。
- [ ] 真机 ≥2 种尺寸测试；深色模式、横竖屏不崩溃。

## B. 敏感权限专项
- [x] ✅ **无障碍服务已移除**（服务声明、`BoundAppAccessibilityService.java`、xml、BIND 权限均删除；「应用启动」路线触发选项已隐藏）。不再触发 Google Play 无障碍政策风险。
- [ ] 🟠 **Background location（保留）**：你已决定保留地点触发功能。须在 Play Console 填**背景位置声明表** + 上传**演示视频**（逐字段答案与视频脚本见桌面指南《02-UnlockHub背景位置声明与演示视频》）。
- [ ] 🟠 `SCHEDULE_EXACT_ALARM`：Android 13+ 需在应用内正确请求，准备用途说明（精确不活跃检查）。
- [ ] 🟠 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`：Play 限制此权限用途，须能说明是保活签到必需；否则考虑改用 WorkManager。
- [ ] `POST_NOTIFICATIONS` / `USE_FULL_SCREEN_INTENT`：验证 Android 16 下锁屏弹窗仍工作。
- [ ] 权限最小化：逐条审视 WIFI_STATE、BLUETOOTH_CONNECT 等是否都在用。

## C. Play Console 配置
- [ ] Main store listing 全部填写（见 01；图标 512×512、feature graphic 1024×500、截图 ≥2 张）。
- [ ] 隐私政策 URL 可公开访问（见 02）。
- [ ] Data safety 表单提交且与隐私政策一致（见 03）。
- [ ] 内容分级问卷完成（见 04）。
- [ ] Target audience：选成人年龄段（**不要**勾 13 岁以下）。
- [ ] Ads declaration：**No ads**。
- [ ] 背景位置声明表（保留该权限，必填）。
- [ ] 国家/地区与定价：免费（发布后不能改为付费）。

## D. 个人账号封闭测试（2023-11-13 后注册的个人账号必需）
- [ ] 创建 Closed testing 轨道并上传 `.aab`。
- [ ] 招募 **≥12 名测试者**（建议 15–20 留余量），发 opt-in 链接。
- [ ] 所有测试者 opt-in 并**连续 14 天**保持。
- [ ] 期间记录 bug 与反馈（Production Access 问卷会问）。
- [ ] 14 天后申请 Production access，认真填约 10 题问卷。
- [ ] 组织账号可豁免此项 — TODO(用户确认账号类型)。

## E. 提交与发布后
- [ ] 提交审核（新账号首审可能数天到一周+）。
- [ ] 审核期不频繁重复提交。
- [ ] 发布后监控 Crash/ANR，回复评论，更新 06 备案表。

---
### 状态小结
- ✅ 已解决：无障碍政策风险、明文传输、target36、包名统一、签名配置脚手架。
- ⬜ 仍需你做：**创建密钥 + 出 .aab + 真机测试**；**背景位置声明表 + 演示视频**；`SCHEDULE_EXACT_ALARM`/电池优化权限说明；封闭测试。
