package com.skythinker.gptassistant.tool;

import android.graphics.Typeface;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonVisitor;
import io.noties.markwon.SpannableBuilder;
import io.noties.markwon.html.HtmlTag;
import io.noties.markwon.html.MarkwonHtmlRenderer;
import io.noties.markwon.html.TagHandler;

class DetailsTagHandler extends TagHandler {
    private static final Map<TextView, DetailsState> DETAILS_STATE_MAP = new WeakHashMap<>();

    static void renderParsedMarkdown(@NonNull Markwon markwon, @NonNull TextView textView, @NonNull Spanned spanned) {
        DetailsParsingSpan[] spans = spanned.getSpans(0, spanned.length(), DetailsParsingSpan.class);
        if(spans == null || spans.length == 0) {
            DETAILS_STATE_MAP.remove(textView);
            markwon.setParsedMarkdown(textView, spanned);
            return;
        }

        List<DetailsElement> rootElements = new ArrayList<>();
        for(DetailsParsingSpan span : spans) {
            DetailsElement element = settle(new DetailsElement(
                    spanned.getSpanStart(span),
                    spanned.getSpanEnd(span),
                    span.summary,
                    span.defaultExpanded
            ), rootElements);
            if(element != null) {
                rootElements.add(element);
            }
        }

        for(DetailsElement element : rootElements) {
            initDetails(element, spanned);
        }
        sort(rootElements);
        assignStateIndices(rootElements, new int[]{0});

        DetailsState state = getDetailsState(textView);
        SpannableBuilder builder = new SpannableBuilder();
        int cursor = 0;

        // 在同一个TextView里重组details，可保留正文已有的Markdown样式与链接点击
        for(DetailsElement element : rootElements) {
            if(element.start > cursor) {
                builder.append(spanned.subSequence(cursor, element.start));
            }
            appendDetails(builder, markwon, textView, spanned, state, element);
            cursor = element.end;
        }

        if(cursor < spanned.length()) {
            builder.append(spanned.subSequence(cursor, spanned.length()));
        }

        markwon.setParsedMarkdown(textView, builder.spannableStringBuilder());
    }

    @Override
    public void handle(@NonNull MarkwonVisitor visitor, @NonNull MarkwonHtmlRenderer renderer, @NonNull HtmlTag tag) {
        int summaryEnd = -1;

        for(HtmlTag child : tag.getAsBlock().children()) {
            if(!child.isClosed()) {
                continue;
            }
            if("summary".equals(child.name())) {
                summaryEnd = child.end();
            }

            TagHandler tagHandler = renderer.tagHandler(child.name());
            if(tagHandler != null) {
                tagHandler.handle(visitor, renderer, child);
            } else if(child.isBlock()) {
                visitChildren(visitor, renderer, child.getAsBlock());
            }
        }

        if(summaryEnd > -1) {
            visitor.builder().setSpan(new DetailsParsingSpan(
                    subSequenceTrimmed(visitor.builder(), tag.start(), summaryEnd),
                    tag.attributes().containsKey("open")
            ), tag.start(), tag.end());
        }
    }

    @NonNull
    @Override
    public Collection<String> supportedTags() {
        return Collections.singleton("details");
    }

