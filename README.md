# 墨哺 MobWriter

<img src="branding/mobwriter-app-icon.png" alt="墨哺应用图标" width="128" />

墨哺是一款 Android 应用，用来在手机上编辑 Hexo 或 Astro 博客。站点类型在设置里选择。

写文章、修改文章和提交到 GitHub 仓库可以在同一处完成。正文按段落、标题、引用、列表、代码和公式组织，不必手写 Markdown 标记；标题、分类、标签等信息在界面中填写。完成后可以发布到 Hexo 的文章目录，也可以存成草稿。

## 功能

- 打开仓库中已有的文章，或新建一篇。
- 在所见即所得、源代码、阅读和打字机四种视图之间切换。
- 单独填写标题、分类、标签、系列、置顶和音乐播放器。
- 为选中的文字设置粗体、斜体、删除线、行内代码、链接和行内公式。
- 插入标题、引用、有序和无序列表、代码块、公式块、分割线和图片。公式块与代码块只填写内容本身。
- 从相册插入图片。选图后会先按长边缩小，再压成 WebP。Hexo 默认同文章目录，也可以改成 `source/` 下的图片目录。Astro 放到 `src/assets/imgs/`，正文写成相对路径。
- 写作过程中的修改保存在手机上。发布到文章目录成功后，这篇本地修改会被清除。
- 发布提交到文章目录，默认是 `source/_posts`。存草稿写入同级的 `_drafts`；编辑已发布文章时，草稿是一份副本，已发布的原文不会被替换。Hexo 默认不会生成草稿。
- 可以在发布后触发仓库中的 GitHub Actions，执行 `hexo generate --deploy`。自动构建可在设置中关闭。
- 底部有「主页」和「我的」。文章列表在主页，设置在「我的」里。
- 设置里的「检查更新」读取 GitHub Release。可以下载时直接安装；不能安装时打开发布页面。

## 环境

- 手机系统为 Android 15（API 35）或更高版本。
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

## 在手机上安装新版本

[`.github/workflows/release-apk.yml`](.github/workflows/release-apk.yml) 会构建安装包并发布到 GitHub Release。合并到 `master` 之后，工作流先跑仓库测试。测试通过后，如果已经有正式标签还没有 Release，就给最早的那个标签补上 Release：安装包从该标签已经指向的提交构建，标签不会被移动，也不会改去分配下一个版本号。只有每个正式标签都已经有 Release 时，才把修订号加一。因此 `v0.1.2` 已经指向旧提交但还没有 Release 时，下一次自动发布仍然是 `v0.1.2`，而不是 `v0.1.3`。另外两种方式仍然可用：

- 推送正式版本标签，例如 `v1.2.0`。标签使用 `v主版本.次版本.修订号`。人工推送的正式标签只发布该标签，不会再分配下一个号。
- 在 GitHub 的 Actions 页面手动运行 Release Android package。留空标签时，规则与合并到 `master` 相同。如果填写一个已经存在的正式标签，工作流只发布那个标签，并且从它已经指向的提交构建；标签不存在时不会新建。

发布内容是 `MobWriter.apk` 和 `update.json`。应用用 `update.json` 里的 `versionCode` 判断是否比已安装的版本新。工作流把 `versionCode` 设为该工作流的运行编号，并且必须大于已经发布过的 `versionCode`。更新地址在构建时写入安装包，源码不写死账号或仓库名。

发布包只用一把固定签名，这样后一包才能覆盖安装前一包。不要把密钥文件提交到仓库，把下面四个 Secrets 配齐；缺任何一个时工作流会失败，不会临时生成调试钥匙：

- `ANDROID_KEYSTORE_BASE64`：密钥库的 base64。
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

同一次合并只会发布一个包。自动推送的标签不会再启动第二次发布，也不会移动已经存在的标签。已经用调试签名装上的旧包签的是另一把钥匙，装这把钥匙的第一包之前要先卸载一次。卸载会清掉手机上的令牌和草稿。

手机上打开「我的」，进入「设置」，点「检查更新」。已配置更新源且 Release 里有更高的 `versionCode` 时，应用下载 APK 并打开系统安装确认；如果系统不允许直接安装，或下载失败，就打开这个 Release 页面。

## 开发

```sh
./gradlew :core:test :app:lintDebug
```

个人参数放在被忽略的 `config/*.local.*` 中，可从 `config/private.example.properties` 复制。常规测试不读取本机博客。只有执行 `./gradlew :core:test -PincludeLocalPosts=true` 时，才会使用其中的 `test.postsDirectory`。

提交前运行 `python3 scripts/check-privacy.py`。它只报告问题所在文件和类别，不打印配置内容。执行 `git config core.hooksPath .githooks` 可以启用本地提交检查。

`app/` 是 Android 应用，`core/` 是 Markdown、Hexo 与 GitHub 逻辑，`deploy/publish.yml` 是博客仓库可用的工作流模板。`.github/workflows/release-apk.yml` 用来发布墨哺自己的安装包。
