#!/usr/bin/env python3
"""从 Gradle 解析出的运行时依赖生成开源许可清单。

用法：
    python tools/gen-licenses.py

为什么要生成而不是手写：手写清单一定会烂——加一支依赖、升一次版本，
关于页里那条许可信息就开始说谎。这里直接读两样东西：

1. `:app:dependencies --configuration releaseRuntimeClasspath` 的第一层，
   也就是我们自己声明的那批依赖及其**实际解析到的版本**（compose 那几支
   没写版本号，由 BOM 定，所以必须问 Gradle，不能猜）；
2. Gradle 模块缓存里对应的 `.pom`，取 name / developer / license / url。

只列第一层：整棵传递依赖有七八十支，对用户没有意义，
而且 androidx.collection-jvm 这种东西出现在关于页里只会让人觉得在凑数。

不在 maven 里的东西（图标集）手写在一个单独的文件里，本脚本不碰它。
"""

import io
import os
import re
import subprocess
import sys
import glob
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# 用哪个 JDK：JAVA_HOME 优先，其次 PATH 上的 java。本仓库的 gradlew 在部分机器上不可靠，
# 所以这里直连 wrapper jar，但绝不要把某个人的 JDK 安装路径写死进脚本。
_java_home = os.environ.get("JAVA_HOME")
JAVA = (os.path.join(_java_home, "bin", "java.exe" if os.name == "nt" else "java")
        if _java_home else "java")
WRAPPER = os.path.join("gradle", "wrapper", "gradle-wrapper.jar")
MAIN = "org.gradle.wrapper.GradleWrapperMain"
CACHE = os.path.join(os.path.expanduser("~"), ".gradle",
                     "caches", "modules-2", "files-2.1")
OUT = os.path.join(ROOT, "app", "src", "main", "java",
                   "com", "qianxi", "simlet", "licenses", "GeneratedLicenses.kt")

POM_NS = "{http://maven.apache.org/POM/4.0.0}"

# POM 里的许可证名各家写得不统一，界面上要短。
LICENSE_ALIAS = {
    "The Apache Software License, Version 2.0": "Apache-2.0",
    "The Apache License, Version 2.0": "Apache-2.0",
    "Apache License, Version 2.0": "Apache-2.0",
    "Apache-2.0": "Apache-2.0",
    "MIT License": "MIT",
    "The MIT License": "MIT",
    "Eclipse Public License v. 2.0": "EPL-2.0",
    "GNU Lesser General Public License v3.0": "LGPL-3.0",
}

# 这些是构建期/元数据条目，不是运行时库，界面上不该出现。
SKIP_GROUPS = set()


def resolved_direct_deps():
    """跑 Gradle，取依赖树第一层的 g:a:version。"""
    cmd = [JAVA, "-classpath", WRAPPER, MAIN,
           ":app:dependencies", "--configuration", "releaseRuntimeClasspath"]
    text = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True,
                          encoding="utf-8", errors="replace").stdout
    lines = text.splitlines()
    try:
        start = next(i for i, l in enumerate(lines)
                     if l.startswith("releaseRuntimeClasspath"))
    except StopIteration:
        sys.exit("没找到 releaseRuntimeClasspath 这一段，Gradle 输出格式变了？")

    deps = []
    for line in lines[start + 1:]:
        # 第一层以 "+--- " 或 "\--- " 开头，再深一层前面有竖线或空格
        head = re.match(r"^[+\\]--- (.*)$", line)
        if head is None:
            continue
        body = re.sub(r"\s*\(.*\)\s*$", "", head.group(1)).strip()
        if " -> " in body:
            # compose 那几支没写版本，由 BOM 定，Gradle 标成 "g:a -> 1.7.6"
            coord, version = body.split(" -> ", 1)
            parts = coord.split(":")
        else:
            parts = body.split(":")
            version = parts[2] if len(parts) > 2 else None
        if not version or len(parts) < 2 or parts[0] == "project":
            continue
        group, artifact = parts[0], parts[1]
        if f"{group}:{artifact}" == "androidx.compose:compose-bom":
            continue  # 只是版本约束，不是运行时库
        deps.append((group, artifact, version))
        if line.startswith("\\"):
            break
    return deps


def read_pom(group, artifact, version):
    hits = glob.glob(os.path.join(CACHE, group, artifact, version, "*",
                                  f"{artifact}-{version}.pom"))
    if not hits:
        return None
    try:
        return ET.fromstring(io.open(hits[0], encoding="utf-8").read())
    except ET.ParseError:
        return None


def text_of(node, tag):
    child = node.find(f"{POM_NS}{tag}")
    if child is None or child.text is None:
        return None
    return child.text.strip() or None


def parse_entry(group, artifact, version):
    pom = read_pom(group, artifact, version)
    name = (text_of(pom, "name") if pom is not None else None) or artifact
    url = text_of(pom, "url") if pom is not None else None
    if not url and pom is not None:
        scm = pom.find(f"{POM_NS}scm")
        if scm is not None:
            url = text_of(scm, "url")
    author = None
    license_name = None
    if pom is not None:
        devs = pom.findall(f"{POM_NS}developers/{POM_NS}developer/{POM_NS}name")
        if devs and devs[0].text:
            author = devs[0].text.strip()
        if not author:
            author = text_of(pom.find(f"{POM_NS}organization"), "name") \
                if pom.find(f"{POM_NS}organization") is not None else None
        lic = pom.find(f"{POM_NS}licenses/{POM_NS}license/{POM_NS}name")
        if lic is not None and lic.text:
            license_name = lic.text.strip()
    if not author:
        author = group.split(".")[0]
    if not license_name:
        license_name = "未知"
    if not url:
        url = f"https://central.sonatype.com/artifact/{group}/{artifact}"
    return {
        "name": name,
        "author": author,
        "license": LICENSE_ALIAS.get(license_name, license_name),
        "version": version,
        "url": url,
    }


def kotlin_literal(value):
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def main():
    deps = resolved_direct_deps()
    if not deps:
        sys.exit("依赖树第一层是空的，先确认能跑 :app:dependencies")

    entries = []
    for group, artifact, version in deps:
        if group in SKIP_GROUPS:
            continue
        entries.append(parse_entry(group, artifact, version))

    entries.sort(key=lambda e: e["name"].lower())

    body = []
    for e in entries:
        body.append(
            "    OssEntry(\n"
            f"        name = {kotlin_literal(e['name'])},\n"
            f"        author = {kotlin_literal(e['author'])},\n"
            f"        license = {kotlin_literal(e['license'])},\n"
            f"        version = {kotlin_literal(e['version'])},\n"
            f"        url = {kotlin_literal(e['url'])}\n"
            "    ),"
        )

    content = (
        "// Auto-generated by tools/gen-licenses.py - do not edit.\n"
        "package com.qianxi.simlet.licenses\n"
        "\n"
        "/** 我们自己声明的那批运行时依赖，按库名排序。 */\n"
        "val mavenLicenses: List<OssEntry> = listOf(\n"
        + "\n".join(body)
        + "\n)\n"
    )
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    io.open(OUT, "w", encoding="utf-8", newline="\n").write(content)
    print(f"生成 {len(entries)} 条 -> {os.path.relpath(OUT, ROOT)}")
    for e in entries:
        print(f"  {e['name']}  {e['version']}  {e['license']}  {e['url']}")


if __name__ == "__main__":
    main()
