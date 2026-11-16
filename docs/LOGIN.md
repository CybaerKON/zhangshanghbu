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

## 2. 顺序登录窗口

`campus/LoginDialog.java`，一个悬浮窗，按平台顺序推进：

| 步骤 | 地址 | 说明 |
| --- | --- | --- |
| 1 校园网（WebVPN） | `https://v.hbu.cn/` | 校外访问教务的必经隧道；页面里可选 CAS 统一认证或账号登录 |
| 2 本科教务（URP） | `https://zhjw.hbu.edu.cn/` | 若统一认证已 SSO，会直接进主页；否则再登一次 |
| 3 办事大厅 | `https://ehall.hbu.edu.cn/` | 可「稍后再说」跳过，不影响课表与成绩 |

每一步：

```
loadUrl(平台首页)
   ↓ onPageFinished
注入 probe.js → 返回 {url, title, pwd, name, user, logout}
   ↓
判定 isLogged(platform, url, pwd)
   ├─ 是 → 快照 Cookie / 标记该平台在线 / 进入下一步
   └─ 否 → 状态写「请在窗口里完成 X 登录」，等用户操作
```

### 判定规则（`LoginDialog.isLogged`）

| 平台 | 判定 |
| --- | --- |
| WebVPN | 没有可见密码框，且地址不是 `/login`，且（在 `/http/` 下 或 Cookie 里有 vpn 令牌） |
| URP | 没有可见密码框，不是 `loginAction`，且地址含 `index.jsp` / `main.jsp` / `xskbcx` / `grade`，或标题像教务 |
| 办事大厅 | 地址是 `ehall` / `xsfwpt` 且不在登录页 |

只看 URL 会误判（WebVPN 登录后的资源门户也没有密码框），只看密码框也会误判
（部分页面把登录表单藏在弹窗里）。所以两者结合，并且**每步都允许手动推进**：
「这步已完成」按钮不受判定影响，用户永远能往下走。

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
