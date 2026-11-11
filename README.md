# 墨哺 MobWriter

<img src="branding/mobwriter-app-icon.png" alt="墨哺：书页与钢笔笔尖" width="128" />

在手机上写 Hexo 文章。正文按段落、标题、引用、列表、代码和公式来排，不手写 Markdown 符号。写完点发布，文章会提交到 Hexo 仓库的 `source/_posts`。

公式是单独的一块。点「公式」后只在框里写 LaTeX，例如 `\alpha_t + \beta`，不用自己加 `$$`。句子中间的短公式用「行内公式」，美元符号也不会露出来。

## 打开

用 Android Studio 打开项目目录，连上手机运行。桌面名称是墨哺，英文名是 MobWriter。应用包名是 `dev.mobwriter.app`。工程自带 Gradle Wrapper，Gradle 发行包使用腾讯镜像。

第一次打开要填 GitHub 令牌。经典令牌勾选 `repo`；如果还要在手机上触发自动构建，再加上 `workflow`。细粒度令牌则给这个仓库的 Contents 读写，以及 Actions 读写。

首次使用需自行填写仓库所有者和仓库名，应用不内置任何个人仓库。分支和文章目录默认是 `main` 与 `source/_posts`，可按 Hexo 项目修改。文章目录按你仓库的实际位置填，不假定站点挂在子路径还是域名根目录。

## 怎么写

- 标题、分类、标签、系列在正文上方，点一下加上，不用写 YAML。
- 选中文字后点「粗体」「斜体」「链接」。
- 回车另起一段。
- 「公式」插入公式块，里面直接写 LaTeX，可以写多行。
- 「代码块」同样只写代码本身。
- 图片从相册选取，默认放进与文章同名的资源文件夹。如果图片统一放在 `source/images`，到设置里改成这个方式，正文会写成 `/images/文件名`。

写到一半会留在手机里。发布成功后，这篇本地未提交的修改会清掉。

## 发布

「发布」把 Markdown 提交到文章目录。「存草稿」提交到同级的 `source/_drafts`，Hexo 默认不会把它生成出来。

如果仓库里放了 `deploy/publish.yml`（复制到 Hexo 仓库的 `.github/workflows/publish.yml`），发布后会触发 `hexo generate --deploy`。模板读取该项目的 Hexo 配置，不限定服务器、域名或部署目录。博客项目需自行安装所选部署方式对应的 Hexo 插件。

私有域名和部署地址放在被忽略的 `config/hexo-private.local.yml` 中（从空白示例复制），再由使用者配置为 Hexo 仓库的 `HEXO_PRIVATE_CONFIG` Secret。工作流将它作为第二份配置合并，详见 [Hexo 多配置说明](https://hexo.io/docs/configuration#Using-an-Alternate-Config)。Git SSH 部署还需 `HEXO_DEPLOY_KEY` 和已核验的 `HEXO_KNOWN_HOSTS`；自定义 SSH 配置可放在 `HEXO_SSH_CONFIG`。不要把这些值写进工作流或公开配置。

没有这个工作流时，文章仍然会写进仓库，站点要等下次在电脑上部署后更新。

## 开发与构建

需要 JDK 17 或更高版本，以及 Android SDK 35。Android Studio 会生成本机的 `local.properties`；也可以自行设置 `sdk.dir` 指向 Android SDK 目录。该文件不提交到仓库。

```sh
git clone <repository-url> MobWriter
cd MobWriter
./gradlew :app:assembleDebug
./gradlew :core:test :app:lintDebug
```

调试安装包生成在 `app/build/outputs/apk/debug/app-debug.apk`。本地整理后的安装包放在 `dist/`，构建缓存与 APK 不纳入 Git。

## 项目目录

- `app/`：Android 应用、Compose 界面与桌面图标资源。
- `core/`：Markdown、Hexo、GitHub 操作与单元测试。
- `branding/`：新 Logo 原图、预览、生成提示词及旧图标备份，详见 [Logo 说明](branding/README.md)。
- `scripts/prepare-logo.sh`：使用 ImageMagick 7 从原图导出 Android 图标资源。
- `deploy/publish.yml`：供 Hexo 博客仓库使用的发布工作流模板。
- `gradle/`：Gradle Wrapper 配置。

## 本机配置与隐私

源码、测试和文档使用通用示例；应用首次启动时由使用者填写自己的仓库和令牌，数据保存在手机本地。服务地址和个人账户不会由构建脚本注入 APK。

开发者可从 `config/private.example.properties` 复制一份 `config/private.local.properties`，填写本机参考参数和可选的 `test.postsDirectory`。只有显式执行 `./gradlew :core:test -PincludeLocalPosts=true` 才会读取真实博客作为测试样本；常规测试不访问本机博客。

`config/*.local.*`、`local.properties`、私钥、安装包、生成配置和构建目录均被忽略。提交前运行 `python3 scripts/check-privacy.py`；它只报告问题所在文件与类别，不打印私密值。可用 `git config core.hooksPath .githooks` 启用本地提交检查。Git 提交身份遵循用户自己的配置。
