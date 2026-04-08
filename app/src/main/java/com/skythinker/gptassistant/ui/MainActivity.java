package com.skythinker.gptassistant.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.PaintDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONObject;
import io.noties.prism4j.annotations.PrismBundle;

import com.skythinker.gptassistant.BuildConfig;
import com.skythinker.gptassistant.data.ChatManager;
import com.skythinker.gptassistant.data.ChatManager.ChatMessage.ChatRole;
import com.skythinker.gptassistant.data.ChatManager.ChatMessage;
import com.skythinker.gptassistant.data.ChatManager.MessageList;
import com.skythinker.gptassistant.data.ChatManager.Conversation;
import com.skythinker.gptassistant.service.AgentAccessibilityService;
import com.skythinker.gptassistant.tool.DocumentParser;
import com.skythinker.gptassistant.data.GlobalDataHolder;
import com.skythinker.gptassistant.data.MainActionLayoutItem;
import com.skythinker.gptassistant.data.MainActionRegistry;
import com.skythinker.gptassistant.data.MainActionSpec;
import com.skythinker.gptassistant.tool.GlobalUtils;
import com.skythinker.gptassistant.tool.MarkdownRenderer;
import com.skythinker.gptassistant.service.MyAccessbilityService;
import com.skythinker.gptassistant.data.PromptTabData;
import com.skythinker.gptassistant.R;
import com.skythinker.gptassistant.tool.WebScraper;
import com.skythinker.gptassistant.api.ChatApiClient;
import com.skythinker.gptassistant.asr.AsrClientBase;
import com.skythinker.gptassistant.asr.BaiduAsrClient;
import com.skythinker.gptassistant.asr.GoogleAsrClient;
import com.skythinker.gptassistant.asr.HmsAsrClient;
import com.skythinker.gptassistant.asr.WhisperAsrClient;

@SuppressLint({"UseCompatLoadingForDrawables", "JavascriptInterface", "SetTextI18n"})
@PrismBundle(includeAll = true)
public class MainActivity extends Activity {

    private int selectedTab = 0;
    private TextView tvGptReply;
    private EditText etUserInput;
    private ImageButton btSend, btAttachment;
    private ScrollView svChatArea;
    private LinearLayout llChatList;
    private LinearLayout llPrimaryActionList;
    private LinearLayout llSecondaryActionList;
    private CardView cvMore;
    private PopupWindow pwMenu;
    private final Map<String, CardView> mainActionButtonMap = new HashMap<>();
    private Handler handler;
    private MarkdownRenderer markdownRenderer;
    private long asrStartTime = 0;
    BroadcastReceiver localReceiver = null;

    private static boolean isAlive = false;
    private static boolean isRunning = false;

    ChatApiClient chatApiClient = null;
    private String chatApiBuffer = "";
    private String pendingAssistantSegmentBuffer = "";
    private ChatMessage currentReplyAnchorMessage = null;

    private TextToSpeech tts = null;
    private boolean ttsEnabled = true;
    final private List<String> ttsSentenceSeparator = Arrays.asList("。", ".", "？", "?", "！", "!", "……", "\n"); // 用于为TTS断句
    private int ttsSentenceEndIndex = 0;
    private String ttsLastId = "";
    private long lastReplyRenderTime = 0;

    private boolean multiChat = false;
    ChatManager chatManager = null;
    private Conversation currentConversation = null; // 当前会话信息
    private MessageList multiChatList = null; // 指向currentConversation.messages

    private boolean multiVoice = false;

    private boolean networkEnabled = false;
    private boolean agentMode = false;

    private JSONObject currentTemplateParams = null; // 当前模板参数

    AsrClientBase asrClient = null;
    AsrClientBase.IAsrCallback asrCallback = null;

    WebScraper webScraper = null;

    Uri photoUri = null;

    ArrayList<ChatMessage.Attachment> selectedAttachments = new ArrayList<>(); // 选中的附件列表

