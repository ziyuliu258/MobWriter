# 墨哺 Logo

生成日期：2026-11-11。使用内置 image_gen 图像生成工具。

墨哺是手机上的 Hexo 博客写作与发布工具。标志将展开的书页与中央留白中的钢笔笔尖结合，保留应用现有的墨黑、朱砂红与米白配色。图案不含文字，方便在桌面小尺寸下辨认。

- `mobwriter-logo-source.png`：图像生成工具返回的原始透明 PNG。
- `mobwriter-app-icon.png`：带米白底的 512 × 512 图标预览。
- `prompt.txt`：生成原始图像使用的完整提示词。
- `archive/ic_launcher_fg-v1.xml`：替换前的图标备份。

执行 `bash scripts/prepare-logo.sh` 可以重新导出 Android PNG 资源（需要 ImageMagick 7）。该脚本只裁取透明留白、缩放和增加布局留白，保留原始图案和透明通道。

启动页使用独立的 `mobwriter_logo`，避免将桌面图标的安全边距带进启动页。桌面图标使用 108 dp 前景画布，图案宽度为 52 dp，并保留原有的米白背景。Android 13 及以上还通过前景的 alpha 通道提供主题单色图标。

适配依据：[Android 官方自适应图标指南](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)。
