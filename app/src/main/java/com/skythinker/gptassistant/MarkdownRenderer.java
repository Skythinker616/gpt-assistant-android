package com.skythinker.gptassistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.LeadingMarginSpan;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Image;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.LinkResolver;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.core.spans.LinkSpan;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.ext.tables.TableAwareMovementMethod;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.image.ImageProps;
import io.noties.markwon.image.ImageSize;
import io.noties.markwon.image.ImageSizeResolverDef;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.noties.markwon.movement.MovementMethodPlugin;
import io.noties.markwon.syntax.Prism4jThemeDefault;
import io.noties.markwon.syntax.SyntaxHighlightPlugin;
import io.noties.markwon.utils.LeadingMarginUtils;
import io.noties.prism4j.Prism4j;

public class MarkdownRenderer {
    private final Context context;
    private final Markwon markwon;

    class ClickToCopySpan extends ClickableSpan {
        @Override
        public void onClick(@NonNull View widget) {
            if(widget instanceof TextView) {
                Spanned spanned = (Spanned) ((TextView) widget).getText();
                int start = spanned.getSpanStart(this);
                int end = spanned.getSpanEnd(this);
                String text = spanned.subSequence(start, end).toString().trim();
                GlobalUtils.copyToClipboard(context, text);
                GlobalUtils.showToast(context, context.getString(R.string.toast_code_clipboard), false);
            }
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) { }
    }

    class CopyIconSpan implements LeadingMarginSpan {
        @Override
        public int getLeadingMargin(boolean first) { return 0; }

        @Override
        public void drawLeadingMargin(@NonNull Canvas canvas, @NonNull Paint p, int x, int dir, int top, int baseline, int bottom, @NonNull CharSequence text, int start, int end, boolean first, @NonNull Layout layout) {
            if (!LeadingMarginUtils.selfStart(start, text, this)) return; // 仅处理第一行

            int save = canvas.save();
            try {
                Paint paint = new Paint();
                String textToDraw = context.getString(R.string.text_copy_code_notice);
                paint.setTextSize(GlobalUtils.dpToPx(context, 14));
                paint.setColor(0x50000000);
                Rect bounds = new Rect();
                paint.getTextBounds(textToDraw, 0, textToDraw.length(), bounds);
                int y = top + bounds.height() + GlobalUtils.dpToPx(context, 4);
                int x1 = layout.getWidth() - bounds.width() - GlobalUtils.dpToPx(context, 8);
                if(layout.getWidth() > bounds.width() + GlobalUtils.dpToPx(context, 16))
                    canvas.drawText(textToDraw, x1, y, paint);
            } finally {
                canvas.restoreToCount(save);
            }
        }
    }

    class ImageLinkResolver implements LinkResolver {
        private LinkResolver original;

        public ImageLinkResolver(LinkResolver original) {
            this.original = original;
        }

        @Override
        public void resolve(@NonNull View view, @NonNull String link) {
            Log.d("markdown", "Image resolver link = " + link);
            original.resolve(view, link);
        }
    }