    private static void appendDetails(@NonNull SpannableBuilder builder, @NonNull Markwon markwon,
                                      @NonNull TextView textView, @NonNull Spanned spanned,
                                      @NonNull DetailsState state, @NonNull DetailsElement element) {
        if(!element.isDetails) {
            builder.append(element.content);
            return;
        }

        boolean expanded = state.isExpanded(element.stateIndex, element.defaultExpanded);
        int summaryStart = builder.length();
        builder.append(expanded ? "\u25BE " : "\u25B8 ");
        builder.append(element.content);
        int summaryEnd = builder.length();

        builder.setSpan(new ToggleDetailsSpan(markwon, textView, spanned, element.stateIndex, expanded),
                summaryStart, summaryEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new StyleSpan(Typeface.BOLD),
                summaryStart, summaryEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        if(expanded) {
            appendLineBreakIfNeeded(builder);
            // 折叠时仅保留summary，展开后再按原顺序追加details内部内容
            for(DetailsElement child : element.children) {
                appendDetails(builder, markwon, textView, spanned, state, child);
            }
        }

        appendLineBreakIfNeeded(builder);
    }

    @Nullable
    private static DetailsElement settle(@NonNull DetailsElement element, @NonNull List<? extends DetailsElement> elements) {
        for(DetailsElement current : elements) {
            if(element.start > current.start && element.end <= current.end) {
                DetailsElement settled = settle(element, current.children);
                if(settled != null) {
                    Iterator<DetailsElement> iterator = current.children.iterator();
                    while(iterator.hasNext()) {
                        DetailsElement balanced = settle(iterator.next(), Collections.singletonList(element));
                        if(balanced == null) {
                            iterator.remove();
                        }
                    }
                    current.children.add(element);
                }
                return null;
            }
        }
        return element;
    }

    private static void initDetails(@NonNull DetailsElement element, @NonNull Spanned spanned) {
        int end = element.end;
        for(int i = element.children.size() - 1; i >= 0; i--) {
            DetailsElement child = element.children.get(i);
            if(child.end < end) {
                element.children.add(new DetailsElement(child.end, end, spanned.subSequence(child.end, end)));
            }
            initDetails(child, spanned);
            end = child.start;
        }

        int start = element.start + element.content.length();
        if(end != start) {
            element.children.add(new DetailsElement(start, end, spanned.subSequence(start, end)));
        }
    }

    private static void sort(@NonNull List<DetailsElement> elements) {
        Collections.sort(elements, (left, right) -> Integer.compare(left.start, right.start));
        for(DetailsElement element : elements) {
            if(!element.children.isEmpty()) {
                sort(element.children);
            }
        }
    }

    private static void assignStateIndices(@NonNull List<DetailsElement> elements, @NonNull int[] nextIndex) {
        for(DetailsElement element : elements) {
            if(element.isDetails) {
                element.stateIndex = nextIndex[0]++;
                assignStateIndices(element.children, nextIndex);
            }
        }
    }

    @NonNull
    private static CharSequence subSequenceTrimmed(@NonNull CharSequence text, int start, int end) {
        while(start < end) {
            boolean startBlank = Character.isWhitespace(text.charAt(start));
            boolean endBlank = Character.isWhitespace(text.charAt(end - 1));
            if(!startBlank && !endBlank) {
                break;
            }
            if(startBlank) {
                start++;
            }
            if(endBlank) {
                end--;
            }
        }
        return text.subSequence(start, end);
    }

    @NonNull
    private static DetailsState getDetailsState(@NonNull TextView textView) {
        DetailsState state = DETAILS_STATE_MAP.get(textView);
        if(state != null) {
            return state;
        }
        state = new DetailsState();
        DETAILS_STATE_MAP.put(textView, state);
        return state;
    }

    private static void appendLineBreakIfNeeded(@NonNull SpannableBuilder builder) {
        CharSequence text = builder.spannableStringBuilder();
        if(text.length() > 0) {
            char lastChar = text.charAt(text.length() - 1);
            if(lastChar == '\n' || lastChar == '\r') {
                return;
            }
        }
        builder.append("\n");
    }

    private static class DetailsState {
        private final SparseBooleanArray expandedStates = new SparseBooleanArray();

        boolean isExpanded(int stateIndex, boolean defaultValue) {
            int keyIndex = expandedStates.indexOfKey(stateIndex);
            return keyIndex >= 0 ? expandedStates.valueAt(keyIndex) : defaultValue;
        }

        void setExpanded(int stateIndex, boolean expanded) {
            expandedStates.put(stateIndex, expanded);
        }
    }

    private static class DetailsParsingSpan {
        private final CharSequence summary;
        private final boolean defaultExpanded;

        DetailsParsingSpan(@NonNull CharSequence summary, boolean defaultExpanded) {
            this.summary = summary;
            this.defaultExpanded = defaultExpanded;
        }
    }

    private static class DetailsElement {
        private final int start;
        private final int end;
        private final CharSequence content;
        private final boolean isDetails;
        private final boolean defaultExpanded;
        private final List<DetailsElement> children = new ArrayList<>();
        private int stateIndex = -1;

        DetailsElement(int start, int end, @NonNull CharSequence content) {
            this.start = start;
            this.end = end;
            this.content = content;
            this.isDetails = false;
            this.defaultExpanded = false;
        }

        DetailsElement(int start, int end, @NonNull CharSequence summary, boolean defaultExpanded) {
            this.start = start;
            this.end = end;
            this.content = summary;
            this.isDetails = true;
            this.defaultExpanded = defaultExpanded;
        }
    }

    private static class ToggleDetailsSpan extends ClickableSpan {
        private final Markwon markwon;
        private final TextView textView;
        private final Spanned spanned;
        private final int stateIndex;
        private final boolean expanded;

        ToggleDetailsSpan(@NonNull Markwon markwon, @NonNull TextView textView,
                          @NonNull Spanned spanned, int stateIndex, boolean expanded) {
            this.markwon = markwon;
            this.textView = textView;
            this.spanned = spanned;
            this.stateIndex = stateIndex;
            this.expanded = expanded;
        }

        @Override
        public void onClick(@NonNull View widget) {
            DetailsState state = getDetailsState(textView);
            state.setExpanded(stateIndex, !expanded);
            renderParsedMarkdown(markwon, textView, spanned);
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            ds.setUnderlineText(false);
            ds.setFakeBoldText(true);
        }
    }
}
