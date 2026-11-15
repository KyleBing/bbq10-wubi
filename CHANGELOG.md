# 更新日志

版本号与 `versionCode` 对齐，形如 `1.0.0.N`（与 BlackBerry 10 系统显示一致）。

## 1.0.0.5 — 2026-11-15

### 变更
- 候选顺序改为 Rime 词序：整码优先，再按 weight，用户词不再一律置顶。
- 空格上屏首选后不再额外插入空格。
- 已登录时隐藏邮箱密码框；同步操作放到页面下部。
- 复制 / 清空 / 中英文切到候选栏下方，并加大「中」与「主页」点击区域。

## 1.0.0.4 — 2026-11-15

### 修复
- 登录仍走系统 OpenSSL（`s23_clnt.c` handshake aborted）：Android 4.3 没有 AES-GCM，且自带旧 BouncyCastle 会挡住新库。改为重定位后的纯 Java TLS 1.2 直连门户。

## 1.0.0.3 — 2026-11-15

### 变更
- 使用 iOS 玫枫五笔同一套 AppIcon。

## 1.0.0.2 — 2026-11-15

### 修复
- Q10 实体键盘字母进不了五笔：拦截 InputConnection，并用按键码补 unicode=0 的情况。
- 不再占用 SYM 键切中英文（改点左上角「中 / En」）。
- 登录 SSL Handshake：Android 4.3 无 AES-GCM、不信任 Let's Encrypt，改用 BouncyCastle TLS 1.2 + ISRG Root X1。

## 1.0.0.1 — 2026-11-15

### 新增
- Q10 实体键盘五笔输入本：正文在上、候选在下，复制后到其它 App 粘贴。
- 与 iOS 玫枫五笔同一套 `wubi86_jidian` 码表、四码唯一、空码清空、回车清码、z 拼音反查、date/time/week、中文标点。
- 门户用户词同步（上传 / 下载 / 合并 / 自动同步）与扩展词库拉取。
- 本地下载页与 GitHub Pages / Release 更新流程（对齐 bbq10-vocabulary）。
