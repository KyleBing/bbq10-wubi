# BBQ10 五笔

BlackBerry Q10（720×720 / Android 4.3）五笔输入本。

系统输入法打不了这套五笔，所以这是一个独立 App：上面编辑正文，下面显示编码和候选，用实体键盘打字，再复制到其它地方粘贴。

词库与 [iOS 玫枫五笔](https://github.com/KyleBing) 相同：`wubi86_jidian` + 用户词 + 扩展词。Q10 是 API 18，现代 NDK 编出来的 librime 最低要 API 21，因此引擎在 Java 里按同一套 Rime 码表和 iOS 输入习惯实现。

下载（推送 `main` 并开启 Pages 后）：https://kylebing.github.io/bbq10-wubi/

## 本地构建

```bash
./gradlew :app:assembleDebug
./scripts/serve_local.sh          # 默认 http://127.0.0.1:8765/
```

APK 文件名：`Wubi-<versionName>.apk`。本地页在 `dist-local/`；线上 Pages 链到 GitHub Release。

## 输入

- `a–y` 五笔码，最多 4 码；空格上屏首选，`1–9` 选对应候选
- 组词时 `,` / `.` 或 `-` / `=` 翻页
- `SYM` 中英文；`Shift+字母` 直接大写
- `z` + 拼音反查；`date` / `time` / `week` 出当前时间
- 未组词时的标点按 iOS 习惯出中文标点

## 词库同步

设置 → 同步账号，登录 https://kylebing.cn/wubi/portal/  
与 macOS / iOS / Windows / 码表助手共用用户词。扩展词库只从云端拉取覆盖本地。

注册：https://kylebing.cn/wubi/manager/#/register

## CI

- **Build Android APK**：PR / push `main` 编译并上传 APK
- **Build & Deploy Site**：push `main` 后写下载页到 GitHub Pages
- **Release**：`versionName` 尚无对应 tag 时自动发 Release

首次部署在仓库 Settings → Pages → Source 选 **GitHub Actions**。