    DocumentParser documentParser = null;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() { // 全局异常捕获
            @Override
            public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
                Log.e("UncaughtException", thread.getClass().getName() + " " + throwable.getMessage());
                throwable.printStackTrace();
//                GlobalUtils.copyToClipboard(MainActivity.this, Log.getStackTraceString(throwable));
                System.exit(-1);
            }
        });

        handler = new Handler(); // 初始化Handler

        GlobalDataHolder.init(this); // 初始化全局共享数据

        // 初始化Markdown渲染器
        markdownRenderer = new MarkdownRenderer(this);

        // 初始化TTS
        tts = new TextToSpeech(this, status -> {
            if(status == TextToSpeech.SUCCESS) {
                int res = tts.setLanguage(Locale.getDefault());
                if(res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Unsupported language.");
                }else{
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) {
//                            Log.d("TTS", "onStart: " + utteranceId);
                        }

                        @Override
                        public void onDone(String utteranceId) {
//                            Log.d("TTS", "onDone: " + utteranceId);
                            if(ttsLastId.equals(utteranceId) && !chatApiClient.isStreaming()) {
                                Log.d("TTS", "Queue finished");
                                if(multiVoice) {
                                    Intent intent = new Intent("com.skythinker.gptassistant.KEY_SPEECH_START");
                                    LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(intent);
                                }
                            }
                        }

                        @Override
                        public void onError(String utteranceId) {
                            Log.e("TTS", "onError: " + utteranceId);
                        }
                    });
                    Log.d("TTS", "Init success.");
                }
            }else{
                Log.e("TTS", "Init failed. ErrorCode: " + status);
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
        btAttachment = findViewById(R.id.bt_attachment);
        svChatArea = findViewById(R.id.sv_chat_list);
        llChatList = findViewById(R.id.ll_chat_list);
        llPrimaryActionList = findViewById(R.id.ll_main_primary_actions);
        cvMore = findViewById(R.id.cv_more);

        documentParser = new DocumentParser(this); // 初始化文档解析器
        handleShareIntent(getIntent()); // 处理分享的文本/图片

        updateForMultiWindowMode(); // 根据当前窗口模式控制UI是否占满屏幕

        findViewById(R.id.ll_main).setOnDragListener((v, event) -> { // 处理拖拽事件（跨应用拖拽）
            if(event.getAction() == DragEvent.ACTION_DROP) {
                requestDragAndDropPermissions(event);
                ClipData clipData = event.getClipData();
                if(clipData != null) {
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        ClipData.Item item = clipData.getItemAt(i);
                        Uri uri = item.getUri();
                        if (uri != null) { // 文件、图片作为附件处理
                            addAttachment(uri);
                        } else { // 纯文本直接添加到输入框
                            if(item.getText() != null) {
                                String text = item.getText().toString();
                                String inputText = etUserInput.getText().toString();
                                if(!inputText.equals("")) {
                                    etUserInput.setText(inputText + "\n\n" + text);
                                } else {
                                    etUserInput.setText(text);
                                }
                            }
                        }
                    }
                }
            }
            return true;
        });

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
                    @Override
                    public void onMsgReceive(String message) { // 收到GPT回复（增量）
                        chatApiBuffer += message;
                        pendingAssistantSegmentBuffer += message;
                        if(System.currentTimeMillis() - lastReplyRenderTime > 100) { // 限制最高渲染频率10Hz
                            handler.post(() -> {
                                boolean isBottom = svChatArea.getChildAt(0).getBottom()
                                        <= svChatArea.getHeight() + svChatArea.getScrollY(); // 判断消息布局是否在底部

                                markdownRenderer.render(tvGptReply, chatApiBuffer); // 渲染Markdown
                                tvGptReply.requestLayout();

                                if (isBottom) {
                                    scrollChatAreaToBottom(); // 渲染前在底部则渲染后滚动到底部
                                }

                                if (currentTemplateParams.getBool("speak", ttsEnabled)) { // 处理TTS
                                    if (chatApiBuffer.startsWith("<think>\n") && !chatApiBuffer.contains("\n</think>\n")) { // 不朗读思维链部分
                                        ttsSentenceEndIndex = tvGptReply.getText().toString().length(); // 正在思考则设置tts起点在末尾
                                    } else {
                                        String wholeText = tvGptReply.getText().toString(); // 获取可朗读的文本
                                        if (ttsSentenceEndIndex < wholeText.length()) {
                                            int nextSentenceEndIndex = wholeText.length();
                                            boolean found = false;
                                            for (String separator : ttsSentenceSeparator) { // 查找最后一个断句分隔符
                                                int index = wholeText.indexOf(separator, ttsSentenceEndIndex);
                                                if (index != -1 && index < nextSentenceEndIndex) {
                                                    nextSentenceEndIndex = index + separator.length();
                                                    found = true;
                                                }
                                            }
                                            if (found) { // 找到断句分隔符则添加到朗读队列
                                                String sentence = wholeText.substring(ttsSentenceEndIndex, nextSentenceEndIndex);
                                                ttsSentenceEndIndex = nextSentenceEndIndex;
                                                String id = UUID.randomUUID().toString();
                                                tts.speak(sentence, TextToSpeech.QUEUE_ADD, null, id);
                                                ttsLastId = id;
                                            }
                                        }
                                    }
                                }
                            });

                            lastReplyRenderTime = System.currentTimeMillis();
                        }
                    }

                    @Override
                    public void onFinished(boolean completed) { // GPT回复完成
                        handler.post(() -> {
                            try {
                                markdownRenderer.render(tvGptReply, chatApiBuffer); // 渲染Markdown
                                tvGptReply.requestLayout();
                                String ttsText = tvGptReply.getText().toString();
                                if(currentTemplateParams.getBool("speak", ttsEnabled) && ttsText.length() > ttsSentenceEndIndex) { // 如果TTS开启则朗读剩余文本
                                    String id = UUID.randomUUID().toString();
                                    tts.speak(ttsText.substring(ttsSentenceEndIndex), TextToSpeech.QUEUE_ADD, null, id);
                                    ttsLastId = id;
                                }
                                if(!pendingAssistantSegmentBuffer.isEmpty()) { // 仅保存自上个tool边界之后的新assistant文本
                                    ChatMessage assistantMessage = new ChatMessage(ChatRole.ASSISTANT).setText(pendingAssistantSegmentBuffer);
                                    multiChatList.add(assistantMessage);
                                    currentReplyAnchorMessage = assistantMessage;
                                    if(tvGptReply.getParent() instanceof LinearLayout) {
                                        ((LinearLayout) tvGptReply.getParent()).setTag(currentReplyAnchorMessage);
                                    }
                                    pendingAssistantSegmentBuffer = "";
                                }
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

                    private final ArrayList<ChatApiClient.CallingFunction> callingFunctions = new ArrayList<>();

                    private void callFunction(ChatApiClient.CallingFunction function) {
                        if (function.name.equals("get_html_text")) { // 调用联网函数
                            try {
                                JSONObject argJson = new JSONObject(function.arguments);
                                String url = argJson.getStr("url"); // 获取URL
                                runOnUiThread(() -> {
                                    webScraper.load(url, new WebScraper.Callback() { // 抓取网页内容
                                        @Override
                                        public void onLoadResult(String result) {
                                            processFunctionResult(function, result); // 返回网页内容给GPT
//                                            Log.d("FunctionCall", String.format("Response: %s", result));
                                        }

                                        @Override
                                        public void onLoadFail(String message) {
                                            processFunctionResult(function, "Failed to get response of this url. " + message);
                                        }
                                    });
                                    Log.d("FunctionCall", String.format("Loading url: %s", url));
                                });
                            } catch (JSONException e) {
                                e.printStackTrace();
                                processFunctionResult(function, "Error when getting response.");
                            }
                        }else if (function.name.equals("get_widget_tree")) {
                            if(AgentAccessibilityService.isConnected()) {
                                moveTaskToBack(true); // 最小化当前窗口
                                handler.postDelayed(() -> {
                                    JSONObject json = AgentAccessibilityService.staticThis.getWidgetJson();
                                    json.putOpt("package", AgentAccessibilityService.staticThis.getCurrentPackageName());
                                    Intent intent = new Intent(MainActivity.this, MainActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(intent);
                                    processFunctionResult(function, json.toString());
                                }, 500);
                            } else {
                                processFunctionResult(function, "Accessibility service is not enabled.");
                            }
                        } else if (function.name.equals("widget_action")) {
                            try {
                                JSONObject argJson = new JSONObject(function.arguments);
                                int id =  Integer.parseInt(argJson.getStr("id")); // 获取widget id
                                String action = argJson.getStr("action", "click"); // 获取操作类型
                                String text = argJson.getStr("input_text", ""); // 获取输入文本
                                if(AgentAccessibilityService.isConnected()) {
                                    moveTaskToBack(true); // 最小化当前窗口
                                    handler.postDelayed(() -> {
                                        AgentAccessibilityService.staticThis.rootWidgetNode.performAction(id, action, text);
                                        handler.postDelayed(() -> {
                                            JSONObject json = AgentAccessibilityService.staticThis.getWidgetJson();
                                            json.putOpt("package", AgentAccessibilityService.staticThis.getCurrentPackageName());
                                            Intent intent = new Intent(MainActivity.this, MainActivity.class);
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            startActivity(intent);
                                            processFunctionResult(function, json.toString());
                                        }, 2000);
                                    }, 500);
                                } else {
                                    processFunctionResult(function, "Error: accessibility service is not enabled.");
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                processFunctionResult(function, "Error when getting response.");
                            }
                        } else if(function.name.equals("find_package_name")) {
                            try {
                                JSONObject argJson = new JSONObject(function.arguments);
                                String queryApp = argJson.getStr("app_name"); // 获取应用名称
                                PackageManager pm = getPackageManager();
                                Intent main = new Intent(Intent.ACTION_MAIN, null);
                                main.addCategory(Intent.CATEGORY_LAUNCHER);
                                List<ResolveInfo> packages = pm.queryIntentActivities(main, 0); // 获取所有可启动应用
                                JSONObject resultJson = new JSONObject();
                                for(ResolveInfo resolve_info : packages) {
                                    String package_name = resolve_info.activityInfo.packageName;
                                    String app_name = (String)pm.getApplicationLabel(pm.getApplicationInfo(package_name, PackageManager.GET_META_DATA));
                                    if(app_name.toLowerCase().contains(queryApp.toLowerCase()) ||
                                            queryApp.toLowerCase().contains(app_name.toLowerCase())) {
                                        resultJson.putOpt(app_name, package_name);
                                    }
                                }
                                if(!resultJson.isEmpty()) {
                                    processFunctionResult(function, resultJson.toString());
                                } else {
                                    processFunctionResult(function, "No matching package found.");
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                processFunctionResult(function, "Error when getting response.");
                            }
                        } else if(function.name.equals("launch_package")) {
                            try {
                                JSONObject argJson = new JSONObject(function.arguments);
                                String packageName = argJson.getStr("package"); // 获取包名
                                Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
                                if(intent != null) {
                                    startActivity(intent); // 启动应用
                                    handler.postDelayed(() -> {
                                        if(packageName.equals(AgentAccessibilityService.staticThis.getCurrentPackageName())) {
                                            Intent selfIntent = new Intent(MainActivity.this, MainActivity.class);
                                            selfIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            startActivity(selfIntent);
                                            processFunctionResult(function, "OK");
                                        } else {
                                            processFunctionResult(function, "Error: failed to launch the package.");
                                        }
                                    }, 2000);
                                } else {
                                    processFunctionResult(function, "Error: package not found.");
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                                processFunctionResult(function, "Error when getting response.");
                            }
                        } else if (function.name.equals("exit_voice_chat")) {
                            if (multiVoice)
                                runOnUiThread(() -> {toggleVoiceChat();});
                            processFunctionResult(function, "OK");
                        } else {
                            processFunctionResult(function, "Function not found.");
                            Log.d("FunctionCall", String.format("Function not found: %s", function.name));
                        }
                    }
                    private void processFunctionResult(ChatApiClient.CallingFunction function, String result) {
                        handler.post(() -> {
                            Log.d("MainActivity", "function result: " + function.name);
                            Log.d("MainActivity", "function result: " + result);
                            ChatMessage functionMessage = new ChatMessage(ChatRole.FUNCTION).addFunctionCall(function.toolId, function.name, function.arguments, result);
                            multiChatList.add(functionMessage);
                            currentReplyAnchorMessage = functionMessage;
                            if(tvGptReply != null && tvGptReply.getParent() instanceof LinearLayout) {
                                ((LinearLayout) tvGptReply.getParent()).setTag(currentReplyAnchorMessage);
                            }
                            callingFunctions.remove(function); // 从函数调用列表中移除已完成的函数
                            if(callingFunctions.size() == 0) { // 所有函数调用完成，发送给GPT
                                lastReplyRenderTime = 0;
                                sendChatList();
                            } else {
                                callFunction(callingFunctions.get(0)); // 继续处理下一个函数调用
                            }
                        });
                    }

                    @Override
                    public void onFunctionCall(ArrayList<ChatApiClient.CallingFunction> functions) { // 收到函数调用请求
                        handler.post(() -> {
                            if(!pendingAssistantSegmentBuffer.isEmpty()) { // 先保存tool边界前的assistant文本
                                ChatMessage assistantTextMessage = new ChatMessage(ChatRole.ASSISTANT).setText(pendingAssistantSegmentBuffer);
                                multiChatList.add(assistantTextMessage);
                                currentReplyAnchorMessage = assistantTextMessage;
                                if(tvGptReply != null && tvGptReply.getParent() instanceof LinearLayout) {
                                    ((LinearLayout) tvGptReply.getParent()).setTag(currentReplyAnchorMessage);
                                }
                            }
                            ChatMessage assistantMessage = new ChatMessage(ChatRole.ASSISTANT);
                            for(ChatApiClient.CallingFunction function : functions) {
                                Log.d("FunctionCall", String.format("%s: %s", function.name, function.arguments));
                                assistantMessage.addFunctionCall(function.toolId, function.name, function.arguments, null);
                            }
                            multiChatList.add(assistantMessage); // 保存请求到聊天数据列表
                            currentReplyAnchorMessage = assistantMessage;
                            if(tvGptReply != null && tvGptReply.getParent() instanceof LinearLayout) {
                                ((LinearLayout) tvGptReply.getParent()).setTag(currentReplyAnchorMessage);
                            }

                            pendingAssistantSegmentBuffer = "";
                            for(ChatApiClient.CallingFunction function : functions) { // 仅向UI追加提示，不写入assistant文本分段
                                if(!chatApiBuffer.isEmpty() && !chatApiBuffer.endsWith("\n")) {
                                    chatApiBuffer += "\n\n";
                                }
                                if(function.name.equals("get_html_text")) {
                                    try {
                                        String url = new JSONObject(function.arguments).getStr("url");
                                        chatApiBuffer += formatWebReferenceNotice(url) + "\n\n";
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                } else {
									chatApiBuffer += String.format(getString(R.string.text_tool_call_notice_format), function.name) + "\n\n";
								}
                            }
                            boolean isBottom = svChatArea.getChildAt(0).getBottom()
                                    <= svChatArea.getHeight() + svChatArea.getScrollY();
                            markdownRenderer.render(tvGptReply, chatApiBuffer);
                            tvGptReply.requestLayout();
                            if(isBottom) {
                                scrollChatAreaToBottom();
                            }

                            callingFunctions.clear();
                            callingFunctions.addAll(functions); // 保存函数调用列表（浅拷贝）

                            callFunction(callingFunctions.get(0)); // 处理第一个函数调用
                        });
                    }
                });

        chatApiClient.setTemperature(GlobalDataHolder.getGptTemperature());

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
                etUserInput.setText("");
            }
        });

        // 附件选择按钮点击事件
        btAttachment.setOnClickListener(view -> {
            // 弹出附件选择菜单
            PopupWindow popupWindow = getAttachmentPopupWindow();
            popupWindow.showAtLocation(btAttachment, Gravity.BOTTOM | Gravity.START, dpToPx(3), dpToPx(43));
            // 设置背景透明度
            View container = popupWindow.getContentView().getRootView();
            Context context = popupWindow.getContentView().getContext();
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) container.getLayoutParams();
            params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            params.dimAmount = 0.3f;
            wm.updateViewLayout(container, params);
        });

        // 长按输入框开始录音或清空内容
        etUserInput.setOnLongClickListener(view -> {
            if(etUserInput.getText().toString().equals("")) {
                Intent broadcastIntent = new Intent("com.skythinker.gptassistant.KEY_SPEECH_START");
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                view.setTag("recording");
            } else {
                etUserInput.setText("");
            }
            return true;
        });

        etUserInput.setOnTouchListener((view, motionEvent) -> {
            if(motionEvent.getAction() == MotionEvent.ACTION_UP){
                if("recording".equals(view.getTag())){
                    Intent broadcastIntent = new Intent("com.skythinker.gptassistant.KEY_SPEECH_STOP");
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                    view.setTag(null);
                }
            }
            return false;
        });

        initMainActionMenu();

        // 更多按钮仍然保留为固定入口，避免二级菜单被隐藏后没有打开方式。
        cvMore.setOnClickListener(view -> {
            if(cvMore.getVisibility() == View.VISIBLE) {
                pwMenu.showAsDropDown(view, 0, 0);
            }
        });

        // 上方空白区域点击事件，退出程序
        (findViewById(R.id.view_bg_empty)).setOnClickListener(view -> {
            finish();
        });

        // 用户设置为启动时开启连续对话
        if(GlobalDataHolder.getDefaultEnableMultiChat()){
            multiChat = true;
        }

        // 用户设置为启动时开启TTS
        if(!GlobalDataHolder.getDefaultEnableTts()){
            ttsEnabled = false;
        }

        // 上次开启了联网
        if(GlobalDataHolder.getEnableInternetAccess()){
            networkEnabled = true;
        }

        // 上次开启了Agent模式
        if(GlobalDataHolder.getAgentMode()){
            if(AgentAccessibilityService.isConnected()){
                agentMode = true;
            }else{
                agentMode = false;
                GlobalDataHolder.saveAgentModeSetting(false);
            }
        }

        renderMainActionButtons();
        startNewChat(); // 初始化对话列表

        // 处理选中的模板
        if(GlobalDataHolder.getSelectedTab() != -1 && GlobalDataHolder.getSelectedTab() < GlobalDataHolder.getTabDataList().size())
            selectedTab = GlobalDataHolder.getSelectedTab();
        switchToTemplate(selectedTab);
        Button selectedTabBtn = (Button) ((LinearLayout) findViewById(R.id.tabs_layout)).getChildAt(selectedTab); // 将选中的模板按钮滚动到可见位置
        selectedTabBtn.getParent().requestChildFocus(selectedTabBtn, selectedTabBtn);

        updateModelSpinner(); // 设置模型选择下拉框

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
                if(multiVoice) {
                    toggleVoiceChat();
                }
            }

            @Override
            public void onResult(String result) {
                if(result != null) {
                    runOnUiThread(() -> etUserInput.setText(result));
                }
            }

            @Override
            public void onAutoStop() {
                if(multiVoice) {
                    Intent broadcastIntent = new Intent("com.skythinker.gptassistant.KEY_SPEECH_STOP");
                    LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(broadcastIntent);
                    Intent broadcastIntent2 = new Intent("com.skythinker.gptassistant.KEY_SEND");
                    LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(broadcastIntent2);
                }
            }
        };
        // 设置使用百度/Whisper/华为语音识别
        if(GlobalDataHolder.getAsrUseBaidu()) {
            setAsrClient("baidu");
        } else if(GlobalDataHolder.getAsrUseWhisper()) {
            setAsrClient("whisper");
        } else if(GlobalDataHolder.getAsrUseGoogle()) {
            setAsrClient("google");
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
                    asrClient.startRecognize();
                    asrStartTime = System.currentTimeMillis();
                    etUserInput.setText("");
                    etUserInput.setHint(R.string.text_listening_hint);
                } else if(action.equals("com.skythinker.gptassistant.KEY_SPEECH_STOP")) { // 停止语音识别
                    etUserInput.setHint(R.string.text_input_hint);
                    if(System.currentTimeMillis() - asrStartTime < 1000) {
                        asrClient.cancelRecognize();
                    } else {
                        asrClient.stopRecognize();
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

        //检查更新
        if(!BuildConfig.VERSION_NAME.equals(GlobalDataHolder.getLatestVersion())) {
            GlobalUtils.showToast(this, getString(R.string.toast_update_available), false);
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
        } else if (type.equals("google")) {
            asrClient = new GoogleAsrClient(this);
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

    private String formatWebReferenceNotice(String url) {
        String displayHost = url;
        try {
            String host = Uri.parse(url).getHost();
            if(host != null && host.length() > 0) {
                displayHost = host.startsWith("www.") && host.length() > 4 ? host.substring(4) : host;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return String.format(getString(R.string.text_ref_web_notice_format), displayHost, url);
    }

    // 设置是否启用Agent模式
    private void setAgentModeEnabled(boolean enabled) {
        if(enabled) {
            chatApiClient.addFunction("get_widget_tree", "get widget information tree on the screen", "{}", new String[]{});
            chatApiClient.addFunction("widget_action", "perform an action on a widget in widget tree and get new widget tree",
                    "{id: {type: string, description: widget id}," +
                            "action: {type: string, description: action name, enum: [click, long_click, scroll_down, scroll_up, edit]}," +
                            "input_text: {type: string, description: input text}}",
                    new String[]{"id", "action"});
            chatApiClient.addFunction("find_package_name", "find package name by app name",
                    "{app_name: {type: string, description: application name}}", new String[]{"app_name"});
            chatApiClient.addFunction("launch_package", "launch a package by package name",
                    "{package: {type: string, description: package name}}", new String[]{"package"});
        } else {
            chatApiClient.removeFunction("get_widget_tree");
            chatApiClient.removeFunction("widget_action");
            chatApiClient.removeFunction("find_package_name");
            chatApiClient.removeFunction("launch_package");
        }
    }

    // 初始化“更多”弹出菜单，后续二级按钮全部动态渲染到这个容器里
    private void initMainActionMenu() {
        View menuView = LayoutInflater.from(this).inflate(R.layout.main_popup_menu, null);
        llSecondaryActionList = menuView.findViewById(R.id.ll_main_secondary_actions);
        pwMenu = new PopupWindow(menuView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pwMenu.setOutsideTouchable(true);
    }

    // 根据用户配置和默认规则重建主界面按钮
    private void renderMainActionButtons() {
        llPrimaryActionList.removeAllViews();
        llSecondaryActionList.removeAllViews();
        mainActionButtonMap.clear();

        int primaryCount = 0;
        int secondaryCount = 0;
        List<MainActionLayoutItem> actionItems = MainActionRegistry.getResolvedLayout(GlobalDataHolder.getMainActionLayout());
        for (MainActionLayoutItem item : actionItems) {
            if(item.placement == MainActionRegistry.PLACEMENT_HIDDEN) {
                continue;
            }
            MainActionSpec spec = MainActionRegistry.findSpec(item.actionId);
            if(spec == null) {
                continue;
            }

            LinearLayout parent = llSecondaryActionList;
            boolean addToPrimary = item.placement == MainActionRegistry.PLACEMENT_PRIMARY
                    && primaryCount < MainActionRegistry.MAX_PRIMARY_ACTION_COUNT;
            if(addToPrimary) {
                parent = llPrimaryActionList;
                primaryCount++;
            } else {
                secondaryCount++;
            }
            addMainActionButton(parent, spec);
        }

        cvMore.setVisibility(secondaryCount > 0 ? View.VISIBLE : View.GONE);
        if(secondaryCount == 0 && pwMenu.isShowing()) {
            pwMenu.dismiss();
        }
    }

    private void addMainActionButton(LinearLayout parent, MainActionSpec spec) {
        CardView button = (CardView) LayoutInflater.from(this).inflate(R.layout.main_action_button, parent, false);
        button.setCardBackgroundColor(Color.TRANSPARENT);
        button.setForeground(ContextCompat.getDrawable(this, getMainActionForegroundRes(spec.id)));
        button.setContentDescription(getString(spec.titleRes));
        button.setOnClickListener(view -> handleMainActionClick(spec.id));
        parent.addView(button);
        mainActionButtonMap.put(spec.id, button);
    }

    private void handleMainActionClick(String actionId) {
        switch (actionId) {
            case MainActionRegistry.ACTION_NEW_CHAT:
                startNewChat();
                break;
            case MainActionRegistry.ACTION_TTS:
                toggleTts();
                break;
            case MainActionRegistry.ACTION_NETWORK:
                toggleNetwork();
                break;
            case MainActionRegistry.ACTION_AGENT:
                toggleAgentMode();
                break;
            case MainActionRegistry.ACTION_MULTI_CHAT:
                toggleMultiChat();
                break;
            case MainActionRegistry.ACTION_VOICE_CHAT:
                toggleVoiceChat();
                break;
            case MainActionRegistry.ACTION_HISTORY:
                openHistory();
                break;
            case MainActionRegistry.ACTION_SETTINGS:
                openSettings();
                break;
            case MainActionRegistry.ACTION_CLOSE:
                closeMain();
                break;
        }
    }

    private void refreshMainActionButtonState(String actionId) {
        CardView button = mainActionButtonMap.get(actionId);
        if(button == null) {
            return;
        }
        button.setForeground(ContextCompat.getDrawable(this, getMainActionForegroundRes(actionId)));
    }

    private int getMainActionForegroundRes(String actionId) {
        MainActionSpec spec = MainActionRegistry.findSpec(actionId);
        if(spec == null) {
            return R.drawable.more_btn;
        }
        if(spec.alternateIconRes == 0) {
            return spec.normalIconRes;
        }
        switch (actionId) {
            case MainActionRegistry.ACTION_TTS:
                return ttsEnabled ? spec.normalIconRes : spec.alternateIconRes;
            case MainActionRegistry.ACTION_NETWORK:
                return networkEnabled ? spec.alternateIconRes : spec.normalIconRes;
            case MainActionRegistry.ACTION_AGENT:
                return agentMode ? spec.alternateIconRes : spec.normalIconRes;
            case MainActionRegistry.ACTION_MULTI_CHAT:
                return multiChat ? spec.alternateIconRes : spec.normalIconRes;
            case MainActionRegistry.ACTION_VOICE_CHAT:
                return multiVoice ? spec.alternateIconRes : spec.normalIconRes;
            default:
                return spec.normalIconRes;
        }
    }

    // 新建会话一个会话
    private void startNewChat() {
        if(pwMenu.isShowing()) {
            pwMenu.dismiss();
        }
        clearChatListView();
        persistCurrentConversation(false);

        currentConversation = new Conversation();
        multiChatList = currentConversation.messages;
    }

    private boolean hasValidConversation() {
        return currentConversation != null
                && multiChatList != null
                && ((multiChatList.size() > 0 && multiChatList.get(0).role != ChatRole.SYSTEM)
                || (multiChatList.size() > 1 && multiChatList.get(0).role == ChatRole.SYSTEM));
    }

    // 在离开主界面前保存当前会话，避免设置页导入导出漏掉进行中的内容。
    private void persistCurrentConversation(boolean forceSave) {
        if(!hasValidConversation()) {
            return;
        }
        if(!forceSave && !GlobalDataHolder.getAutoSaveHistory()) {
            return;
        }

        currentConversation.updateTime();
        if(currentConversation.id != -1 && chatManager.hasConversation(currentConversation.id)) {
            chatManager.updateConversation(currentConversation);
        } else {
            chatManager.addConversation(currentConversation);
        }
    }

    private void toggleTts() {
        ttsEnabled = !ttsEnabled;
        refreshMainActionButtonState(MainActionRegistry.ACTION_TTS);
        if(ttsEnabled) {
            GlobalUtils.showToast(this, R.string.toast_tts_on, false);
        }else{
            GlobalUtils.showToast(this, R.string.toast_tts_off, false);
            tts.stop();
        }
    }

    private void toggleMultiChat() {
        multiChat = !multiChat;
        refreshMainActionButtonState(MainActionRegistry.ACTION_MULTI_CHAT);
        if(multiChat) {
            GlobalUtils.showToast(this, R.string.toast_multi_chat_on, false);
        }else{
            GlobalUtils.showToast(this, R.string.toast_multi_chat_off, false);
        }
    }

    private void toggleVoiceChat() {
        if(!multiVoice && !ttsEnabled) { // 未开启TTS时不允许开启连续语音对话
            GlobalUtils.showToast(this, R.string.toast_voice_chat_tts_off, false);
            return;
        }
        multiVoice = !multiVoice;
        refreshMainActionButtonState(MainActionRegistry.ACTION_VOICE_CHAT);
        if(multiVoice){
            asrClient.setEnableAutoStop(true);
//            chatApiClient.addFunction("exit_voice_chat", "this should be called when a conversation ends", "{}", new String[]{});
            Intent intent = new Intent("com.skythinker.gptassistant.KEY_SPEECH_START");
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            GlobalUtils.showToast(this, R.string.toast_multi_voice_on, false);
        } else {
            asrClient.setEnableAutoStop(false);
//            chatApiClient.removeFunction("exit_voice_chat");
            Intent intent = new Intent("com.skythinker.gptassistant.KEY_SPEECH_STOP");
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            GlobalUtils.showToast(this, R.string.toast_multi_voice_off, false);
        }
    }

    private void toggleNetwork() {
        networkEnabled = !networkEnabled;
        refreshMainActionButtonState(MainActionRegistry.ACTION_NETWORK);
        if(networkEnabled) {
            GlobalUtils.showToast(this, R.string.toast_network_on, false);
        }else{
            GlobalUtils.showToast(this, R.string.toast_network_off, false);
        }
        setNetworkEnabled(networkEnabled);
        GlobalDataHolder.saveFunctionSetting(networkEnabled, GlobalDataHolder.getWebMaxCharCount(), GlobalDataHolder.getOnlyLatestWebResult());
    }

    private void toggleAgentMode() {
        agentMode = !agentMode;
        if(agentMode) {
            if(AgentAccessibilityService.isConnected()) {
                GlobalUtils.showToast(this, R.string.toast_agent_on, false);
            }else{
                agentMode = false;
                GlobalUtils.showToast(this, R.string.toast_agent_accessibility_off, false);
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            }
        }else{
            GlobalUtils.showToast(this, R.string.toast_agent_off, false);
        }
        refreshMainActionButtonState(MainActionRegistry.ACTION_AGENT);
        setAgentModeEnabled(agentMode);
        GlobalDataHolder.saveAgentModeSetting(agentMode);
    }

    private void openHistory() {
        if(pwMenu.isShowing()) {
            pwMenu.dismiss();
        }
        persistCurrentConversation(false);
        Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
        startActivityForResult(intent, 3);
    }

    private void openSettings() {
        if(pwMenu.isShowing()) {
            pwMenu.dismiss();
        }
        persistCurrentConversation(false);
        startActivityForResult(new Intent(MainActivity.this, TabConfActivity.class), 0);
    }

    private void closeMain() {
        if(pwMenu.isShowing()) {
            pwMenu.dismiss();
        }
        finish();
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

            // 更新GPT客户端相关设置
            chatApiClient.setApiInfo(GlobalDataHolder.getGptApiHost(), GlobalDataHolder.getGptApiKey());
            chatApiClient.setModel(currentTemplateParams.getStr("model", GlobalDataHolder.getGptModel()));
            chatApiClient.setTemperature(GlobalDataHolder.getGptTemperature());

            // 更新所使用的语音识别接口
            if(GlobalDataHolder.getAsrUseBaidu() && !(asrClient instanceof BaiduAsrClient)) {
                setAsrClient("baidu");
            } else if(GlobalDataHolder.getAsrUseWhisper() && !(asrClient instanceof WhisperAsrClient)) {
                setAsrClient("whisper");
            } else if(GlobalDataHolder.getAsrUseGoogle() && !(asrClient instanceof GoogleAsrClient)) {
                setAsrClient("google");
            } else if(!GlobalDataHolder.getAsrUseBaidu() && !GlobalDataHolder.getAsrUseWhisper() && !GlobalDataHolder.getAsrUseGoogle() && !(asrClient instanceof HmsAsrClient)) {
                setAsrClient("hms");
            }

            // 更新Whisper接口的API信息
            if(asrClient instanceof WhisperAsrClient) {
                ((WhisperAsrClient) asrClient).setApiInfo(GlobalDataHolder.getGptApiHost(), GlobalDataHolder.getGptApiKey());
            }

            setNetworkEnabled(currentTemplateParams.getBool("network", GlobalDataHolder.getEnableInternetAccess())); // 更新GPT联网设置
            renderMainActionButtons(); // 设置页可能改了一级/二级布局，返回时需要重建按钮
        } else if((requestCode == 1 || requestCode == 2) && resultCode == RESULT_OK) { // 从相册或相机返回
            Uri uri = requestCode == 1 ? photoUri : data.getData(); // 获取图片URI
            addAttachment(uri);
        } else if(requestCode == 3 && resultCode == RESULT_OK) { // 从聊天历史界面返回
            if(data != null && data.hasExtra("id")) {
                long id = data.getLongExtra("id", -1);
                Log.d("MainActivity", "onActivityResult 3: id=" + id);
                Conversation conversation = chatManager.getConversation(id);
                if(conversation != null) {
                    conversation.updateTime();
                    reloadConversation(conversation);
                    persistCurrentConversation(true);
                }
            }
        } else if(requestCode == 4 && resultCode == RESULT_OK) { // 选择文件
            try {
                ArrayList<Uri> uris = new ArrayList<>();
                ClipData clipData = data.getClipData();
                if(clipData != null) { // 多选文件
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        uris.add(clipData.getItemAt(i).getUri());
                    }
                } else { // 单选文件
                    Uri uri = data.getData();
                    if(uri != null)
                        uris.add(uri);
                }
                for (Uri uri : uris) {
                    addAttachment(uri);
                }
            } catch (Exception e) {
                e.printStackTrace();
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
                        startNewChat();
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
                    et.setHint(R.string.text_temp_param_input_hint);
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
        setAgentModeEnabled(currentTemplateParams.getBool("agent", GlobalDataHolder.getAgentMode()));
        updateTabListView();
        updateTemplateParamsView();
    }

    // 添加一条聊天记录到聊天列表布局
    private LinearLayout addChatView(ChatRole role, String content, ArrayList<ChatMessage.Attachment> attachments) {
        ViewGroup.MarginLayoutParams iconParams = new ViewGroup.MarginLayoutParams(dpToPx(30), dpToPx(30)); // 头像布局参数
        iconParams.setMargins(dpToPx(4), dpToPx(12), dpToPx(4), dpToPx(12));

        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); // 内容布局参数
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
        if(role == ChatRole.USER) {
            SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
            stringBuilder.append(content);
            if (attachments != null) { // 如有图片则在末尾添加ImageSpan
                boolean hasImageAttachment = false;
                for(ChatMessage.Attachment attachment : attachments) {
                    if(attachment.type == ChatMessage.Attachment.Type.IMAGE) {
                        if(!hasImageAttachment) {
                            stringBuilder.append("\ni");
                            hasImageAttachment = true;
                        } else {
                            stringBuilder.append(" i");
                        }
                        Bitmap bitmap = base64ToBitmap(attachment.content);
                        int maxSize = dpToPx(120);
                        bitmap = resizeBitmap(bitmap, maxSize, maxSize);
                        ImageSpan imageSpan = new ImageSpan(this, bitmap);
                        stringBuilder.setSpan(imageSpan, stringBuilder.length() - 1, stringBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        stringBuilder.setSpan(new ClickableSpan() {
                            @Override
                            public void onClick(@NonNull View view) {
                                Bitmap bitmap = base64ToBitmap(attachment.content);
                                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                                LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
                                View dialogView = inflater.inflate(R.layout.image_preview_dialog, null);
                                AlertDialog dialog = builder.create();
                                dialog.show();
                                dialog.getWindow().setContentView(dialogView);
                                ((ImageView) dialogView.findViewById(R.id.iv_image_preview)).setImageBitmap(bitmap);
                                ((TextView) dialogView.findViewById(R.id.tv_image_preview_size)).setText(String.format("%s x %s", bitmap.getWidth(), bitmap.getHeight()));
                                dialogView.findViewById(R.id.cv_image_preview_cancel).setOnClickListener(view1 -> dialog.dismiss());
                                dialogView.findViewById(R.id.cv_image_preview_del).setVisibility(View.GONE);
                                dialogView.findViewById(R.id.cv_image_preview_reselect).setVisibility(View.GONE);
                            }
                        }, stringBuilder.length() - 1, stringBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
                for(ChatMessage.Attachment attachment : attachments) {
                    if(attachment.type == ChatMessage.Attachment.Type.TEXT) {
                        stringBuilder.append("\n").append(attachment.name);
                        stringBuilder.setSpan(new ClickableSpan() {
                            @Override
                            public void onClick(@NonNull View view) {
                                new ConfirmDialog(MainActivity.this)
                                        .setTitle(attachment.name)
                                        .setContent(attachment.content)
                                        .setContentAlignment(View.TEXT_ALIGNMENT_TEXT_START)
                                        .setOkButtonVisibility(View.GONE)
                                        .show();
                            }
                        }, stringBuilder.length() - attachment.name.length(), stringBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
            tvContent.setText(stringBuilder);
        } else if(role == ChatRole.ASSISTANT) {
            markdownRenderer.render(tvContent, content);
        }
        tvContent.setTextSize(16);
        tvContent.setTextColor(Color.BLACK);
        tvContent.setLayoutParams(contentParams);
        tvContent.setSingleLine(false);
        tvContent.setMaxLines(Integer.MAX_VALUE);
        tvContent.setHorizontallyScrolling(false);
        tvContent.setVerticalScrollBarEnabled(false);
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
                if(chat.role == ChatRole.USER) {
                    multiChatList.remove(chat);
                } else {
                    while(index >= 0 && multiChatList.get(index).role != ChatRole.USER && multiChatList.get(index).role != ChatRole.SYSTEM) {
                        multiChatList.remove(index);
                        index--;
                    }
                }
            }
            if(tvContent == tvGptReply) { // 删除的是GPT正在回复的消息框，停止回复和TTS
                chatApiBuffer = "";
                pendingAssistantSegmentBuffer = "";
                currentReplyAnchorMessage = null;
                lastReplyRenderTime = 0;
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
                if(chat.attachments.size() > 0) { // 若含有附件则设置为选中的附件
                    selectedAttachments.clear();
                    selectedAttachments.addAll(chat.attachments); // 注意这是浅拷贝
                } else {
                    selectedAttachments.clear();
                }
                updateAttachmentButton(); // 更新附件按钮状态
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
                if(chat.attachments.size() > 0) { // 若含有附件则设置为选中的附件
                    selectedAttachments.clear();
                    selectedAttachments.addAll(chat.attachments); // 注意这是浅拷贝
                } else {
                    selectedAttachments.clear();
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
            ChatMessage chat = (ChatMessage) llOuter.getTag(); // 获取布局上绑定的聊天记录数据
            if(chat == null || chat.role != ChatRole.USER) {
                GlobalUtils.copyToClipboard(this, tvContent.getText().toString()); // 如果是助手回复则复制渲染后的内容
            } else {
                GlobalUtils.copyToClipboard(this, chat.contentText); // 如果是用户提问则复制原始提问内容
            }
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
            startNewChat();
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

        if(selectedAttachments.size() > 0) { // 若有选中的文件则添加到聊天记录数据中
            for (ChatMessage.Attachment attachment : selectedAttachments) {
                multiChatList.get(multiChatList.size() - 1).addAttachment(attachment);
            }
        }

        if(llChatList.getChildCount() > 0 && llChatList.getChildAt(0) instanceof TextView) { // 若有占位TextView则删除
            llChatList.removeViewAt(0);
        }

//        if(isMultiChat && llChatList.getChildCount() > 0) { // 连续对话模式下，将第一条提问改写为添加模板后的内容（历史遗留，忘记为什么这么写了，先留着）
//            LinearLayout llFirst = (LinearLayout) llChatList.getChildAt(0);
//            TextView tvFirst = (TextView) llFirst.getChildAt(1);
//            ChatMessage firstChat = (ChatMessage) llFirst.getTag();
//            if(firstChat.role == ChatRole.USER) {
//                if (firstChat.contentImageBase64 != null && tvFirst.getText().toString().endsWith("\n ")) { // 若有附加图片则也要一并添加
//                    SpannableString oldText = (SpannableString) tvFirst.getText();
//                    ImageSpan imgSpan = oldText.getSpans(oldText.length() - 1, oldText.length(), ImageSpan.class)[0];
//                    SpannableString newText = new SpannableString(firstChat.contentText + "\n ");
//                    newText.setSpan(imgSpan, newText.length() - 1, newText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//                    tvFirst.setText(newText);
//                } else {
//                    tvFirst.setText(firstChat.contentText);
//                }
//            }
//        }

//        if(GlobalDataHolder.getOnlyLatestWebResult()) { // 若设置为仅保留最新网页数据，删除之前的所有网页数据
//            for (int i = 0; i < multiChatList.size(); i++) {
//                ChatMessage chatItem = multiChatList.get(i);
//                if (chatItem.role == ChatRole.FUNCTION) {
//                    multiChatList.remove(i);
//                    i--;
//                    if(i > 0 && multiChatList.get(i).role == ChatRole.ASSISTANT) { // 也要删除调用Function的Assistant记录
//                        multiChatList.remove(i);
//                        i--;
//                    }
//                }
//            }
//        }

        // 添加对话布局
        LinearLayout llInput = addChatView(ChatRole.USER, isMultiChat ? multiChatList.get(multiChatList.size() - 1).contentText : userInput, multiChatList.get(multiChatList.size() - 1).attachments);
        LinearLayout llReply = addChatView(ChatRole.ASSISTANT, getString(R.string.text_waiting_reply), null);

        llInput.setTag(multiChatList.get(multiChatList.size() - 1)); // 将对话数据绑定到布局上

        tvGptReply = (TextView) llReply.getChildAt(1);

        scrollChatAreaToBottom();

        chatApiBuffer = "";
        pendingAssistantSegmentBuffer = "";
        currentReplyAnchorMessage = null;
        lastReplyRenderTime = 0;
        ttsSentenceEndIndex = 0;
        if (BuildConfig.DEBUG && userInput.startsWith("#markdowndebug\n")) { // Markdown渲染测试
            markdownRenderer.render(tvGptReply, userInput.replace("#markdowndebug\n", ""));
        } else {
            chatApiClient.resetReasoningState();
            sendChatList();
            selectedAttachments.clear();
            btSend.setImageResource(R.drawable.cancel_btn);
            updateAttachmentButton(); // 更新附件按钮状态
        }
    }

    // 预处理并发送聊天列表给GPT
    void sendChatList() {
        MessageList chatList = new MessageList();
        for(ChatMessage message : multiChatList) { // 深拷贝聊天记录
            chatList.add(message.clone());
        }

        if(GlobalDataHolder.getOnlyLatestWebResult()) { // 若设置为仅保留最新网页数据，删除之前的所有网页数据
            boolean foundLastUserMessage = false;
            for (int i = chatList.size() - 1; i >= 0; i--) {
                ChatMessage message = chatList.get(i);
                if(message.role == ChatRole.USER) {
                    foundLastUserMessage = true;
                }
                if(foundLastUserMessage && message.role == ChatRole.FUNCTION && message.toolCalls.get(0).functionName.equals("get_html_text")) {
                    message.toolCalls.get(0).content = ""; // 清空网页数据
                }
            }
        }

        // 仅保留最后N条手机屏幕信息
        int foundLastWidgetMessageCount = 0;
        for(int i = chatList.size() - 1; i >= 0; i--) {
            ChatMessage message = chatList.get(i);
            if(message.role == ChatRole.FUNCTION) {
                String functionName = message.toolCalls.get(0).functionName;
                if(functionName.equals("get_widget_tree") || functionName.equals("widget_action")) {
                    foundLastWidgetMessageCount++;
                    if(foundLastWidgetMessageCount > 2) { // 只保留最后2条手机屏幕信息
                        message.toolCalls.get(0).content = "";
                    }
                }
            }
        }

        // 仅保留最近N个USER回合对应的完整消息链，避免assistant拆段后过早丢失上下文
        if(GlobalDataHolder.getGptMaxContextNum() > 0) {
            int foundUserTurnCount = 0;
            int firstKeptUserIndex = -1;
            for(int i = chatList.size() - 1; i >= 0; i--) {
                if(chatList.get(i).role == ChatRole.USER) {
                    foundUserTurnCount++;
                    if(foundUserTurnCount >= GlobalDataHolder.getGptMaxContextNum()) {
                        firstKeptUserIndex = i;
                        break;
                    }
                }
            }
            if(firstKeptUserIndex > 0) {
                for(int i = firstKeptUserIndex - 1; i >= 0; i--) {
                    if(chatList.get(i).role != ChatRole.SYSTEM) {
                        chatList.remove(i);
                    }
                }
            }
        }

        chatApiClient.sendPromptList(chatList); // 发送聊天列表给GPT
    }

    // 获取附件弹窗
    private PopupWindow getAttachmentPopupWindow() {
        LinearLayout.LayoutParams popupParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); // 删除按钮布局参数
        popupParams.setMargins(0, 0, 0, 0);

        LinearLayout.LayoutParams deleteIconParams = new LinearLayout.LayoutParams(dpToPx(30), dpToPx(30)); // 删除按钮布局参数
        deleteIconParams.setMargins(dpToPx(5), 0, dpToPx(5), 0);

        LinearLayout.LayoutParams filenameCardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(30)); // 文件名布局参数
        filenameCardParams.setMargins(dpToPx(5), 0, dpToPx(5), 0);

        LinearLayout.LayoutParams uploadIconParams = new LinearLayout.LayoutParams(dpToPx(30), dpToPx(30)); // 删除按钮布局参数
        uploadIconParams.setMargins(dpToPx(5), 0, dpToPx(5), 0);

        LinearLayout llPopup = new LinearLayout(this);
        llPopup.setOrientation(LinearLayout.VERTICAL);
        llPopup.setGravity(Gravity.LEFT);
        llPopup.setLayoutParams(popupParams);

        PopupWindow popupWindow = new PopupWindow(llPopup, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setOutsideTouchable(true);

        ScrollView svAttachment = new ScrollView(this);
        svAttachment.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        svAttachment.setVerticalScrollBarEnabled(false);
        LinearLayout llAttachmentList = new LinearLayout(this);
        llAttachmentList.setOrientation(LinearLayout.VERTICAL);
        llAttachmentList.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if(selectedAttachments.size() > 0) {
            for (ChatMessage.Attachment attachment : selectedAttachments) {
                Log.d("MainActivity", "getAttachmentPopupWindow name: " + attachment.name);
                LinearLayout llAttachment = new LinearLayout(this);
                llAttachment.setOrientation(LinearLayout.HORIZONTAL);
                llAttachment.setGravity(Gravity.START);
                llAttachment.setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5));
                llAttachment.setTag(attachment);

                CardView cvFilename = new CardView(this);
                cvFilename.setLayoutParams(filenameCardParams);
                cvFilename.setCardBackgroundColor(Color.WHITE);
                cvFilename.setRadius(dpToPx(5));
                cvFilename.setContentPadding(dpToPx(5), 0, dpToPx(5), 0);
                cvFilename.setElevation(0);

                TextView tvFilename = new TextView(this);
                tvFilename.setText(attachment.name);
                tvFilename.setTextColor(Color.BLACK);
                tvFilename.setTextSize(14);
                tvFilename.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                tvFilename.setSingleLine(true);
                tvFilename.setGravity(Gravity.CENTER);

                cvFilename.addView(tvFilename);

                cvFilename.setOnClickListener(view -> { // 点击文件名进行预览
                    if (attachment.type == ChatMessage.Attachment.Type.IMAGE) { // 图片类型的附件
                        Bitmap bitmap = base64ToBitmap(attachment.content);
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
                        View dialogView = inflater.inflate(R.layout.image_preview_dialog, null);
                        AlertDialog dialog = builder.create();
                        dialog.show();
                        dialog.getWindow().setContentView(dialogView);
                        ((ImageView) dialogView.findViewById(R.id.iv_image_preview)).setImageBitmap(bitmap);
                        ((TextView) dialogView.findViewById(R.id.tv_image_preview_size)).setText(String.format("%s x %s", bitmap.getWidth(), bitmap.getHeight()));
                        dialogView.findViewById(R.id.cv_image_preview_cancel).setOnClickListener(view1 -> dialog.dismiss());
                        dialogView.findViewById(R.id.cv_image_preview_del).setVisibility(View.GONE);
                        dialogView.findViewById(R.id.cv_image_preview_reselect).setVisibility(View.GONE);
                    } else { // 文本类型的附件
                        new ConfirmDialog(MainActivity.this)
                                .setTitle(attachment.name)
                                .setContent(attachment.content)
                                .setContentAlignment(View.TEXT_ALIGNMENT_TEXT_START)
                                .setOkButtonVisibility(View.GONE)
                                .show();
                    }
                });

                CardView cvDelete = new CardView(this);
                cvDelete.setLayoutParams(deleteIconParams);
                cvDelete.setForeground(getDrawable(R.drawable.close_btn));
                cvDelete.setCardBackgroundColor(Color.WHITE);
                cvDelete.setElevation(0);
                cvDelete.setOnClickListener(view -> { // 删除单个附件
                    llAttachmentList.removeView(llAttachment);
                    selectedAttachments.remove(attachment);
                    if (llAttachmentList.getChildCount() == 0) {
                        llAttachmentList.setVisibility(View.GONE);
                    }
                    updateAttachmentButton(); // 更新附件按钮状态
                });

                llAttachment.addView(cvDelete);
                llAttachment.addView(cvFilename);
                llAttachmentList.addView(llAttachment);
            }
        }

        svAttachment.addView(llAttachmentList);
        llPopup.addView(svAttachment);

        LinearLayout llUpload = new LinearLayout(this);
        llUpload.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0));
        llUpload.setOrientation(LinearLayout.HORIZONTAL);
        llUpload.setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5));
        llUpload.setGravity(Gravity.START);

        CardView cvTakePhoto = new CardView(this);
        cvTakePhoto.setLayoutParams(uploadIconParams);
        cvTakePhoto.setForeground(getDrawable(R.drawable.camera_btn));
        cvTakePhoto.setCardBackgroundColor(Color.WHITE);
        cvTakePhoto.setElevation(0);
        cvTakePhoto.setOnClickListener(view -> {
            popupWindow.dismiss();
            photoUri = FileProvider.getUriForFile(MainActivity.this, BuildConfig.APPLICATION_ID + ".provider", new File(getCacheDir(), "photo.jpg"));
            Intent intent=new Intent();
            intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            startActivityForResult(intent, 1);
        });

        CardView cvSelectPhoto = new CardView(this);
        cvSelectPhoto.setLayoutParams(uploadIconParams);
        cvSelectPhoto.setForeground(getDrawable(R.drawable.image_btn));
        cvSelectPhoto.setCardBackgroundColor(Color.WHITE);
        cvSelectPhoto.setElevation(0);
        cvSelectPhoto.setOnClickListener(view -> {
            popupWindow.dismiss();
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, 2);
        });

        CardView cvSelectDocument = new CardView(this);
        cvSelectDocument.setLayoutParams(uploadIconParams);
        cvSelectDocument.setForeground(getDrawable(R.drawable.file_btn));
        cvSelectDocument.setCardBackgroundColor(Color.WHITE);
        cvSelectDocument.setElevation(0);
        cvSelectDocument.setOnClickListener(view -> {
            popupWindow.dismiss();
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String mimeTypes[] = {"text/plain", "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            };
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, 4);
        });

        CardView cvDeleteAll = new CardView(this);
        cvDeleteAll.setLayoutParams(uploadIconParams);
        cvDeleteAll.setForeground(getDrawable(R.drawable.clear_btn));
        cvDeleteAll.setCardBackgroundColor(Color.WHITE);
        cvDeleteAll.setElevation(0);
        cvDeleteAll.setOnClickListener(view -> {
            llAttachmentList.removeAllViews();
            selectedAttachments.clear();
            updateAttachmentButton(); // 更新附件按钮状态
        });

        llUpload.addView(cvTakePhoto);
        llUpload.addView(cvSelectPhoto);
        llUpload.addView(cvSelectDocument);
        llUpload.addView(cvDeleteAll);
        llPopup.addView(llUpload);

        llPopup.setOnClickListener(view -> { // 点击空白处关闭弹出窗口
            if (popupWindow.isShowing()) {
                popupWindow.dismiss();
            }
        });

        llAttachmentList.setOnClickListener(view -> { // 点击空白处关闭弹出窗口
            if (popupWindow.isShowing()) {
                popupWindow.dismiss();
            }
        });

        return popupWindow;
    }

    // 根据URI添加附件
    private void addAttachment(Uri uri) {
        try {
            Log.d("MainActivity", "addAttachment: uri=" + uri);
            String mimeType = getContentResolver().getType(uri);
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            String filename = "file";
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                String displayName = cursor.getString(nameIndex);
                if(displayName != null) {
                    filename = displayName;
                }
                cursor.close();
            }
            if (mimeType.startsWith("image/")) {
                Bitmap bitmap = (Bitmap) BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
                if (GlobalDataHolder.getLimitVisionSize()) {
                    if (bitmap.getWidth() < bitmap.getHeight())
                        bitmap = resizeBitmap(bitmap, 512, 2048);
                    else
                        bitmap = resizeBitmap(bitmap, 2048, 512);
                } else {
                    bitmap = resizeBitmap(bitmap, 2048, 2048);
                }
                selectedAttachments.add(ChatMessage.Attachment.createNew(ChatMessage.Attachment.Type.IMAGE, filename, bitmapToBase64(bitmap), false));
                Log.d("MainActivity", "addImageAttachment: fileName=" + filename + " size=" + bitmap.getWidth() + "x" + bitmap.getHeight());
                updateAttachmentButton(); // 更新附件按钮状态
            } else {
                String finalFilename = filename;
                new DocumentParser(this).parseDocument(uri, mimeType, new DocumentParser.ParseCallback() {
                    @Override
                    public void onParseSuccess(String text) {
                        selectedAttachments.add(ChatMessage.Attachment.createNew(ChatMessage.Attachment.Type.TEXT, finalFilename, text, false));
                        Log.d("MainActivity", "addAttachment: fileName=" + finalFilename + " size=" + text.length());
                        runOnUiThread(() -> {
                            updateAttachmentButton(); // 更新附件按钮状态
                        });
                    }

                    @Override
                    public void onParseError(Exception e) {
                        runOnUiThread(() -> {
                            Log.e("MainActivity", "addAttachment parse error: " + finalFilename);
                            GlobalUtils.showToast(MainActivity.this, getString(R.string.toast_unsupported_file) + finalFilename, false);
                        });
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 更新附件按钮
    private void updateAttachmentButton() {
        TextView tvNumber = findViewById(R.id.tv_attachment_num);
        if(selectedAttachments.size() > 0) {
            btAttachment.setImageResource(R.drawable.attachment_btn_enabled);
            tvNumber.setVisibility(View.VISIBLE);
            String numberText = selectedAttachments.size() < 100 ? String.valueOf(selectedAttachments.size()) : "99+";
            tvNumber.setText(numberText);
        } else {
            btAttachment.setImageResource(R.drawable.attachment_btn);
            tvNumber.setText("0");
            tvNumber.setVisibility(View.GONE);
        }
    }

    // 将聊天记录恢复到界面上
    private void reloadConversation(Conversation conversation) {
        startNewChat(); // 新建一个聊天

        currentConversation = conversation;
        multiChatList = conversation.messages;

        llChatList.removeViewAt(0); // 删除占位TextView
        for(int i = 0; i < multiChatList.size(); i++) {
            ChatMessage chatItem = multiChatList.get(i);
            if(chatItem.role == ChatRole.USER) {
                LinearLayout llChatItem = addChatView(chatItem.role, chatItem.contentText, chatItem.attachments);
                llChatItem.setTag(chatItem);
                continue;
            }
            if(chatItem.role == ChatRole.SYSTEM) {
                continue;
            }

            int roundEnd = i;
            boolean hasLegacyReferenceBlock = false;
            ChatMessage assistantAnchor = null;
            while(roundEnd < multiChatList.size()
                    && multiChatList.get(roundEnd).role != ChatRole.USER
                    && multiChatList.get(roundEnd).role != ChatRole.SYSTEM) {
                ChatMessage roundMessage = multiChatList.get(roundEnd);
                assistantAnchor = roundMessage;
                if(roundMessage.role == ChatRole.ASSISTANT && roundMessage.toolCalls.size() == 0
                        && roundMessage.contentText != null
                        && (roundMessage.contentText.contains(getString(R.string.text_ref_web_prefix))
                        || roundMessage.contentText.contains("Ref web: ")
                        || roundMessage.contentText.contains("参考网页: "))) {
                    hasLegacyReferenceBlock = true;
                }
                roundEnd++;
            }

            StringBuilder assistantText = new StringBuilder();
            for(int j = i; j < roundEnd; j++) {
                ChatMessage roundMessage = multiChatList.get(j);
                if(roundMessage.role == ChatRole.ASSISTANT) {
                    if(roundMessage.toolCalls.size() == 0) {
                        if(roundMessage.contentText != null) {
                            assistantText.append(roundMessage.contentText);
                        }
                    } else {
                        for(ChatMessage.ToolCall toolCall : roundMessage.toolCalls) {
                            if(assistantText.length() > 0 && !assistantText.toString().endsWith("\n")) {
                                assistantText.append("\n\n");
                            }
                            if(!hasLegacyReferenceBlock && toolCall.functionName.equals("get_html_text")) {
                                try {
                                    String url = new JSONObject(toolCall.arguments).getStr("url");
                                    assistantText.append(formatWebReferenceNotice(url)).append("\n\n");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } else {
								assistantText.append(String.format(getString(R.string.text_tool_call_notice_format), toolCall.functionName)).append("\n\n");
							}
                        }
                    }
                }
            }
            if(assistantText.length() > 0) {
                LinearLayout llChatItem = addChatView(ChatRole.ASSISTANT, assistantText.toString(), null);
                llChatItem.setTag(assistantAnchor);
            }
            i = roundEnd - 1;
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
        chatApiBuffer = "";
        pendingAssistantSegmentBuffer = "";
        currentReplyAnchorMessage = null;
        lastReplyRenderTime = 0;

        TextView tv = new TextView(this); // 清空列表后添加一个占位TextView
        tv.setTextColor(Color.parseColor("#000000"));
        tv.setTextSize(16);
        tv.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
        tv.setText(R.string.default_greeting);
        tvGptReply = tv;
        llChatList.addView(tv);
    }

    // 处理启动Intent
    private void handleShareIntent(Intent intent) {
        if(intent != null){
            String action = intent.getAction();
            if(Intent.ACTION_PROCESS_TEXT.equals(action)) { // 全局上下文菜单
                String text = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT);
                if(text != null){
                    etUserInput.setText(text);
                }
            } else if(Intent.ACTION_SEND.equals(action)) { // 分享单个文件
                String type = intent.getType();
                if(type != null && type.startsWith("image/")) {
                    Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM); // 获取图片Uri
                    if (imageUri != null) {
                        addAttachment(imageUri); // 添加图片到附件列表
                    }
                    if (!GlobalUtils.checkVisionSupport(GlobalDataHolder.getGptModel()))
                        Toast.makeText(this, R.string.toast_use_vision_model, Toast.LENGTH_LONG).show();
                } else if(type != null && type.equals("text/plain") && intent.getStringExtra(Intent.EXTRA_TEXT) != null) { // 分享文本
                    String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                    if(text != null) {
                        etUserInput.setText(text);
                    }
                } else { // 分享文档
                    Uri documentUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if(documentUri != null) {
                        addAttachment(documentUri); // 添加文档到附件列表
                    }
                }
            } else if(Intent.ACTION_VIEW.equals(action)) { // 打开文件
                Uri documentUri = intent.getData();
                if(documentUri != null) {
                    addAttachment(documentUri); // 添加文档到附件列表
                }
            } else if(Intent.ACTION_SEND_MULTIPLE.equals(action)) { // 分享多个文件
                ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                for(Uri uri : uris) {
                    if(uri != null) {
                        addAttachment(uri); // 添加到附件列表
                    }
                }
            }
        }
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

    // 将Bitmap转换为Base64编码
    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] bytes = baos.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }

    // 根据当前的多窗口模式更新UI
    void updateForMultiWindowMode() {
        if(isInMultiWindowMode()) { // 进入分屏/小窗，设置为全屏显示主界面
            findViewById(R.id.ll_main).setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else { // 退出分屏/小窗，显示主界面在屏幕下方
            findViewById(R.id.ll_main).setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        updateForMultiWindowMode();
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
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
        persistCurrentConversation(false);
        chatManager.removeEmptyConversations();
        chatManager.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
        super.onBackPressed();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.translate_up_in, R.anim.translate_down_out);
    }
}
