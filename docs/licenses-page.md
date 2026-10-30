# 开源许可页

关于页 → 开源许可，独立一屏。版式照系统那张「开放源代码许可」：
每条 = 库名 + 作者 + 许可证徽章，右上角版本号，**整行可点**跳到该项目地址。

配色与组件全部走本项目主题：徽章是绿底绿字（`TextBadge`）而不是参考图的青色，
卡片 12dp 无阴影，分割线贴着文字列。图标集不另立分类，和依赖混排在同一张列表里。

## 数据从哪来

`tools/gen-licenses.py` 生成 `licenses/GeneratedLicenses.kt`，两步：

1. 跑 `:app:dependencies --configuration releaseRuntimeClasspath`，
   **只取依赖树第一层**——也就是 `app/build.gradle.kts` 里我们自己声明的那批，
   以及它们实际解析到的版本。compose 那几支没写版本号、由 BOM 定，
   所以必须问 Gradle，不能猜。
2. 读 Gradle 模块缓存里对应的 `.pom`，取 `name` / `developers` / `licenses` / `url`。
   许可证名按 `LICENSE_ALIAS` 收敛成界面上那种短写法。

跑法（和 `gen-icons.py` 一样，是开发机上的手动步骤，不进构建）：

```
python tools/gen-licenses.py
```

## 为什么不列传递依赖

整棵树七八十支，`androidx.collection-jvm` 这种东西摆到关于页里只是凑数，
用户既看不懂也不关心。列自己直接声明的依赖是够用的口径。

代价：传递依赖的许可没有逐条展示。真要合规到那一步，得换 AboutLibraries 那类
构建期插件——它会把整棵树连同 LICENSE 原文抓下来，代价是多一个构建插件、
多一份产物，而且它的默认界面不是这套配色。目前这个应用不值得。

## 不在 maven 里的东西

图标集 Reicon 手写一条，在 `licenses/OssEntry.kt` 里，生成脚本不碰那个文件。
它的 `version` 填的是钉住的上游提交短 sha（`fe69753`），不是版本号——
这是刻意的，跟 `docs/icons-reicon.md` 里锁的版本是同一个。

## 维护

- 加/删/升一支依赖之后，重跑 `gen-licenses.py` 再提交。
  不重跑的后果是界面上的版本号开始说谎，而这正是这个页面唯一的作用。
- 生成文件头写着 `Auto-generated ... do not edit.`。
- 关于页那行的尾值「N 个」是 `openSourceEntries.size` 算出来的，
  条目数不会和列表对不上。
