package com.skythinker.gptassistant;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.PaintDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ImageSpan;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONObject;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.image.ImageSize;
import io.noties.markwon.image.ImageSizeResolverDef;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.noties.markwon.syntax.Prism4jThemeDefault;
import io.noties.markwon.syntax.SyntaxHighlightPlugin;
import io.noties.prism4j.Prism4j;
import io.noties.prism4j.annotations.PrismBundle;

import com.skythinker.gptassistant.ChatManager.ChatMessage.ChatRole;
import com.skythinker.gptassistant.ChatManager.ChatMessage;
import com.skythinker.gptassistant.ChatManager.MessageList;
import com.skythinker.gptassistant.ChatManager.Conversation;

@SuppressLint({"UseCompatLoadingForDrawables", "JavascriptInterface", "SetTextI18n"})
@PrismBundle(includeAll = true)
public class MainActivity extends Activity {

    private int selectedTab = 0;
    private TextView tvGptReply;
    private EditText etUserInput;
    private ImageButton btSend, btImage;
    private ScrollView svChatArea;
    private LinearLayout llChatList;
    private Handler handler = new Handler();
    private Markwon markwon;
    private long asrStartTime = 0;
    BroadcastReceiver localReceiver = null;

    private static boolean isAlive = false;
    private static boolean isRunning = false;

    ChatApiClient chatApiClient = null;
    private String chatApiBuffer = "";

    private TextToSpeech tts = null;
    private boolean ttsEnabled = true;
    final private List<String> ttsSentenceSeparator = Arrays.asList("。", ".", "？", "?", "！", "!", "……", "\n"); // 用于为TTS断句
    private int ttsSentenceEndIndex = 0;

    private boolean multiChat = false;
    ChatManager chatManager = null;
    private Conversation currentConversation = null; // 当前会话信息
    private MessageList multiChatList = null; // 指向currentConversation.messages

    private JSONObject currentTemplateParams = null; // 当前模板参数

    AsrClientBase asrClient = null;
    AsrClientBase.IAsrCallback asrCallback = null;

    WebScraper webScraper = null;

    Bitmap selectedImageBitmap = null;
    Uri photoUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GlobalDataHolder.init(this); // 初始化全局共享数据

