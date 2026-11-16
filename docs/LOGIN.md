# 登录与登录态

## 1. 河大校外访问的真实链路

```
                     ┌───────────────────────────────────────────┐
  zhjw.hbu.edu.cn ──▶│ 302 → v.hbu.cn/http/<enc>/                │
                     │ 网瑞达 WEBVPN（账号 / CAS 统一认证 / 短信 / TOTP）│
                     └───────────────────────────────────────────┘
                                     │ 登录成功
                                     ▼
                     URP 综合教务（清元优软，JSP + frameset，GB2312）
                     cas.hbu.edu.cn  统一身份认证（SPA，有二级验证）
                     ehall.hbu.edu.cn 办事大厅
                     xsfwpt.hbu.cn   学生事务平台
```

关键点：

1. `/http/<enc>/` 的 `<enc>` 是 WebVPN 给这个资源编的路径，**随会话变化**，
   所以原生层不能写死教务 URL；
2. 登录页有图形验证码，CAS 还可能有二次验证；任何「原生表单 POST」都会随改版失效；
3. 登录成功后所有请求都在 `v.hbu.cn` 这个源下，Cookie 绑在这个源上。

因此：登录在 WebView 里完成，取数也在同一个源的页面里做。

## 2. 登录窗口：一次统一认证

`campus/LoginDialog.java`，一个悬浮窗，只有一条主线：**统一认证 → 静默验证教务**。

| 阶段 | 做什么 | 结束条件 |
| --- | --- | --- |
| ENTRY | 打开 `https://v.hbu.cn/login`，自动点「统一认证（CAS登录业务系统）」 | **拿到 WebVPN 会话票** `wengine_vpn_ticket` |
| CHECK | 静默打开 `https://zhjw.hbu.edu.cn/`，看是否已单点进入 | 进到教务页 → 跳过；出现登录页 → 进入 LOGIN |
| LOGIN | 只有教务确实要自己登一次时才显示 | 进到教务页 |
| DONE | 关窗 | — |

```
ENTRY: loadUrl(v.hbu.cn/login)
   ↓ onPageFinished → probe.js → {url, pwd, cas, title}
   ↓ stage(url)
   ├─ LOGIN_CAS / LOGIN_VPN → 提示用户登录（入口页自动点一次统一认证）
   ├─ CALLBACK（ticket= / cas_login=true）→ 什么都不做，等它跳完
   ├─ loginAgain → 提示二次验证
   └─ 拿到 wengine_vpn_ticket → 校园网 OK
   ↓
CHECK: loadUrl(zhjw) → 已登录？→ 跳过；否则 LOGIN
```

### 为什么不能按 URL 猜「登录好了」

CAS 登录成功到 WebVPN 真正建立会话之间，还有一跳：

```
cas 登录成功 → 302 v.hbu.cn/login?cas_login=true&ticket=ST-…
             → WebVPN 拿票换会话 → 302 目标页
```

旧版看到「在 v.hbu.cn 下、URL 里没有 `/login`」就认为登录完成并 `loadUrl` 下一步，
把这一跳打断，票没送到，服务端又打回登录页 —— 表现出来就是「登上了又跳回登录界面」。
所以现在：

- 校园网阶段**只认会话票**，不看 URL 长相；
- `ticket=`、`cas_login=true`、`loginAgain`、CAS 页都算「认证跳转中」，一律不决策、不导航。

### 另一个坑：CAS 前缀不能当教务根

CAS 也走 WebVPN 代理：`https://v.hbu.cn/https/<cas 的编码>/lyuapServer/login`。
旧版把任意 `/https/<hex>/` 都记成教务根地址，登录完成后所有 URP 请求都打到 CAS 前缀上，
服务端仍然返回登录页。现在只从 `/student/` 页面学习教务根（`Portal.noteUrl`），
重新登录时清空（`Portal.clearBase`）。

### 判定规则（`LoginDialog.ready`）

| 平台 | 判定 |
| --- | --- |
| 校园网 | Cookie 里有 `wengine_vpn_ticket`，且当前页不在认证跳转中 |
| URP | 没有可见密码框，不是登录页，地址含 `/student/` 或以 `/index` 结尾，或标题像教务 |
| 办事大厅 | 地址是 `ehall` / `xsfwpt` 且不在登录页 |

办事大厅**不在默认登录流程里**：点「办事大厅」里的功能时才按需登录。
「这步已完成」按钮仍保留，判定不准时用户永远能手动往下走。

## 3. 登录态

`campus/Session.java` 维护每个平台的三态：`UNKNOWN / OK / EXPIRED`。

- `markOk` 只在确认后调用，并写入 `platformAt` 时间戳；
- 冷启动 `bootstrap`：12 小时内确认过的平台先当作 OK，其余待检查；
- `check`：先探测 WebVPN；隧道断了直接把教务与办事大厅标失效（不再白等两次超时），
  否则继续逐个探测；
- `ensure(platforms, action)`：功能入口统一走这里，缺哪个平台就弹登录窗口，
  成功后继续执行原动作；
- 掉线时首页/圈子挂提示（写明哪个平台），我的页显示每个平台的状态。

**课表不经过 `ensure`**：课表是本地数据，没登录也能看、能翻周、能加课；
只有「从教务导入」这一步需要登录。

## 4. Cookie

- 系统 WebView 的 `CookieManager` 自己会落盘，是主存储；
- 额外把 `v.hbu.cn / zhjw.hbu.edu.cn / ehall.hbu.edu.cn / xsfwpt.hbu.cn` 的 Cookie
  快照成 JSON 存在 SharedPreferences，用于 WebView 数据被清后恢复；
- 退出登录：按 Cookie 名逐个 `Max-Age=0` 清掉，并清本地状态；
- 不保存密码，App 进程里也没有任何密码字段。

## 5. 为什么不用 CAS 接口直接登录

`cas.hbu.edu.cn` 是前端 SPA，账号密码、验证码、二级验证都在页面 JS 里跑，
没有稳定公开的接口；逆向后仍然要面对验证码与二次验证，而且改版即失效。
在 WebView 里让学校页面自己完成，是唯一长期稳定的路径 —— 同时天然满足
「密码不经过 App」这条隐私底线。
