package dev.mobwriter.app.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.mobwriter.app.core.model.Block

object ArticleHtml {
    fun page(title: String, blocks: List<Block>): String {
        val body = buildString {
            append("<h1>").append(escape(title.ifBlank { "无题" })).append("</h1>")
            blocks.forEach { append(block(it)) }
        }
        return shell(body)
    }

    fun block(block: Block): String = when (block) {
        is Block.Paragraph -> "<p>${inline(block.text)}</p>"
        is Block.Heading -> "<h${block.level.coerceIn(1, 6)}>${inline(block.text)}</h${block.level.coerceIn(1, 6)}>"
        is Block.Quote -> "<blockquote>${block.alert?.let { "<div class=\"alert\">${escape(it)}</div>" }.orEmpty()}${inline(block.text)}</blockquote>"
        is Block.BulletList -> "<ul>" + block.items.joinToString("") { "<li>${inline(it)}</li>" } + "</ul>"
        is Block.OrderedList -> "<ol>" + block.items.joinToString("") { "<li>${inline(it)}</li>" } + "</ol>"
        is Block.Code -> "<pre><code>${escape(block.code)}</code></pre>"
        is Block.Math -> "<div class=\"display\">$$${block.latex}$$</div>"
        is Block.Image -> "<figure><img src=\"${escape(block.src)}\" alt=\"${escape(block.alt)}\"><figcaption>${escape(block.alt)}</figcaption></figure>"
        is Block.Table -> "<p>${escape(block.header.joinToString(" · "))}</p>" +
            block.rows.joinToString("") { "<p>${escape(it.joinToString(" · "))}</p>" }
        is Block.Rule -> "<hr>"
        is Block.Raw -> "<pre>${escape(block.text)}</pre>"
    }

    fun piece(item: Block): String = shell(block(item))

    fun inline(raw: String): String {
        val math = mutableListOf<String>()
        val kept = StringBuilder()
        var index = 0
        while (index < raw.length) {
            if (raw[index] == '$' && (index + 1 >= raw.length || raw[index + 1] != '$')) {
                val close = raw.indexOf('$', index + 1)
                if (close > index + 1 && !raw.substring(index, close).contains('\n')) {
                    math += raw.substring(index, close + 1)
                    kept.append("\u0000MATH${math.lastIndex}\u0000")
                    index = close + 1
                    continue
                }
            }
            kept.append(raw[index])
            index++
        }
        var html = escape(kept.toString())
        html = html.replace(Regex("""\*\*(.+?)\*\*"""), "<strong>$1</strong>")
        html = html.replace(Regex("""~~(.+?)~~"""), "<del>$1</del>")
        html = html.replace(Regex("""`([^`]+)`"""), "<code>$1</code>")
        html = html.replace(Regex("""\[(.+?)]\((https?://[^)\s]+)\)"""), "<a href=\"$2\">$1</a>")
        html = html.replace(Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)"""), "<em>$1</em>")
        math.forEachIndexed { i, latex ->
            html = html.replace("\u0000MATH$i\u0000", latex)
        }
        return html.replace("\n", "<br>")
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun shell(body: String): String = """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <link rel="stylesheet" href="katex.min.css">
        <style>
          body { margin: 0; color: #1e1a16; font-family: "Noto Serif CJK SC", "Source Han Serif SC", serif; font-size: 17px; line-height: 1.75; background: transparent; }
          h1 { font-size: 28px; line-height: 1.3; } h2 { font-size: 24px; } h3 { font-size: 21px; } h4 { font-size: 18px; } h5 { font-size: 16px; } h6 { font-size: 15px; }
          blockquote { margin: 0.6em 0; padding: 0.15em 0 0.15em 0.9em; border-left: 3px solid #8E3B32; color: #3d3832; }
          .alert { color: #8E3B32; font-size: 12px; letter-spacing: 0.04em; }
          a { color: #8E3B32; }
          code { font-family: ui-monospace, monospace; background: #f3ede3; padding: 0 0.2em; }
          pre { background: #f3ede3; padding: 12px; border-radius: 12px; overflow: auto; }
          .display { margin: 0.8em 0; text-align: center; overflow-x: auto; }
          img { max-width: 100%; }
          figcaption { color: #8a8175; font-size: 13px; }
        </style>
        </head><body>$body
        <script src="katex.min.js"></script>
        <script src="auto-render.min.js"></script>
        <script>
          document.addEventListener("DOMContentLoaded", function () {
            renderMathInElement(document.body, {
              delimiters: [
                {left: "$$", right: "$$", display: true},
                {left: "$", right: "$", display: false}
              ],
              throwOnError: false
            });
            var report = function () { Host.height(Math.ceil(document.body.scrollHeight)); };
            report();
            setTimeout(report, 60);
            setTimeout(report, 240);
          });
        </script>
        </body></html>
    """.trimIndent()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RenderedHtml(
    html: String,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var heightPx by remember(html) { mutableIntStateOf(48) }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                setBackgroundColor(0)
                setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun height(value: Int) {
                        post { if (value > 0) heightPx = value }
                    }
                }, "Host")
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url?.toString().orEmpty()
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            (view.getTag(android.R.id.content) as? (String) -> Unit)?.invoke(url)
                            return true
                        }
                        return false
                    }
                }
            }
        },
        update = { web ->
            web.setTag(android.R.id.content, onLink)
            if (web.tag != html) {
                web.tag = html
                web.loadDataWithBaseURL("file:///android_asset/katex/", html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
        modifier = modifier.height(heightPx.coerceAtLeast(24).dp),
    )
}