        // 初始化Markdown渲染器
        markwon = Markwon.builder(this)
                .usePlugin(SyntaxHighlightPlugin.create(new Prism4j(new GrammarLocatorDef()), Prism4jThemeDefault.create(0)))
                .usePlugin(JLatexMathPlugin.create(40, builder -> builder.inlinesEnabled(true)))
//                .usePlugin(TablePlugin.create(this)) // unstable
//                .usePlugin(MovementMethodPlugin.create(TableAwareMovementMethod.create()))
                .usePlugin(ImagesPlugin.create())
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(new AbstractMarkwonPlugin() {
                    @NonNull
                    @Override
                    public String processMarkdown(@NonNull String markdown) {
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
                            // 进行替换
                            sepList.set(i, sepList.get(i).replaceAll(regexDollar, latexReplacement)
                                    .replaceAll(regexBrackets, latexReplacement)
                                    .replaceAll(regexParentheses, latexReplacement)
                                    .replaceAll(regexImage, imageReplacement));
                        }
                        return String.join("```", sepList);
                    }
                })
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
                        builder.imageSizeResolver(new ImageSizeResolverDef(){
                            @NonNull @Override
                            protected Rect resolveImageSize(@Nullable ImageSize imageSize, @NonNull Rect imageBounds, int canvasWidth, float textSize) {
                                int maxSize = dpToPx(120);
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
                .build();

        // 初始化TTS
        tts = new TextToSpeech(this, status -> {
            if(status == TextToSpeech.SUCCESS) {
                int res = tts.setLanguage(Locale.getDefault());
                if(res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "不支持当前语言");
                }else{
                    Log.d("TTS", "初始化成功");
                }
            }else{
                Log.e("TTS", "初始化失败 ErrorCode: " + status);
            }
        });

        setContentView(R.layout.activity_main); // 设置主界面布局
        overridePendingTransition(R.anim.translate_up_in, R.anim.translate_down_out); // 设置进入动画
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS); // 设置沉浸式状态栏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        tvGptReply = findViewById(R.id.tv_chat_notice);
        tvGptReply.setTextIsSelectable(true);
        tvGptReply.setMovementMethod(LinkMovementMethod.getInstance());
        etUserInput = findViewById(R.id.et_user_input);
        btSend = findViewById(R.id.bt_send);
        btImage = findViewById(R.id.bt_image);
        svChatArea = findViewById(R.id.sv_chat_list);
        llChatList = findViewById(R.id.ll_chat_list);

        // 处理启动Intent
        Intent activityIntent = getIntent();
        if(activityIntent != null){
            String action = activityIntent.getAction();
            if(Intent.ACTION_PROCESS_TEXT.equals(action)) { // 全局上下文菜单
                String text = activityIntent.getStringExtra(Intent.EXTRA_PROCESS_TEXT);
                if(text != null){
                    etUserInput.setText(text);
                }
            } else if(Intent.ACTION_SEND.equals(action)) { // 分享图片
                String type = activityIntent.getType();
                if(type != null && type.startsWith("image/")) {
                    Uri imageUri = activityIntent.getParcelableExtra(Intent.EXTRA_STREAM); // 获取图片Uri
                    if(imageUri != null) {
                        try {
                            // 获取图片Bitmap并缩放
                            Bitmap bitmap = (Bitmap) BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
                            selectedImageBitmap = bitmap;
                            if(GlobalDataHolder.getLimitVisionSize()) {
                                if (bitmap.getWidth() < bitmap.getHeight())
                                    selectedImageBitmap = resizeBitmap(bitmap, 512, 2048);
                                else
                                    selectedImageBitmap = resizeBitmap(bitmap, 2048, 512);
                            } else {
                                selectedImageBitmap = resizeBitmap(bitmap, 2048, 2048);
                            }
                            btImage.setImageResource(R.drawable.image_enabled);
                            if(!GlobalDataHolder.getGptModel().contains("vision"))
                                Toast.makeText(this, R.string.toast_use_vision_model, Toast.LENGTH_LONG).show();
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                } else if(type != null && type.equals("text/plain")) { // 分享文本
                    String text = activityIntent.getStringExtra(Intent.EXTRA_TEXT);
                    if(text != null){
                        etUserInput.setText(text);
                    }
                }
            }
        }

        chatManager = new ChatManager(this); // 初始化聊天记录管理器
        ChatMessage.setContext(this); // 设置聊天消息的上下文（用于读写文件）
//        chatManager.removeAllConversations(true); // 重置聊天记录（调试用）
//        for(int i = 0; i < 50; i++) {
//            Conversation conversation = chatManager.newConversation();
//            conversation.messages.add(new ChatMessage(ChatRole.USER).setText("你好"));
//            conversation.messages.add(new ChatMessage(ChatRole.ASSISTANT).setText("你好，有什么可以帮您？" + i));
//        }

        webScraper = new WebScraper(this, findViewById(R.id.ll_main_base)); // 初始化网页抓取器

        // 初始化GPT客户端
        chatApiClient = new ChatApiClient(this,
                GlobalDataHolder.getGptApiHost(),
                GlobalDataHolder.getGptApiKey(),
                GlobalDataHolder.getGptModel(),
                new ChatApiClient.OnReceiveListener() {
                    private long lastRenderTime = 0;

                    @Override
                    public void onMsgReceive(String message) { // 收到GPT回复（增量）
                        chatApiBuffer += message;
                        handler.post(() -> {
                            if(System.currentTimeMillis() - lastRenderTime > 100) { // 限制最高渲染频率10Hz
                                boolean isBottom = svChatArea.getChildAt(0).getBottom()
                                        <= svChatArea.getHeight() + svChatArea.getScrollY(); // 判断消息布局是否在底部
                                try {
                                    markwon.setMarkdown(tvGptReply, chatApiBuffer); // 渲染Markdown
                                } catch (Exception e){
                                    e.printStackTrace();
                                }
                                if(isBottom){
                                    scrollChatAreaToBottom(); // 渲染前在底部则渲染后滚动到底部
                                }
                                lastRenderTime = System.currentTimeMillis();
                            }

                            if(currentTemplateParams.getBool("speak", ttsEnabled)) { // 处理TTS
                                String wholeText = tvGptReply.getText().toString(); // 获取可朗读的文本
                                if(ttsSentenceEndIndex < wholeText.length()) {
                                    int nextSentenceEndIndex = wholeText.length();
                                    boolean found = false;
                                    for(String separator : ttsSentenceSeparator) { // 查找最后一个断句分隔符
                                        int index = wholeText.indexOf(separator, ttsSentenceEndIndex);
                                        if(index != -1 && index < nextSentenceEndIndex) {
                                            nextSentenceEndIndex = index + separator.length();
                                            found = true;
                                        }
                                    }
                                    if(found) { // 找到断句分隔符则添加到朗读队列
                                        String sentence = wholeText.substring(ttsSentenceEndIndex, nextSentenceEndIndex);
                                        ttsSentenceEndIndex = nextSentenceEndIndex;
                                        tts.speak(sentence, TextToSpeech.QUEUE_ADD, null);
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void onFinished(boolean completed) { // GPT回复完成
                        handler.post(() -> {
                            String referenceStr = "\n\n" + getString(R.string.text_ref_web_prefix);
                            int referenceCount = 0;
                            if(completed) { // 如果是完整回复则添加参考网页
                                int questionIndex = multiChatList.size() - 1;
                                while(questionIndex >= 0 && multiChatList.get(questionIndex).role != ChatRole.USER) { // 找到上一个提问消息
                                    questionIndex--;
                                }
                                for(int i = questionIndex + 1; i < multiChatList.size(); i++) { // 依次检查函数调用，并获取网页URL
                                    if(multiChatList.get(i).role == ChatRole.FUNCTION
                                        && multiChatList.get(i-1).role == ChatRole.ASSISTANT
                                        && multiChatList.get(i-1).functionName != null) {
                                        String funcName = multiChatList.get(i-1).functionName;
                                        String funcArgs = multiChatList.get(i-1).contentText;
                                        if(funcName.equals("get_html_text")) {
                                            String url = new JSONObject(funcArgs).getStr("url");
                                            referenceStr += String.format("[[%s]](%s) ", ++referenceCount, url);
                                        }
                                    }
                                }
                            }
                            try {
                                markwon.setMarkdown(tvGptReply, chatApiBuffer); // 渲染Markdown
                                String ttsText = tvGptReply.getText().toString();
                                if(currentTemplateParams.getBool("speak", ttsEnabled) && ttsText.length() > ttsSentenceEndIndex) { // 如果TTS开启则朗读剩余文本
                                    tts.speak(ttsText.substring(ttsSentenceEndIndex), TextToSpeech.QUEUE_ADD, null);
                                }
                                if(referenceCount > 0)
                                    chatApiBuffer += referenceStr; // 添加参考网页
                                multiChatList.add(new ChatMessage(ChatRole.ASSISTANT).setText(chatApiBuffer)); // 保存回复内容到聊天数据列表
                                ((LinearLayout) tvGptReply.getParent()).setTag(multiChatList.get(multiChatList.size() - 1)); // 绑定该聊天数据到布局
                                markwon.setMarkdown(tvGptReply, chatApiBuffer); // 再次渲染Markdown添加参考网页
                                btSend.setImageResource(R.drawable.send_btn);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        handler.post(() -> {
                            String errText = String.format(getString(R.string.text_gpt_error_prefix) + "%s", message);
                            if(tvGptReply != null){
                                tvGptReply.setText(errText);
                            }else{
                                Toast.makeText(MainActivity.this, errText, Toast.LENGTH_LONG).show();
                            }
                            btSend.setImageResource(R.drawable.send_btn);
                        });
                    }

                    @Override
                    public void onFunctionCall(String name, String arg) { // 收到函数调用请求
                        Log.d("FunctionCall", String.format("%s: %s", name, arg));
                        multiChatList.add(new ChatMessage(ChatRole.ASSISTANT).setFunction(name).setText(arg)); // 保存请求到聊天数据列表
                        if (name.equals("get_html_text")) { // 调用联网函数
                            try {
                                JSONObject argJson = new JSONObject(arg);
                                String url = argJson.getStr("url"); // 获取URL
                                runOnUiThread(() -> {
                                    markwon.setMarkdown(tvGptReply, String.format(getString(R.string.text_visiting_web_prefix) + "[%s](%s)", URLDecoder.decode(url), url));
                                    webScraper.load(url, new WebScraper.Callback() { // 抓取网页内容
                                        @Override
                                        public void onLoadResult(String result) {
                                            postSendFunctionReply(name, result); // 返回网页内容给GPT
//                                            Log.d("FunctionCall", String.format("Response: %s", result));
                                        }

                                        @Override
                                        public void onLoadFail(String message) {
                                            postSendFunctionReply(name, "Failed to get response of this url.");
                                        }
                                    });
                                    Log.d("FunctionCall", String.format("Loading url: %s", url));
                                });
                            } catch (JSONException e) {
                                e.printStackTrace();
                                postSendFunctionReply(name, "Error when getting response.");
                            }
                        } else {
                            postSendFunctionReply(name, "Function not found.");
                            Log.d("FunctionCall", String.format("Function not found: %s", name));
                        }
                    }
                });

        // 发送按钮点击事件
        btSend.setOnClickListener(view -> {
            if (chatApiClient.isStreaming()) {
                chatApiClient.stop();
            }else if(webScraper.isLoading()){
                webScraper.stopLoading();
                if(tvGptReply != null)
                    tvGptReply.setText(R.string.text_cancel_web);
                btSend.setImageResource(R.drawable.send_btn);
            }else{
                tts.stop();
                sendQuestion(null);
            }
        });

        // 图片选择按钮点击事件
        btImage.setOnClickListener(view -> {
            if (selectedImageBitmap != null) { // 当前已选中图片，弹出预览窗口
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                LayoutInflater inflater = LayoutInflater.from(this);
                View dialogView = inflater.inflate(R.layout.image_preview_dialog, null);
                AlertDialog dialog = builder.create();
                dialog.show();
                dialog.getWindow().setContentView(dialogView);
                ((ImageView) dialogView.findViewById(R.id.iv_image_preview)).setImageBitmap(selectedImageBitmap);
                ((TextView) dialogView.findViewById(R.id.tv_image_preview_size)).setText(String.format("%s x %s", selectedImageBitmap.getWidth(), selectedImageBitmap.getHeight()));
                dialogView.findViewById(R.id.bt_image_preview_cancel).setOnClickListener(view1 -> dialog.dismiss());
                dialogView.findViewById(R.id.bt_image_preview_del).setOnClickListener(view1 -> { // 移除当前选择的图片
                    dialog.dismiss();
                    selectedImageBitmap = null;
                    btImage.setImageResource(R.drawable.image);
                });
                dialogView.findViewById(R.id.bt_image_preview_reselect).setOnClickListener(view1 -> { // 重新选择图片
                    dialogView.findViewById(R.id.bt_image_preview_del).performClick();
                    btImage.performClick();
                });
            } else { // 当前没有图片被选中，弹出选择窗口
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                LayoutInflater inflater = LayoutInflater.from(this);
                View dialogView = inflater.inflate(R.layout.image_method_dialog, null);
                AlertDialog dialog = builder.create();
                dialog.show();
                dialog.getWindow().setContentView(dialogView);
                dialogView.findViewById(R.id.bt_take_photo).setOnClickListener(view1 -> { // 拍照
                    dialog.dismiss();
                    photoUri = FileProvider.getUriForFile(MainActivity.this, BuildConfig.APPLICATION_ID + ".provider", new File(getCacheDir(), "photo.jpg"));
                    Intent intent=new Intent();
                    intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    startActivityForResult(intent, 1);
                });
                dialogView.findViewById(R.id.bt_select_from_album).setOnClickListener(view1 -> { // 从相册选择
                    dialog.dismiss();
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    intent.setType("image/*");
                    startActivityForResult(intent, 2);
                });
                dialogView.findViewById(R.id.bt_image_cancel).setOnClickListener(view1 -> dialog.dismiss());
            }
        });

        // 长按输入框清空内容
        etUserInput.setOnLongClickListener(view -> {
            etUserInput.setText("");
            return true;
        });

        // 连续对话按钮点击事件（切换连续对话开关状态）
        (findViewById(R.id.cv_multi_chat)).setOnClickListener(view -> {
            multiChat = !multiChat;
            if(multiChat){
                ((CardView) findViewById(R.id.cv_multi_chat)).setForeground(getDrawable(R.drawable.chat_btn_enabled));
            }else{
                ((CardView) findViewById(R.id.cv_multi_chat)).setForeground(getDrawable(R.drawable.chat_btn));
            }
        });

        // 新建对话按钮点击事件
        (findViewById(R.id.cv_new_chat)).setOnClickListener(view -> {
            clearChatListView();

            if(currentConversation != null &&
                    ((multiChatList.size() > 0 && multiChatList.get(0).role != ChatRole.SYSTEM) || (multiChatList.size() > 1 && multiChatList.get(0).role == ChatRole.SYSTEM)) &&
                    GlobalDataHolder.getAutoSaveHistory()) // 包含有效对话则保存当前对话
                chatManager.addConversation(currentConversation);

            currentConversation = new Conversation();
            multiChatList = currentConversation.messages;
        });

        (findViewById(R.id.cv_new_chat)).performClick(); // 初始化对话列表

        // TTS开关按钮点击事件（切换TTS开关状态）
        (findViewById(R.id.cv_tts_off)).setOnClickListener(view -> {
            ttsEnabled = !ttsEnabled;
            if(ttsEnabled) {
                ((CardView) findViewById(R.id.cv_tts_off)).setForeground(getDrawable(R.drawable.tts_off));
            }else{
                ((CardView) findViewById(R.id.cv_tts_off)).setForeground(getDrawable(R.drawable.tts_off_enable));
                tts.stop();
            }
        });

        (findViewById(R.id.cv_history)).setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivityForResult(intent, 3);
        });

        // 设置按钮点击事件，跳转到设置页面
        (findViewById(R.id.cv_settings)).setOnClickListener(view -> {
            startActivityForResult(new Intent(MainActivity.this, TabConfActivity.class), 0);
        });

        // 关闭按钮点击事件，退出程序
        (findViewById(R.id.cv_close)).setOnClickListener(view -> {
            finish();
        });

        // 上方空白区域点击事件，退出程序
        (findViewById(R.id.view_bg_empty)).setOnClickListener(view -> {
            finish();
        });

        // 用户设置为启动时开启连续对话
        if(GlobalDataHolder.getDefaultEnableMultiChat()){
            multiChat = true;
            ((CardView) findViewById(R.id.cv_multi_chat)).setForeground(getDrawable(R.drawable.chat_btn_enabled));
        }

        // 用户设置为启动时开启TTS
        if(!GlobalDataHolder.getDefaultEnableTts()){
            ttsEnabled = false;
            ((CardView) findViewById(R.id.cv_tts_off)).setForeground(getDrawable(R.drawable.tts_off_enable));
        }

        // 处理选中的模板
        if(GlobalDataHolder.getSelectedTab() != -1 && GlobalDataHolder.getSelectedTab() < GlobalDataHolder.getTabDataList().size())
            selectedTab = GlobalDataHolder.getSelectedTab();
        switchToTemplate(selectedTab);
        Button selectedTabBtn = (Button) ((LinearLayout) findViewById(R.id.tabs_layout)).getChildAt(selectedTab); // 将选中的模板按钮滚动到可见位置
        selectedTabBtn.getParent().requestChildFocus(selectedTabBtn, selectedTabBtn);

        updateModelSpinner(); // 设置模型选择下拉框
        updateImageButtonVisible(); // 设置图片按钮是否可见

        isAlive = true; // 标记当前Activity已启动

        requestPermission(); // 申请动态权限

        // 初始化语音识别回调
        asrCallback = new AsrClientBase.IAsrCallback() {
            @Override
            public void onError(String msg) {
                if(tvGptReply != null) {
                    runOnUiThread(() -> tvGptReply.setText(getString(R.string.text_asr_error_prefix) + msg));
                }else{
                    Toast.makeText(MainActivity.this, getString(R.string.text_asr_error_prefix) + msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onResult(String result) {
                runOnUiThread(() -> etUserInput.setText(result));
            }
        };
        // 设置使用百度/Whisper/华为语音识别
        if(GlobalDataHolder.getAsrUseBaidu()) {
            setAsrClient("baidu");
        } else if(GlobalDataHolder.getAsrUseWhisper()) {
            setAsrClient("whisper");
        } else {
            setAsrClient("hms");
        }

        // 设置本地广播接收器
        localReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if(action.equals("com.skythinker.gptassistant.KEY_SPEECH_START")) { // 开始语音识别
                    tts.stop();
                    asrClient.startRecongnize();
                    asrStartTime = System.currentTimeMillis();
                    etUserInput.setText("");
                    etUserInput.setHint(R.string.text_listening_hint);
                } else if(action.equals("com.skythinker.gptassistant.KEY_SPEECH_STOP")) { // 停止语音识别
                    etUserInput.setHint(R.string.text_input_hint);
                    if(System.currentTimeMillis() - asrStartTime < 1000) {
                        asrClient.cancelRecongnize();
                    } else {
                        asrClient.stopRecongnize();
                    }
                } else if(action.equals("com.skythinker.gptassistant.KEY_SEND")) { // 发送问题
                    if(!chatApiClient.isStreaming())
                        sendQuestion(null);
                } else if(action.equals("com.skythinker.gptassistant.SHOW_KEYBOARD")) { // 弹出软键盘
                    etUserInput.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.showSoftInput(findViewById(R.id.et_user_input), InputMethodManager.RESULT_UNCHANGED_SHOWN);
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.skythinker.gptassistant.KEY_SPEECH_START");
        intentFilter.addAction("com.skythinker.gptassistant.KEY_SPEECH_STOP");
        intentFilter.addAction("com.skythinker.gptassistant.KEY_SEND");
        intentFilter.addAction("com.skythinker.gptassistant.SHOW_KEYBOARD");
        LocalBroadcastManager.getInstance(this).registerReceiver(localReceiver, intentFilter);

        // 检查无障碍权限
        if(GlobalDataHolder.getCheckAccessOnStart()) {
            if(!MyAccessbilityService.isConnected()) { // 没有权限则弹窗提醒用户开启
                new ConfirmDialog(this)
                    .setContent(getString(R.string.text_access_notice))
                    .setOnConfirmListener(() -> {
                        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        startActivity(intent);
                    })
                    .setOnCancelListener(() -> {
                        Toast.makeText(MainActivity.this, getString(R.string.toast_access_error), Toast.LENGTH_SHORT).show();
                    })
                    .show();
            }
        }
    }

    // 设置当前使用的语音识别接口
    private void setAsrClient(String type) {
        if(asrClient != null) {
            asrClient.destroy();
        }
        if(type.equals("baidu")) {
            asrClient = new BaiduAsrClient(this);
            asrClient.setCallback(asrCallback);
        } else if (type.equals("hms")) {
            asrClient = new HmsAsrClient(this);
            asrClient.setCallback(asrCallback);
        } else if (type.equals("whisper")) {
            asrClient = new WhisperAsrClient(this, GlobalDataHolder.getGptApiHost(), GlobalDataHolder.getGptApiKey());
            asrClient.setCallback(asrCallback);
        }
    }

    // 设置是否允许GPT联网
    private void setNetworkEnabled(boolean enabled) {
        if(enabled) {
            chatApiClient.addFunction("get_html_text", "get all innerText and links of a web page", "{url: {type: string, description: html url}}", new String[]{"url"});
        } else {
            chatApiClient.removeFunction("get_html_text");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 0) { // 从设置界面返回
            int tabNum = GlobalDataHolder.getTabDataList().size(); // 更新模板列表
            if(selectedTab >= tabNum)
                selectedTab = tabNum - 1;
            switchToTemplate(selectedTab);

            updateModelSpinner(); // 更新模型下拉选框
            updateImageButtonVisible(); // 更新图片按钮可见性

            // 更新GPT客户端相关设置
            chatApiClient.setApiInfo(GlobalDataHolder.getGptApiHost(), GlobalDataHolder.getGptApiKey());
            chatApiClient.setModel(currentTemplateParams.getStr("model", GlobalDataHolder.getGptModel()));

            // 更新所使用的语音识别接口
            if(GlobalDataHolder.getAsrUseBaidu() && !(asrClient instanceof BaiduAsrClient)) {
                setAsrClient("baidu");
            } else if(GlobalDataHolder.getAsrUseWhisper() && !(asrClient instanceof WhisperAsrClient)) {
                setAsrClient("whisper");
            } else if(!GlobalDataHolder.getAsrUseBaidu() && !GlobalDataHolder.getAsrUseWhisper() && !(asrClient instanceof HmsAsrClient)) {
                setAsrClient("hms");
            }

            // 更新Whisper接口的API信息
            if(asrClient instanceof WhisperAsrClient) {
                ((WhisperAsrClient) asrClient).setApiInfo(GlobalDataHolder.getGptApiHost(), GlobalDataHolder.getGptApiKey());
            }

            setNetworkEnabled(currentTemplateParams.getBool("network", GlobalDataHolder.getEnableInternetAccess())); // 更新GPT联网设置
        } else if((requestCode == 1 || requestCode == 2) && resultCode == RESULT_OK) { // 从相册或相机返回
            Uri uri = requestCode == 1 ? photoUri : data.getData(); // 获取图片Uri
            try {
                // 获取Bitmap并缩放
                Bitmap bitmap = (Bitmap) BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
                selectedImageBitmap = bitmap;
                if(GlobalDataHolder.getLimitVisionSize()) {
                    if (bitmap.getWidth() < bitmap.getHeight())
                        selectedImageBitmap = resizeBitmap(bitmap, 512, 2048);
                    else
                        selectedImageBitmap = resizeBitmap(bitmap, 2048, 512);
                } else {
                    selectedImageBitmap = resizeBitmap(bitmap, 2048, 2048);
                }
                btImage.setImageResource(R.drawable.image_enabled); // 高亮显示图片按钮
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else if(requestCode == 3 && resultCode == RESULT_OK) { // 从聊天历史界面返回
            if(data.hasExtra("id")) {
                long id = data.getLongExtra("id", -1);
                Log.d("MainActivity", "onActivityResult 3: id=" + id);
                Conversation conversation = chatManager.getConversation(id);
                chatManager.removeConversation(id);
                conversation.updateTime();
                reloadConversation(conversation);
            }
        }
    }

    // 滚动聊天列表到底部
    private void scrollChatAreaToBottom() {
        svChatArea.post(() -> {
            int delta = svChatArea.getChildAt(0).getBottom()
                    - (svChatArea.getHeight() + svChatArea.getScrollY());
            if(delta != 0)
                svChatArea.smoothScrollBy(0, delta);
        });
    }

    // 设置图片选择按钮可见性
    private void updateImageButtonVisible() {
        if(currentTemplateParams.getStr("model", GlobalDataHolder.getGptModel()).contains("vision"))
            btImage.setVisibility(View.VISIBLE);
        else
            btImage.setVisibility(View.GONE);
    }

    // 更新模型下拉选框
    private void updateModelSpinner() {
        Spinner spModels = findViewById(R.id.sp_main_model);
        List<String> models = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.models))); // 获取内置模型列表
        models.addAll(GlobalDataHolder.getCustomModels()); // 添加自定义模型到列表
        ArrayAdapter<String> modelsAdapter = new ArrayAdapter<String>(this, R.layout.main_model_spinner_item, models) { // 设置Spinner样式和列表数据
            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) { // 设置选中/未选中的选项样式
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                if(spModels.getSelectedItemPosition() == position) {
                    tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                } else {
                    tv.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
                }
                return tv;
            }
        };
        modelsAdapter.setDropDownViewResource(R.layout.model_spinner_dropdown_item); // 设置下拉选项样式
        spModels.setAdapter(modelsAdapter);
        spModels.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { // 设置选项点击事件
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                GlobalDataHolder.saveGptApiInfo(GlobalDataHolder.getGptApiHost(), GlobalDataHolder.getGptApiKey(), adapterView.getItemAtPosition(i).toString(), GlobalDataHolder.getCustomModels());
                chatApiClient.setModel(currentTemplateParams.getStr("model", GlobalDataHolder.getGptModel()));
                updateImageButtonVisible();
                modelsAdapter.notifyDataSetChanged();
            }
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });
        for(int i = 0; i < modelsAdapter.getCount(); i++) { // 查找当前选中的选项
            if(modelsAdapter.getItem(i).equals(GlobalDataHolder.getGptModel())) {
                spModels.setSelection(i);
                break;
            }
            if(i == modelsAdapter.getCount() - 1) { // 没有找到选中的选项，默认选中第一个
                spModels.setSelection(0);
            }
        }
    }

    // 更新模板列表布局
    private void updateTabListView() {
        LinearLayout tabList = findViewById(R.id.tabs_layout);
        tabList.removeAllViews();
        List<PromptTabData> tabDataList = GlobalDataHolder.getTabDataList(); // 获取模板列表数据
        for (int i = 0; i < tabDataList.size(); i++) { // 依次创建按钮并添加到父布局
            PromptTabData tabData = tabDataList.get(i);
            Button tabBtn = new Button(this);
            tabBtn.setText(tabData.getTitle());
            tabBtn.setTextSize(16);
            if(i == selectedTab) {
                tabBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                tabBtn.setBackgroundResource(R.drawable.tab_background_selected);
            } else {
                tabBtn.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
                tabBtn.setBackgroundResource(R.drawable.tab_background_unselected);
            }
            ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 20, 0);
            tabBtn.setLayoutParams(params);
            int finalI = i;
            tabBtn.setOnClickListener(view -> { // 按钮点击时选中对应的模板
                if(finalI != selectedTab) {
                    switchToTemplate(finalI);
                    if(multiChatList.size() > 0)
                        (findViewById(R.id.cv_new_chat)).performClick();
                }
            });
            tabList.addView(tabBtn);
        }
    }

    // 更新模板参数控件
    private void updateTemplateParamsView() {
        LinearLayout llParams = findViewById(R.id.ll_template_params);
        llParams.removeAllViews();
        if(currentTemplateParams.containsKey("input")) {
            for (String inputKey : currentTemplateParams.getJSONObject("input").keySet()) {
                LinearLayout llOuter = new LinearLayout(this); // 外层布局，包含参数名和参数控件
                llOuter.setOrientation(LinearLayout.HORIZONTAL);
                llOuter.setGravity(Gravity.CENTER);
                llOuter.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
                TextView tv = new TextView(this); // 参数名
                tv.setText(inputKey);
                tv.setTextColor(Color.BLACK);
                tv.setTextSize(16);
                tv.setPadding(0, 0, dpToPx(10), 0);
                tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0));
                llOuter.addView(tv);
                JSONObject inputItem = currentTemplateParams.getJSONObject("input").getJSONObject(inputKey);
                if(inputItem.getStr("type").equals("text")) { // 输入型参数控件
                    EditText et = new EditText(this);
                    et.setBackgroundColor(Color.TRANSPARENT);
                    et.setTextSize(16);
                    et.setHint("请输入");
                    et.setTextColor(Color.BLACK);
                    et.setSingleLine(false);
                    et.setMaxHeight(dpToPx(80));
                    et.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                    et.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    et.setPadding(0, 0, 0, 0);
                    llOuter.addView(et);
                } else if(inputItem.getStr("type").equals("select")) { // 下拉选择型参数控件
                    Spinner sp = new Spinner(this, Spinner.MODE_DROPDOWN);
                    sp.setBackgroundColor(Color.TRANSPARENT);
                    sp.setPadding(0, 0, 0, 0);
                    sp.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    sp.setPopupBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.spinner_dropdown_background));
                    List<String> options = new ArrayList<>();
                    JSONArray itemsArray = inputItem.getJSONArray("items");
                    for(int i = 0; i < itemsArray.size(); i++) {
                        options.add(itemsArray.getJSONObject(i).getStr("name"));
                    }
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.param_spinner_item, options) {
                        @Override
                        public View getDropDownView(int position, View convertView, ViewGroup parent) {
                            TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                            if(sp.getSelectedItemPosition() == position) { // 选中项
                                tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                            } else { // 未选中项
                                tv.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
                            }
                            return tv;
                        }
                    };
                    adapter.setDropDownViewResource(R.layout.param_spinner_dropdown_item);
                    sp.setAdapter(adapter);
                    llOuter.addView(sp);
                }
                llOuter.setTag(inputKey);
                llParams.addView(llOuter);
            }
        }

        if(llParams.getChildCount() == 0) { // 没有参数，隐藏参数布局
            ((CardView) llParams.getParent()).setVisibility(View.GONE);
        } else {
            ((CardView) llParams.getParent()).setVisibility(View.VISIBLE);
        }
    }

    // 从界面上获取模板参数
    private JSONObject getTemplateParamsFromView() {
        JSONObject params = new JSONObject();
        LinearLayout llParams = findViewById(R.id.ll_template_params);
        for (int i = 0; i < llParams.getChildCount(); i++) {
            LinearLayout llOuter = (LinearLayout) llParams.getChildAt(i);
            String inputKey = (String) llOuter.getTag();
            if(llOuter.getChildAt(1) instanceof EditText) {
                EditText et = (EditText) llOuter.getChildAt(1);
                params.putOpt(inputKey, et.getText().toString());
            } else if(llOuter.getChildAt(1) instanceof Spinner) {
                Spinner sp = (Spinner) llOuter.getChildAt(1);
                params.putOpt(inputKey, sp.getSelectedItem());
            }
        }
        return params;
    }

    // 切换到指定的模板
    private void switchToTemplate(int tabIndex) {
        selectedTab = tabIndex;
        if(GlobalDataHolder.getSelectedTab() != -1) {
            GlobalDataHolder.saveSelectedTab(selectedTab);
        }
        currentTemplateParams = GlobalDataHolder.getTabDataList().get(selectedTab).parseParams();
        Log.d("MainActivity", "switch template: params=" + currentTemplateParams);
        chatApiClient.setModel(currentTemplateParams.getStr("model", GlobalDataHolder.getGptModel()));
        setNetworkEnabled(currentTemplateParams.getBool("network", GlobalDataHolder.getEnableInternetAccess()));
        updateTabListView();
        updateTemplateParamsView();
        updateImageButtonVisible();
    }

    // 添加一条聊天记录到聊天列表布局
    private LinearLayout addChatView(ChatRole role, String content, String imageBase64) {
        ViewGroup.MarginLayoutParams iconParams = new ViewGroup.MarginLayoutParams(dpToPx(30), dpToPx(30)); // 头像布局参数
        iconParams.setMargins(dpToPx(4), dpToPx(12), dpToPx(4), dpToPx(12));

        ViewGroup.MarginLayoutParams contentParams = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); // 内容布局参数
        contentParams.setMargins(dpToPx(4), dpToPx(15), dpToPx(4), dpToPx(15));

        LinearLayout.LayoutParams popupIconParams = new LinearLayout.LayoutParams(dpToPx(30), dpToPx(30)); // 弹出的操作按钮布局参数
        popupIconParams.setMargins(dpToPx(5), dpToPx(5), dpToPx(5), dpToPx(5));

        LinearLayout llOuter = new LinearLayout(this); // 包围整条聊天记录的最外层布局
        llOuter.setOrientation(LinearLayout.HORIZONTAL);
        if(role == ChatRole.ASSISTANT) // 不同角色使用不同背景颜色
            llOuter.setBackgroundColor(Color.parseColor("#0A000000"));

        ImageView ivIcon = new ImageView(this); // 设置头像
        if(role == ChatRole.USER)
            ivIcon.setImageResource(R.drawable.chat_user_icon);
        else
            ivIcon.setImageResource(R.drawable.chat_gpt_icon);
        ivIcon.setLayoutParams(iconParams);

        TextView tvContent = new TextView(this); // 设置内容
        SpannableString spannableString = null;
        if(role == ChatRole.USER) {
            if (imageBase64 != null) { // 如有图片则在末尾添加ImageSpan
                spannableString = new SpannableString(content + "\n ");
                Bitmap bitmap = base64ToBitmap(imageBase64);
                int maxSize = dpToPx(120);
                bitmap = resizeBitmap(bitmap, maxSize, maxSize);
                ImageSpan imageSpan = new ImageSpan(this, bitmap);
                spannableString.setSpan(imageSpan, content.length() + 1, content.length() + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                spannableString = new SpannableString(content);
            }
            tvContent.setText(spannableString);
        } else if(role == ChatRole.ASSISTANT) {
            markwon.setMarkdown(tvContent, content);
        }
        tvContent.setTextSize(16);
        tvContent.setTextColor(Color.BLACK);
        tvContent.setLayoutParams(contentParams);
        tvContent.setTextIsSelectable(true);
        tvContent.setMovementMethod(LinkMovementMethod.getInstance());

        LinearLayout llPopup = new LinearLayout(this); // 弹出按钮列表布局
        llPopup.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        PaintDrawable popupBackground = new PaintDrawable(Color.TRANSPARENT);
        llPopup.setBackground(popupBackground);
        llPopup.setOrientation(LinearLayout.HORIZONTAL);

        PopupWindow popupWindow = new PopupWindow(llPopup, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true); // 弹出窗口
        popupWindow.setOutsideTouchable(true);
        ivIcon.setTag(popupWindow); // 将弹出窗口绑定到头像上

        CardView cvDelete = new CardView(this); // 删除单条对话按钮
        cvDelete.setForeground(getDrawable(R.drawable.clear_btn));
        cvDelete.setOnClickListener(view -> {
            popupWindow.dismiss();
            ChatMessage chat = (ChatMessage) llOuter.getTag(); // 获取布局上绑定的聊天记录数据
            if(chat != null) {
                int index = multiChatList.indexOf(chat);
                multiChatList.remove(chat);
                while(--index > 0 && (multiChatList.get(index).role == ChatRole.FUNCTION
                        || multiChatList.get(index).functionName != null && multiChatList.get(index).functionName.equals("get_html_text"))) // 将上方联网数据也删除
                    multiChatList.remove(index);
            }
            if(tvContent == tvGptReply) { // 删除的是GPT正在回复的消息框，停止回复和TTS
                if(chatApiClient.isStreaming())
                    chatApiClient.stop();
                tts.stop();
            }
            llChatList.removeView(llOuter);
            if(llChatList.getChildCount() == 0) // 如果删除后聊天列表为空，则添加占位TextView
                clearChatListView();
        });
        llPopup.addView(cvDelete);

        CardView cvDelBelow = new CardView(this); // 删除下方所有对话按钮
        cvDelBelow.setForeground(getDrawable(R.drawable.del_below_btn));
        cvDelBelow.setOnClickListener(view -> {
            popupWindow.dismiss();
            int index = llChatList.indexOfChild(llOuter);
            while(llChatList.getChildCount() > index && llChatList.getChildAt(0) instanceof LinearLayout) { // 模拟点击各条记录的删除按钮
                PopupWindow pw = (PopupWindow) ((LinearLayout) llChatList.getChildAt(llChatList.getChildCount() - 1)).getChildAt(0).getTag();
                ((LinearLayout) pw.getContentView()).getChildAt(0).performClick();
            }
        });
        llPopup.addView(cvDelBelow);

        if(role == ChatRole.USER) { // USER角色才有的按钮
            CardView cvEdit = new CardView(this); // 编辑按钮
            cvEdit.setForeground(getDrawable(R.drawable.edit_btn));
            cvEdit.setOnClickListener(view -> {
                popupWindow.dismiss();
                ChatMessage chat = (ChatMessage) llOuter.getTag(); // 获取布局上绑定的聊天记录数据
                String text = chat.contentText;
                if(chat.contentImageBase64 != null) { // 若含有图片则设置为选中的图片
                    if(text.endsWith("\n "))
                        text = text.substring(0, text.length() - 2);
                    selectedImageBitmap = base64ToBitmap(chat.contentImageBase64);
                    btImage.setImageResource(R.drawable.image_enabled);
                } else {
                    selectedImageBitmap = null;
                    btImage.setImageResource(R.drawable.image);
                }
                etUserInput.setText(text); // 添加文本内容到输入框
                cvDelBelow.performClick(); // 删除下方所有对话
            });
            llPopup.addView(cvEdit);

            CardView cvRetry = new CardView(this); // 重试按钮
            cvRetry.setForeground(getDrawable(R.drawable.retry_btn));
            cvRetry.setOnClickListener(view -> {
                popupWindow.dismiss();
                ChatMessage chat = (ChatMessage) llOuter.getTag(); // 获取布局上绑定的聊天记录数据
                String text = chat.contentText;
                if(chat.contentImageBase64 != null) { // 若含有图片则设置为选中的图片
                    if(text.endsWith("\n "))
                        text = text.substring(0, text.length() - 2);
                    selectedImageBitmap = base64ToBitmap(chat.contentImageBase64);
                } else {
                    selectedImageBitmap = null;
                }
                cvDelBelow.performClick(); // 删除下方所有对话
                sendQuestion(text); // 重新发送问题
            });
            llPopup.addView(cvRetry);
        }

        CardView cvCopy = new CardView(this); // 复制按钮
        cvCopy.setForeground(getDrawable(R.drawable.copy_btn));
        cvCopy.setOnClickListener(view -> { // 复制文本内容到剪贴板
            popupWindow.dismiss();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("chat", tvContent.getText().toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.toast_clipboard, Toast.LENGTH_SHORT).show();
        });
        llPopup.addView(cvCopy);

        for(int i = 0; i < llPopup.getChildCount(); i++) { // 设置弹出按钮的样式
            CardView cvBtn = (CardView) llPopup.getChildAt(i);
            cvBtn.setLayoutParams(popupIconParams);
            cvBtn.setCardBackgroundColor(Color.WHITE);
            cvBtn.setRadius(dpToPx(5));
        }

        ivIcon.setOnClickListener(view -> { // 点击头像时弹出操作按钮
            popupWindow.showAsDropDown(view, dpToPx(30), -dpToPx(35));
        });

        llOuter.addView(ivIcon);
        llOuter.addView(tvContent);

        llChatList.addView(llOuter);

        return llOuter;
    }

    // 发送一个提问，input为null时则从输入框获取
    private void sendQuestion(String input){
        boolean isMultiChat = currentTemplateParams.getBool("chat", multiChat);

        if(!isMultiChat) { // 若为单次对话模式则新建一个聊天
            ((CardView) findViewById(R.id.cv_new_chat)).performClick();
        }

        // 处理提问文本内容
        String userInput = (input == null) ? etUserInput.getText().toString() : input;
        if(multiChatList.size() == 0 && input == null) { // 由用户输入触发的第一次对话需要添加模板内容
            PromptTabData tabData = GlobalDataHolder.getTabDataList().get(selectedTab);
            String template = tabData.getFormattedPrompt(getTemplateParamsFromView());
            if(currentTemplateParams.getBool("system", false)) {
                multiChatList.add(new ChatMessage(ChatRole.SYSTEM).setText(template));
                multiChatList.add(new ChatMessage(ChatRole.USER).setText(userInput));
            } else {
                if(!template.contains("%input%") && !template.contains("${input}"))
                    template += "${input}";
                String question = template.replace("%input%", userInput).replace("${input}", userInput);
                multiChatList.add(new ChatMessage(ChatRole.USER).setText(question));
            }
            currentConversation.title = String.format("%s%s%s",
                    tabData.getTitle(),
                    (!tabData.getTitle().isEmpty() && !userInput.isEmpty()) ? " | " : "",
                    userInput.substring(0, Math.min(100, userInput.length())).replaceAll("\n", " ")); // 保存对话标题
        } else {
            multiChatList.add(new ChatMessage(ChatRole.USER).setText(userInput));
        }

        if(selectedImageBitmap != null) { // 若有选中的图片则添加到聊天记录数据中
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            selectedImageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
            byte[] bytes = baos.toByteArray();
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            multiChatList.get(multiChatList.size() - 1).setImage(base64);
        }

        if(llChatList.getChildCount() > 0 && llChatList.getChildAt(0) instanceof TextView) { // 若有占位TextView则删除
            llChatList.removeViewAt(0);
        }

        if(isMultiChat && llChatList.getChildCount() > 0) { // 连续对话模式下，将第一条提问改写为添加模板后的内容
            LinearLayout llFirst = (LinearLayout) llChatList.getChildAt(0);
            TextView tvFirst = (TextView) llFirst.getChildAt(1);
            ChatMessage firstChat = (ChatMessage) llFirst.getTag();
            if(firstChat.role == ChatRole.USER) {
                if (firstChat.contentImageBase64 != null && tvFirst.getText().toString().endsWith("\n ")) { // 若有附加图片则也要一并添加
                    SpannableString oldText = (SpannableString) tvFirst.getText();
                    ImageSpan imgSpan = oldText.getSpans(oldText.length() - 1, oldText.length(), ImageSpan.class)[0];
                    SpannableString newText = new SpannableString(firstChat.contentText + "\n ");
                    newText.setSpan(imgSpan, newText.length() - 1, newText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    tvFirst.setText(newText);
                } else {
                    tvFirst.setText(firstChat.contentText);
                }
            }
        }

        if(GlobalDataHolder.getOnlyLatestWebResult()) { // 若设置为仅保留最新网页数据，删除之前的所有网页数据
            for (int i = 0; i < multiChatList.size(); i++) {
                ChatMessage chatItem = multiChatList.get(i);
                if (chatItem.role == ChatRole.FUNCTION) {
                    multiChatList.remove(i);
                    i--;
                    if(i > 0 && multiChatList.get(i).role == ChatRole.ASSISTANT) { // 也要删除调用Function的Assistant记录
                        multiChatList.remove(i);
                        i--;
                    }
                }
            }
        }

        // 添加对话布局
        LinearLayout llInput = addChatView(ChatRole.USER, isMultiChat ? multiChatList.get(multiChatList.size() - 1).contentText : userInput, multiChatList.get(multiChatList.size() - 1).contentImageBase64);
        LinearLayout llReply = addChatView(ChatRole.ASSISTANT, getString(R.string.text_waiting_reply), null);

        llInput.setTag(multiChatList.get(multiChatList.size() - 1)); // 将对话数据绑定到布局上

        tvGptReply = (TextView) llReply.getChildAt(1);

        scrollChatAreaToBottom();

        chatApiBuffer = "";
        ttsSentenceEndIndex = 0;
        chatApiClient.sendPromptList(multiChatList);
        btImage.setImageResource(R.drawable.image);
        selectedImageBitmap = null;
        btSend.setImageResource(R.drawable.cancel_btn);
    }

    // 向GPT返回Function结果
    private void postSendFunctionReply(String funcName, String reply) {
        handler.post(() -> {
            Log.d("FunctionCall", "postSendFunctionReply: " + funcName);
            multiChatList.add(new ChatMessage(ChatRole.FUNCTION).setFunction(funcName).setText(reply));
            chatApiClient.sendPromptList(multiChatList);
        });
    }

    // 将聊天记录恢复到界面上
    private void reloadConversation(Conversation conversation) {
        (findViewById(R.id.cv_new_chat)).performClick(); // 新建一个聊天

        currentConversation = conversation;
        multiChatList = conversation.messages;

        llChatList.removeViewAt(0); // 删除占位TextView
        for(ChatMessage chatItem : multiChatList) { // 依次添加对话布局
            if(chatItem.role == ChatRole.USER || (chatItem.role == ChatRole.ASSISTANT && chatItem.functionName == null)) {
                LinearLayout llChatItem = addChatView(chatItem.role, chatItem.contentText, chatItem.contentImageBase64);
                llChatItem.setTag(chatItem);
            }
        }
        scrollChatAreaToBottom();
    }

    // 清空聊天界面
    private void clearChatListView() {
        if(chatApiClient.isStreaming()){
            chatApiClient.stop();
        }
        llChatList.removeAllViews();
        tts.stop();

        TextView tv = new TextView(this); // 清空列表后添加一个占位TextView
        tv.setTextColor(Color.parseColor("#000000"));
        tv.setTextSize(16);
        tv.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
        tv.setText(R.string.default_greeting);
        tvGptReply = tv;
        llChatList.addView(tv);
    }

    // 转换dp为px
    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    // 等比缩放Bitmap到给定的尺寸范围内
    private Bitmap resizeBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale = 1;
        if(width > maxWidth || height > maxHeight)
            scale = Math.min((float)maxWidth / width, (float)maxHeight / height);
        return Bitmap.createScaledBitmap(bitmap, (int)(width * scale), (int)(height * scale), true);
    }

    // 将Base64编码转换为Bitmap
    private Bitmap base64ToBitmap(String base64) {
        byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    // onDestroy->false onCreate->true
    public static boolean isAlive() {
        return isAlive;
    }

    // onPause->false onResume->true
    public static boolean isRunning() {
        return isRunning;
    }

    // 申请动态权限
    private void requestPermission() {
        String[] permissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        ArrayList<String> toApplyList = new ArrayList<String>();

        for (String perm : permissions) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, perm)) {
                toApplyList.add(perm);
            }
        }
        String[] tmpList = new String[toApplyList.size()];
        if (!toApplyList.isEmpty()) {
            requestPermissions(toApplyList.toArray(tmpList), 123);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isRunning = true;
        Log.d("main activity", "back to main activity");
    }

    @Override
    protected void onPause() {
        super.onPause();
        isRunning = false;
        Log.d("main activity", "leave main activity");
    }

    @Override
    protected void onDestroy() {
        isAlive = false;
        LocalBroadcastManager.getInstance(this).unregisterReceiver(localReceiver);
        asrClient.destroy();
        tts.stop();
        tts.shutdown();
        webScraper.destroy();
        if(((multiChatList.size() > 0 && multiChatList.get(0).role != ChatRole.SYSTEM) || (multiChatList.size() > 1 && multiChatList.get(0).role == ChatRole.SYSTEM)) &&
                GlobalDataHolder.getAutoSaveHistory()) // 包含有效对话则保存当前对话
            chatManager.addConversation(currentConversation);
        chatManager.removeEmptyConversations();
        chatManager.destroy();
        super.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.translate_up_in, R.anim.translate_down_out);
    }
}