    public MarkdownRenderer(Context context) {
        this.context = context;
        markwon = Markwon.builder(context)
                .usePlugin(SyntaxHighlightPlugin.create(new Prism4j(new GrammarLocatorDef()), Prism4jThemeDefault.create(0)))
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder) {
                        builder.appendFactory(FencedCodeBlock.class, (configuration, props) -> new ClickToCopySpan());
//                        builder.appendFactory(FencedCodeBlock.class, (configuration, props) -> new CopyIconSpan());
                    }
                })
                .usePlugin(JLatexMathPlugin.create(40, builder -> builder.inlinesEnabled(true)))
                .usePlugin(ImagesPlugin.create())
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(new AbstractMarkwonPlugin() {
                    @NonNull
                    @Override
                    public String processMarkdown(@NonNull String markdown) { // 预处理MD文本
                        List<String> sepList = new ArrayList<>(Arrays.asList(markdown.split("```", -1)));
                        for (int i = 0; i < sepList.size(); i += 2) { // 跳过代码块不处理
                            // 解决仅能渲染“$$...$$”公式的问题
                            String regexDollar = "(?<!\\$)\\$(?!\\$)([^\\n]*?)(?<!\\$)\\$(?!\\$)"; // 匹配单行内的“$...$”
                            String regexBrackets = "(?s)\\\\\\[(.*?)\\\\\\]"; // 跨行匹配“\[...\]”
                            String regexParentheses = "\\\\\\(([^\\n]*?)\\\\\\)"; // 匹配单行内的“\(...\)”
                            String latexReplacement = "\\$\\$$1\\$\\$"; // 替换为“$$...$$”
                            // 为图片添加指向同一URL的链接
                            String regexImage = "!\\[(.*?)\\]\\((.*?)\\)"; // 匹配“![...](...)”
                            String imageReplacement = "[$0]($2)"; // 替换为“[![...](...)](...)”
                            // 将开头的<think>内容替换为代码块
                            String regexThinkComplete = "(?s)^<think>\\n(.*?)\\n</think>\\n"; // 匹配开头的“<think>...</think>”
                            String thinkCompleteReplacement = "```text\n" + context.getString(R.string.text_think_header) + "\n\n$1\n```\n"; // 替换为代码块
                            String regexThinkStart = "(?s)^<think>\\n(.*?)$"; // 匹配开头的“<think>...”到结尾
                            String thinkStartReplacement = "```text\n" + context.getString(R.string.text_thinking_header) + "\n\n$1\n```\n"; // 替换为代码块
                            // 进行替换
                            sepList.set(i, sepList.get(i).replaceAll(regexDollar, latexReplacement)
                                    .replaceAll(regexBrackets, latexReplacement)
                                    .replaceAll(regexParentheses, latexReplacement)
                                    .replaceAll(regexImage, imageReplacement)
                                    .replaceAll(regexThinkComplete, thinkCompleteReplacement)
                                    .replaceAll(regexThinkStart, thinkStartReplacement));
                        }
                        return String.join("```", sepList);
                    }
                })
                .usePlugin(new AbstractMarkwonPlugin() { // 设置图片大小
                    @Override
                    public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
                        builder.imageSizeResolver(new ImageSizeResolverDef(){
                            @NonNull @Override
                            protected Rect resolveImageSize(@Nullable ImageSize imageSize, @NonNull Rect imageBounds, int canvasWidth, float textSize) {
                                int maxSize = GlobalUtils.dpToPx(context, 120);
                                if(imageBounds.width() > maxSize || imageBounds.height() > maxSize) {
                                    float ratio = Math.min((float)maxSize / imageBounds.width(), (float)maxSize / imageBounds.height());
                                    imageBounds.right = imageBounds.left + (int)(imageBounds.width() * ratio);
                                    imageBounds.bottom = imageBounds.top + (int)(imageBounds.height() * ratio);
                                }
                                return imageBounds;
                            }
                        });
                    }
                })
//                .usePlugin(new AbstractMarkwonPlugin() { // 捕获图片点击事件
//                    @Override
//                    public void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder) {
//                        builder.appendFactory(Image.class, (configuration, props) -> {
//                            String url = ImageProps.DESTINATION.require(props);
//                            return new LinkSpan(configuration.theme(), url, new ImageLinkResolver(configuration.linkResolver()));
//                        });
//                        super.configureSpansFactory(builder);
//                    }
//                })
//                .usePlugin(new AbstractMarkwonPlugin() { // 捕获链接点击重定向到内置WebView
//                    @Override
//                    public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
//                        builder.linkResolver(new LinkResolver() {
//                            @Override
//                            public void resolve(@NonNull View view, @NonNull String link) {
//                                WebViewActivity.openUrl(context, null, link);
//                            }
//                        });
//                    }
//                })
                .usePlugin(TablePlugin.create(context))
                .usePlugin(MovementMethodPlugin.create(TableAwareMovementMethod.create()))
                .build();
    }

    public void render(TextView textView, String markdown) {
        if(textView != null && markdown != null) {
            try {
                markwon.setMarkdown(textView, markdown);
//                Log.d("MarkdownRenderer", "render: " + markdown);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
