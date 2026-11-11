# 墨哺 MobWriter

<img src="branding/mobwriter-app-icon.png" alt="墨哺应用图标" width="128" />

墨哺是一款 Android 应用，用来在手机上方便地编辑 Hexo 博客。

写文章、修改文章和提交到 GitHub 仓库可以在同一处完成。正文按段落、标题、引用、列表、代码和公式组织，不必手写 Markdown 标记；标题、分类、标签等信息在界面中填写。完成后可以发布到 Hexo 的文章目录，也可以存成草稿。

## 功能

- 打开仓库中已有的文章，或新建一篇。
- 在所见即所得、源代码、阅读和打字机四种视图之间切换。
- 单独填写标题、分类、标签、系列、置顶和音乐播放器。
- 为选中的文字设置粗体、斜体、删除线、行内代码、链接和行内公式。
- 插入标题、引用、有序和无序列表、代码块、公式块、分割线和图片。公式块与代码块只填写内容本身。
- 从相册插入图片。默认与文章放在同一资源目录；也可以在设置中改为 `source/images`，正文写成 `/images/文件名`。
- 写作过程中的修改保存在手机上。发布到文章目录成功后，这篇本地修改会被清除。
- 发布提交到文章目录，默认是 `source/_posts`。存草稿写入同级的 `_drafts`；编辑已发布文章时，草稿是一份副本，已发布的原文不会被替换。Hexo 默认不会生成草稿。
- 可以在发布后触发仓库中的 GitHub Actions，执行 `hexo generate --deploy`。自动构建可在设置中关闭。

## 环境

- 手机系统为 Android 8.0（API 26）或更高版本。
- 从源码构建需要 JDK 17 或更高版本，以及 Android SDK 35。

## 构建与首次配置

用 Android Studio 打开项目目录并运行，或在命令行构建：

```sh
git clone <repository-url> MobWriter
cd MobWriter
./gradlew :app:assembleDebug
```

调试安装包位于 `app/build/outputs/apk/debug/app-debug.apk`。Android Studio 会生成本机的 `local.properties`，也可以自行填写 `sdk.dir`。该文件不提交到仓库。

第一次打开时填写：

1. **GitHub 令牌。** 令牌只保存在这台手机上。经典令牌勾选 `repo`；若要在手机上触发自动构建，再勾选 `workflow`。细粒度令牌授予该仓库的 Contents 读写权限；触发工作流时同时授予 Actions 读写权限。
2. **仓库所有者和仓库名。** 应用不内置任何仓库。
3. **分支和文章目录。** 默认是 `main` 和 `source/_posts`，按实际的 Hexo 项目修改。

## 发布到站点

发布会把 Markdown 提交到文章目录。应用中打开了自动构建，且仓库里有对应工作流时，发布后会触发该工作流。

把 [`deploy/publish.yml`](deploy/publish.yml) 复制为 Hexo 仓库的 `.github/workflows/publish.yml`。私密配置和密钥的写法在模板里。没有这份工作流时，文章仍会写入仓库，站点在下次部署后更新。

## 开发

```sh
./gradlew :core:test :app:lintDebug
```

个人参数放在被忽略的 `config/*.local.*` 中，可从 `config/private.example.properties` 复制。常规测试不读取本机博客。只有执行 `./gradlew :core:test -PincludeLocalPosts=true` 时，才会使用其中的 `test.postsDirectory`。

提交前运行 `python3 scripts/check-privacy.py`。它只报告问题所在文件和类别，不打印配置内容。执行 `git config core.hooksPath .githooks` 可以启用本地提交检查。

`app/` 是 Android 应用，`core/` 是 Markdown、Hexo 与 GitHub 逻辑，`deploy/publish.yml` 是博客仓库可用的工作流模板。
