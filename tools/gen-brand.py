#!/usr/bin/env python3
"""从 brand/svg 源文件导出位图，供 README、头像、启动图标使用。

    python tools/gen-brand.py            # 全部重新导出
    python tools/gen-brand.py 512 96     # 只导出指定边长

需要 Pillow。浏览器走系统里的 Edge 或 Chrome（headless 截图），
也可用 SIMLET_BROWSER 指向可执行文件。
"""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SVG_DIR = ROOT / "brand" / "svg"
PNG_DIR = ROOT / "brand" / "png"

# 一律按 512 画，小尺寸从 512 缩下来：浏览器给的窗口尺寸有下限（48 直接截不出来），
# 而且缩小比原生画 48px 抗锯齿更干净。
CANVAS = 512

# (源文件, 输出名模板, 边长列表)
JOBS = [
    ("simlet-icon.svg", "simlet-{n}.png", [512, 256, 180, 96, 48]),
    ("simlet-icon-dark.svg", "simlet-dark-{n}.png", [512]),
    ("simlet-avatar.svg", "simlet-avatar-{n}.png", [512]),
    ("simlet-mark.svg", "simlet-mark-{n}.png", [512]),
    ("simlet-icon-mono.svg", "simlet-mono-{n}.png", [512]),
]

CANDIDATES = [
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
]


def browser() -> str:
    chosen = os.environ.get("SIMLET_BROWSER")
    if chosen and Path(chosen).is_file():
        return chosen
    for name in ("msedge", "chrome", "chromium"):
        found = shutil.which(name)
        if found:
            return found
    for path in CANDIDATES:
        if Path(path).is_file():
            return path
    raise SystemExit("找不到 Edge 或 Chrome，设置 SIMLET_BROWSER 指向可执行文件再跑")


def page(svg: str) -> str:
    # 页面底色留透明，铺不铺满由图形自己决定。单色版写的是 currentColor，
    # 只有内联进页面才吃得到这里设定的 color。
    return (
        '<!doctype html>\n<meta charset="utf-8">\n'
        f'<body style="margin:0;background:transparent;color:#131313">{svg}</body>'
    )


def shoot(exe: str, html: Path, out: Path) -> None:
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as profile:
        subprocess.run(
            [
                exe,
                "--headless",
                "--disable-gpu",
                "--hide-scrollbars",
                "--force-device-scale-factor=1",
                f"--user-data-dir={profile}",
                "--default-background-color=00000000",
                f"--window-size={CANVAS},{CANVAS}",
                f"--screenshot={out}",
                html.as_uri(),
            ],
            check=False,
            capture_output=True,
            timeout=120,
        )
        # Windows 上的 msedge.exe 只是启动器：它把活儿交给真正的浏览器进程后立刻返回，
        # 截图文件晚于进程退出出现。等它落地，再等它写完不再变化。
        deadline = time.time() + 30.0
        while not out.exists() and time.time() < deadline:
            time.sleep(0.2)
        if not out.exists():
            raise SystemExit(f"{exe} 没有产出 {out}")
        previous = -1
        while out.stat().st_size != previous:
            previous = out.stat().st_size
            time.sleep(0.2)
        if Image.open(out).size != (CANVAS, CANVAS):
            raise SystemExit(f"{out} 不是 {CANVAS}x{CANVAS}，检查浏览器认不认 --window-size")


def main() -> None:
    wanted = {int(a) for a in sys.argv[1:]}
    PNG_DIR.mkdir(parents=True, exist_ok=True)
    exe = browser()
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as work:
        for source, pattern, sizes in JOBS:
            targets = [s for s in sizes if not wanted or s in wanted]
            if not targets:
                continue
            html = Path(work) / f"{source[:-4]}.html"
            html.write_text(page((SVG_DIR / source).read_text(encoding="utf-8")), encoding="utf-8")
            raw = Path(work) / f"{source[:-4]}.png"
            shoot(exe, html, raw)
            image = Image.open(raw)
            for size in targets:
                out = PNG_DIR / pattern.format(n=size)
                resized = image if size == CANVAS else image.resize((size, size), Image.LANCZOS)
                resized.save(out)
                print(f"{out.relative_to(ROOT)}  {size}x{size}  {out.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
