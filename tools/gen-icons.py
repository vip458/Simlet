#!/usr/bin/env python3
"""重新生成 vendored 的 Reicon 图标。

用法：
    python tools/gen-icons.py

图标集是**子集**，不是上游整包。上游的 JitPack 发布目前不可用
（所有 tag 构建失败、README 给的 dev.reicon:reicon-compose 坐标 404、
且它 api 依赖 compose-bom 2026.04.01 会强制我们升 Compose 大版本），
所以我们直接用它自带的生成器把 ImageVector 源码抽出来，零新增依赖。

新增图标的流程：把 kebab 名字加进 ICONS 列表 -> 跑本脚本 -> 编译装机看效果。
名字在 https://reicon.dev/icons 浏览，或查 data/icons-names-categories.json。
"""

import importlib.util
import json
import os
import sys
import urllib.request

# 钉住上游提交，保证重跑产出可复现
UPSTREAM_SHA = "fe6975391b80c808c25d98b2a21081cf180482f9"
RAW = f"https://raw.githubusercontent.com/dqev/reicon/{UPSTREAM_SHA}"
DATA_URL = f"{RAW}/data/icon-data.json"
TOOL_URL = f"{RAW}/packages/reicon-compose/tool/build_kotlin.py"

ICONS = [
    "arrow-left",    # 顶栏返回
    "chevron-expand-y",  # 行尾「点开选择器」的上下箭头
    "chevron-right", # 行尾箭头
    "chevron-down",  # 运营商分组：收起
    "chevron-up",    # 运营商分组：展开
    "copy",          # 公众号弹层 · 复制名称
    "code",          # 关于页 · 开源许可
    "copyright",     # 关于页 · 第三方图标
    "globe",         # 关于页 · 项目主页
    "help-circle",   # 首页顶栏 · 使用提示（标准问号，不用 help 那支泳圈）
    "info-circle",   # 设置 · 关于
    "language",      # 设置 · 语言
    "moon",          # 设置 · 明暗模式
    "qr",            # 设置 · 公众号渠道
    "refresh",       # 刷新 / 检查更新 / 重新检测
    "search",        # 两个弹层的搜索框
    "settings",      # 首页顶栏设置 / 环境自检主图
    "shield",        # 设置 · 权限与数据
    "sim",           # SIM 卡行
    "tag",           # 设置 · 当前版本
    "tower",         # 运营商名称（signal 那支画的是节点图，不像运营商）
]

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, ".gradle", "reicon-cache")
OUT = os.path.join(ROOT, "app", "src", "main", "java", "dev", "reicon")


def fetch(url, dest):
    if os.path.exists(dest):
        return dest
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    print(f"fetch {url}")
    with urllib.request.urlopen(url, timeout=180) as response:
        payload = response.read()
    with open(dest, "wb") as handle:
        handle.write(payload)
    return dest


def main():
    data = fetch(DATA_URL, os.path.join(CACHE, "icon-data.json"))
    tool = fetch(TOOL_URL, os.path.join(CACHE, "build_kotlin.py"))

    spec = importlib.util.spec_from_file_location("reicon_build_kotlin", tool)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.DATA_PATH = data

    known = set()
    with open(data, encoding="utf-8") as handle:
        for category in json.load(handle).get("categories", {}).values():
            known.update((category.get("icons") or {}).keys())
    missing = [name for name in ICONS if name not in known]
    if missing:
        sys.exit(f"上游没有这些图标名，先去掉或改名：{missing}")

    os.makedirs(OUT, exist_ok=True)
    # 图标文件全量重建：生成器只覆盖子集内的名字，从 ICONS 里删掉的会留成孤儿文件。
    # Reicon.kt 是上游的便捷包装，本应用不用它，但生成器只在文件缺失时写入，
    # 所以把它排除在清理之外——留着当占位，删了下次重跑会被原样还原，反而更绕。
    for existing in os.listdir(OUT):
        if existing.endswith(".kt") and existing != "Reicon.kt":
            os.remove(os.path.join(OUT, existing))
    module.generate(set(ICONS), OUT, None)
    print(f"\n生成 {len(ICONS)} 个图标 -> {os.path.relpath(OUT, ROOT)}")
    print("注意：Reicon.kt 只在首次生成时写入，手工改过不会被覆盖。")


if __name__ == "__main__":
    main()
