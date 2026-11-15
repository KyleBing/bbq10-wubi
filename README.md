# BBQ10 五笔

BlackBerry Q10（720×720 / Android 4.3）五笔输入本。

系统输入法打不了这套五笔，所以这是一个独立 App：上面编辑正文，下面显示编码和候选，底部是复制 / 清空 / 中英文；用实体键盘打字，再复制到其它地方粘贴。

词库与 [iOS 玫枫五笔](https://github.com/KyleBing) 相同：`wubi86_jidian` + 用户词 + 扩展词。Q10 是 API 18，现代 NDK 编出来的 librime 最低要 API 21，因此引擎在 Java 里按同一套 Rime 码表和输入习惯实现。

下载：https://kylebing.github.io/bbq10-wubi/

## 功能概括

- 实体键盘五笔输入本（正文 + 候选 + 复制）
- Rime 词序：整码优先，按 weight 排序
- 四码唯一自动上屏、空码清空、回车清码（可在设置开关）
- `z` + 拼音反查（标注五笔码）；候选可展开多行
- `date` / `time` / `week` 快捷日期时间
- 中文标点；门户用户词同步与扩展词库拉取

## 使用说明

1. 安装 APK，打开 App，用 Q10 实体键盘打字。
2. `a–y` 输入编码（最多 4 码）；`空格` 上屏首选；`1–9` 选对应候选。候选多时点「展开」多行查看。
3. `z` + 拼音反查汉字，候选后显示五笔码。组词时 `,` / `.` 或 `-` / `=` 翻页；点候选也可上屏。
4. 底部点「中 / En」切换中英文；`Shift+字母` 直接大写，不进五笔。
5. `删除` 退一码；无编码时删正文。`返回` 在组词中先清码。
6. 打完后点「复制」，到其它 App 粘贴。
7. 「…」→ 设置 / 同步账号：登录 [门户](https://kylebing.cn/wubi/portal/)，与 macOS / iOS / 码表助手共用用户词；可上传、下载、合并，也可拉扩展词库。

注册：https://kylebing.cn/wubi/manager/#/register

## 本地构建

```bash
./gradlew :app:assembleDebug
./scripts/serve_local.sh          # 默认 http://127.0.0.1:8765/
```

APK 文件名：`Wubi-<versionName>.apk`。本地页在 `dist-local/`；线上 Pages 链到 GitHub Release。

## CI

- **Build Android APK**：PR / push `main` 编译并上传 APK
- **Build & Deploy Site**：push `main` 后写下载页到 GitHub Pages
- **Release**：`versionName` 尚无对应 tag 时自动发 Release

首次部署在仓库 Settings → Pages → Source 选 **GitHub Actions**。
