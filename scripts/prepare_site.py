#!/usr/bin/env python3
"""Build download site into site/: index.html (+ APK only for --local)."""
from __future__ import annotations

import re
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAGES_BASE = "https://kylebing.github.io/bbq10-wubi/"
RELEASE_BASE = "https://github.com/KyleBing/bbq10-wubi/releases/download"

DOWNLOAD_BLOCK = """      <table width="72%" border="0" cellpadding="0" cellspacing="0">
        <tr>
          <td align="center" style="padding:0;">
            <a href="__APK__" style="display:block;background:#2E7D32;padding:12px 8px;text-decoration:none;color:#FFFFFF;-webkit-tap-highlight-color:rgba(125,206,130,0.4);">
              <font face="Helvetica, Arial, sans-serif" color="#FFFFFF" size="4"><b>下载 App v__VERSION__</b></font>
            </a>
          </td>
        </tr>
      </table>"""


def version_name() -> str:
    text = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
    m = re.search(r'versionName\s+"([^"]+)"', text)
    if not m:
        raise SystemExit("versionName not found in app/build.gradle")
    return m.group(1)


def write_local_apk_site(out: Path, ver: str, apk_src: Path) -> str:
    if not apk_src.is_file():
        raise SystemExit(f"APK missing: {apk_src} (run assembleDebug first)")
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    apk_name = f"Wubi-{ver}.apk"
    shutil.copy2(apk_src, out / apk_name)
    shutil.copy2(apk_src, out / "wubi.apk")
    build_info = "本地 · " + datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    html = (ROOT / "web" / "local.html").read_text(encoding="utf-8")
    html = (
        html.replace("__VERSION__", ver)
        .replace("__APK__", apk_name)
        .replace("__BUILD_INFO__", build_info)
    )
    (out / "index.html").write_text(html, encoding="utf-8")
    print(f"site ready: local-apk-only  apk={apk_name}  version={ver}  -> {out}/")
    return apk_name


def write_pages_site(out: Path, ver: str, sha: str) -> None:
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    build_info = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    if sha:
        build_info = f"{build_info} · {sha}"
    apk_href = f"{RELEASE_BASE}/v{ver}/Wubi-{ver}.apk"
    download_block = DOWNLOAD_BLOCK.replace("__APK__", apk_href).replace(
        "__VERSION__", ver
    )
    html = (ROOT / "web" / "index.html").read_text(encoding="utf-8")
    html = (
        html.replace("__DOWNLOAD_BLOCK__", download_block)
        .replace("__VERSION__", ver)
        .replace("__APK__", apk_href)
        .replace("__BASE__", PAGES_BASE)
        .replace("__BUILD_INFO__", build_info)
    )
    (out / "index.html").write_text(html, encoding="utf-8")
    (out / "index.htm").write_text(html, encoding="utf-8")
    print(f"site ready: pages  apk={apk_href}  version={ver}")


def main() -> None:
    args = [a for a in sys.argv[1:] if a]
    local = "--local" in args
    args = [a for a in args if a != "--local"]
    ver = version_name()
    apk_src = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
    sha = args[0][:7] if args else ""
    if local:
        write_local_apk_site(ROOT / "dist-local", ver, apk_src)
        return
    write_pages_site(ROOT / "site", ver, sha)


if __name__ == "__main__":
    main()
