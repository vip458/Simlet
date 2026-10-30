#!/usr/bin/env python3
"""把测试机当前屏幕抓成 README 用的图。

用法：
    python tools/shots.py home-light
    python tools/shots.py region-sheet --serial <adb serial>

只做一件事：截屏 -> 等比缩到宽 540 -> 落盘 docs/screenshots/<name>.png。
导航请自己点（每屏用 uiautomator 的 bounds 中心点，别按截图上的像素坐标，
Read 展示截图是缩放过的）。不这么写成一个「一键跑完全部界面」的脚本，
是因为它得靠硬编码坐标找控件，界面一改就静默抓错屏——那种工具比没有更坏。

README 的图统一出自 Redmi Note 14 Pro（1220x2712 / 375dp）。
"""
import argparse
import os
import subprocess
import sys

import cv2
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "screenshots")


def default_adb():
    """优先用 ANDROID_HOME 下的 adb，其次假定它在 PATH 上。"""
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk:
        candidate = os.path.join(sdk, "platform-tools", "adb.exe" if os.name == "nt" else "adb")
        if os.path.isfile(candidate):
            return candidate
    return "adb"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("name", help="文件名，不含扩展名")
    ap.add_argument("--serial", default=os.environ.get("SHOTS_SERIAL"))
    ap.add_argument("--adb", default=os.environ.get("ADB") or default_adb())
    ap.add_argument("--width", type=int, default=540)
    ap.add_argument("--crop-top", type=int, default=110, dest="crop_top")
    args = ap.parse_args()

    if not args.serial:
        sys.exit("要指定设备：--serial xxx（或设环境变量 SHOTS_SERIAL）")

    base = [args.adb, "-s", args.serial]
    raw = subprocess.run(base + ["exec-out", "screencap", "-p"], capture_output=True).stdout
    img = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        sys.exit("截屏失败：设备没连上，或者屏幕是黑的（锁屏/DRM 屏）")

    h, w = img.shape[:2]
    if args.crop_top:
        # 系统状态栏里会带上当日通知图标和时钟，README 的图不该出现这些；
        # 110px 是 Note 14 Pro（520dpi）的状态栏高度，别的机器自己调。
        img = img[args.crop_top:, :, :]
        h = img.shape[0]
    small = cv2.resize(img, (args.width, round(h * args.width / w)), interpolation=cv2.INTER_AREA)
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, args.name + ".png")
    cv2.imwrite(path, small)
    print("%-20s %4dx%-4d -> %dx%d  %3dKB" % (
        args.name, w, h, small.shape[1], small.shape[0], os.path.getsize(path) // 1024))


if __name__ == "__main__":
    main()
