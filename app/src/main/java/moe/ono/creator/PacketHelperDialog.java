package moe.ono.creator;

import static moe.ono.bridge.ntapi.ChatTypeConstants.C2C;
import static moe.ono.bridge.ntapi.ChatTypeConstants.GROUP;
import static moe.ono.bridge.ntapi.RelationNTUinAndUidApi.getUinFromUid;
import static moe.ono.builder.MsgBuilder.nt_build_ark;
import static moe.ono.builder.MsgBuilder.nt_build_text;
import static moe.ono.common.CheckUtils.isJSON;
import static moe.ono.util.Utils.bytesToHex;
import static moe.ono.util.analytics.ActionReporter.reportVisitor;
import static moe.ono.util.Session.getContact;
import static moe.ono.util.Session.getCurrentChatType;
import static moe.ono.util.Session.getCurrentPeerID;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.util.XPopupUtils;
import com.tencent.qqnt.kernel.nativeinterface.MsgElement;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import moe.ono.R;
import moe.ono.bridge.Nt_kernel_bridge;
import moe.ono.bridge.kernelcompat.ContactCompat;
import moe.ono.hooks.base.api.QQMsgRespHandler;
import moe.ono.hooks.protocol.QPacketHelperKt;
import moe.ono.hooks.base.util.Toasts;
import moe.ono.util.AppRuntimeHelper;
import moe.ono.ui.CommonContextWrapper;
import moe.ono.util.Logger;
import moe.ono.util.SafUtils;
import moe.ono.util.SyncUtils;
import moe.ono.util.Utils;

@SuppressLint({"ResourceType", "StaticFieldLeak"})
public class PacketHelperDialog extends BottomPopupView {
    private final List<String> presetelems; // 存储预设元素的列表
    private final Map<String, String> elemContentMap; // 存储元素名称和内容的映射
    private final SharedPreferences sharedPreferences;
    private static EditText editText;
    public static EditText etUin;
    public static EditText etDesc;
    public static EditText etHint;
    public static EditText etNickname;
    private static String preContent;
    private static Dialog elem_dialog = null;
    private static View decorView;
    private boolean isUpdatingText = false;  // 标志位，用于防止死循环
    private final int MAX_JSON_LENGTH = 5000;
    private static final long HIGHLIGHT_DELAY = 0; // 延迟高亮时间（ms）
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable highlightRunnable;
    private final int previousLength = 0;
    public static String peer;
    public static int chatType;
    public static ContactCompat contactCompat;
    private static RadioGroup mRgSendType;
    private static Boolean isSendByLongmsg = false;
    private static String originalPbContent = "";
    public static RadioGroup mRgSendBy;
    public static CheckBox mRbXmlForward;



    public PacketHelperDialog(@NonNull Context context) {
        super(context);
        sharedPreferences = context.getSharedPreferences("OvO_Presetelems", Context.MODE_PRIVATE);
        presetelems = loadPresetElem();
        elemContentMap = loadElemContentMap();
    }

    public static void createView(Activity activity, Context context, String content) {
        preContent = content;
        Context fixContext = CommonContextWrapper.createAppCompatContext(context);
        XPopup.Builder NewPop = new XPopup.Builder(fixContext).moveUpToKeyboard(true).isDestroyOnDismiss(true);
        NewPop.maxHeight((int) (XPopupUtils.getScreenHeight(context) * .7f));
        NewPop.popupHeight((int) (XPopupUtils.getScreenHeight(context) * .63f));

        if (activity != null){
            decorView = activity.getWindow().getDecorView();
            animateBlurEffect(decorView);
        }

        reportVisitor(AppRuntimeHelper.getAccount(), "CreateView-ElementSender");


        BasePopupView popupView = NewPop.asCustom(new PacketHelperDialog(fixContext));
        popupView.show();
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate() {
        super.onCreate();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            peer = getCurrentPeerID();
            chatType = Objects.requireNonNull(getContact()).getChatType();
            contactCompat = getContact();

            etUin = findViewById(R.id.uin);
            etDesc = findViewById(R.id.desc);
            etHint = findViewById(R.id.hint);
            etNickname = findViewById(R.id.nickname);

            mRgSendType = findViewById(R.id.rg_send_type);
            mRgSendBy = findViewById(R.id.rg_send_by);

            editText = findViewById(R.id.content);
            TextView tvTarget = findViewById(R.id.tv_target);
            Button btnSend = findViewById(R.id.btn_send);
            Button btnCustom = findViewById(R.id.btn_custom);
            Button btnFormat = findViewById(R.id.btn_format);
            Button btnFullWindows = findViewById(R.id.btn_full_windows);

            LinearLayout forwardConfig = findViewById(R.id.forward_config);
            forwardConfig.setVisibility(GONE);

            editText.clearFocus();
            editText.setVisibility(VISIBLE);
            editText.setText(preContent);

            mRgSendBy.setVisibility(GONE);

            etHint.setVisibility(VISIBLE);
            etDesc.setVisibility(VISIBLE);
            etUin.setVisibility(VISIBLE);
            etNickname.setVisibility(VISIBLE);

            etUin.setText(AppRuntimeHelper.getAccount());

            mRbXmlForward = findViewById(R.id.xml_forward);

            try {
                mRgSendType.check(R.id.rb_element);
                editText.setHint("Raw(array)...");
                mRgSendBy.setVisibility(VISIBLE);
                JSONObject jsonObject = new JSONObject(preContent);
                try {
                    jsonObject.get("app");
                    mRgSendType.check(R.id.rb_ark);
                    editText.setHint("Json...");
                    mRgSendBy.setVisibility(GONE);
                } catch (JSONException ignored) {}
            } catch (Exception e) {
                if (!Objects.equals(preContent, "")){
                    try {
                        new JSONArray(preContent);
                    } catch (JSONException ex) {
                        try {
                            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                            factory.setNamespaceAware(true);
                            XmlPullParser parser = factory.newPullParser();
                            parser.setInput(new java.io.StringReader(preContent));
                            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                            }
                            editText.setHint("Xml...");
                            mRgSendType.check(R.id.rb_xml);
                            mRgSendBy.setVisibility(GONE);
                        } catch (Exception er) {
                            editText.setHint("纯文本...");
                            mRgSendType.check(R.id.rb_text);
                            mRgSendBy.setVisibility(GONE);
                        }
                    }
                }
            }

            int chat_type = getCurrentChatType();

            if (chat_type == 1) {
                tvTarget.setText("当前会话: " + peer + " | " + "好友");
            } else if (chat_type == 2) {
                tvTarget.setText("当前会话: " + peer + " | " + "群聊");
            } else {
                tvTarget.setText("当前会话: " + peer + " | " + "未知");
            }

            btnCustom.setOnClickListener(v -> showCustomElemDialog());

            btnCustom.setOnLongClickListener(v -> {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(v.getContext());
                builder.setTitle("备份与恢复")
                        .setItems(new CharSequence[]{"导入", "导出"}, (dialog, which) -> {
                            if (which == 0) {
                                importPresetElemsFromFile();
                            } else if (which == 1) {
                                exportPresetElemsToFile();
                            }
                        });
                builder.create().show();
                return true;
            });


            final RadioButton[] rb = {findViewById(mRgSendType.getCheckedRadioButtonId())};


            mRgSendType.setOnCheckedChangeListener((group, checkedId) -> {
                rb[0] = findViewById(mRgSendType.getCheckedRadioButtonId());
                String send_type = rb[0].getText().toString();
                switch (send_type){
                    case "element":
                        editText.setHint("Raw(array)...");
                        mRgSendBy.setVisibility(VISIBLE);
                        break;
                    case "ark":
                        editText.setHint("Json...");
                        mRgSendBy.setVisibility(GONE);
                        forwardConfig.setVisibility(GONE);
                        break;
                    case "xml":
                        editText.setHint("Xml...");
                        mRgSendBy.setVisibility(GONE);
                        forwardConfig.setVisibility(GONE);
                        break;
                    case "text":
                        editText.setHint("纯文本...");
                        mRgSendBy.setVisibility(GONE);
                        forwardConfig.setVisibility(GONE);
                        break;
                }
            });

            mRgSendBy.setOnCheckedChangeListener((group, checkedId) -> {
                if (mRgSendBy.getCheckedRadioButtonId() == R.id.rb_send_by_forwarding) {
                    forwardConfig.setVisibility(VISIBLE);
                } else {
                    forwardConfig.setVisibility(GONE);
                }

            });

            btnSend.setOnClickListener(v -> {
                reportVisitor(AppRuntimeHelper.getAccount(), "ElementSender-Send-Packet");
                String text = editText.getText().toString();
                String send_type = rb[0].getText().toString();
                ContactCompat contactCompat = getContact();

                if (text.isEmpty()){
                    Toasts.info(getContext(), "你什么都没输入呢");
                    return;
                }

                // ark
                if (send_type.equals("ark")) {
                    try {
                        send_ark_msg(text, contactCompat);
                    } catch (JSONException e) {
                        Toasts.error(getContext(), "JSON语法错误");
                    }

                    return;
                } else if (send_type.equals("text")){
                    send_text_msg(text, contactCompat);
                    return;
                } else if (send_type.equals("xml")) {
                    try {
                        String hex = Utils.bytesToHex(QQMsgRespHandler.Companion.compressData(text));
                        String pb = "{\n" +
                                "    \"12\": {\n" +
                                "        \"1\": \"hex->"+hex+"\",\n" +
                                "        \"2\": 35\n" +
                                "    }\n" +
                                "}";
                        setContentForLongmsg(pb);
                    } catch (Exception e) {
                        Toasts.error(getContext(), "发生错误");
                        Logger.e(e);
                    }
                    return;
                }

                // protobuf
                try {
                    if (!isJSON(text)){
                        Toasts.info(getContext(), "无效的代码");
                        return;
                    }

                    int rbSendBy = mRgSendBy.getCheckedRadioButtonId();
                    if (chat_type != 1 && chat_type != 2) {
                        Toasts.error(getContext(), "失败");
                        return;
                    }

                    if (rbSendBy == R.id.rb_send_by_directly) {
                        QPacketHelperKt.sendMessage(text, peer, chatType == GROUP, send_type, isSendByLongmsg, originalPbContent);
                        originalPbContent = "";
                        isSendByLongmsg = false;
                    } else if (rbSendBy == R.id.rb_send_by_longmsg) {
                        String data = "{\n" +
                                "  \"2\": {\n" +
                                "    \"1\": \"MultiMsg\",\n" +
                                "    \"2\": {\n" +
                                "      \"1\": [\n" +
                                "        {\n" +
                                "          \"3\": {\n" +
                                "            \"1\": {\n" +
                                "              \"2\": " + text +
                                "            }\n" +
                                "          }\n" +
                                "        }\n" +
                                "      ]\n" +
                                "    }\n" +
                                "  }\n" +
                                "}".trim();
                        byte[] protoBytes = QPacketHelperKt.buildMessage(data);
                        byte[] compressedData = compressData(protoBytes);

                        long target = Long.parseLong(chatType == GROUP ? peer : getUinFromUid(peer));

                        String json = "{\n" +
                                "  \"2\": {\n" +
                                "    \"1\": " + (chatType == C2C ? 1 : 3) + ",\n" +
                                "    \"2\": {\n" +
                                "      \"2\": "+ target +"\n" +
                                "    },\n" +
                                "    \"4\": \"hex->"+bytesToHex(compressedData)+"\"\n" +
                                "  },\n" +
                                "  \"15\": {\n" +
                                "    \"1\": 4,\n" +
                                "    \"2\": 2,\n" +
                                "    \"3\": 9,\n" +
                                "    \"4\": 0\n" +
                                "  }\n" +
                                "}".trim();

                        Logger.d("ElementSender-send-by-longmsg", json);
                        QPacketHelperKt.sendPacket("trpc.group.long_msg_interface.MsgService.SsoSendLongMsg", json);
                    } else if (rbSendBy == R.id.rb_send_by_forwarding){
                        String data = "{\"2\": {\n" +
                                "  \"1\": \"MultiMsg\",\n" +
                                "  \"2\": {\n" +
                                "    \"1\": [\n" +
                                "      {\n" +
                                "        \"1\": {\n" +
                                "          \"1\": "+etUin.getText()+",\n" +
                                "          \"5\": {},\n" +
                                "          \"6\": {},\n" +
                                "          \"7\": {},\n" +
                                "          \"8\": {\n" +
                                "            \"1\": 10001,\n" +
                                "            \"4\": \"@ouom_pub\",\n" +
                                "            \"5\": 2\n" +
                                "          }\n" +
                                "        },\n" +
                                "        \"2\": {\n" +
                                "          \"1\": 82,\n" +
                                "          \"2\": {},\n" +
                                "          \"3\": {},\n" +
                                "          \"4\": "+ThreadLocalRandom.current().nextInt(0, 10000000)+",\n" +
                                "          \"5\": "+ThreadLocalRandom.current().nextInt(0, 100000)+",\n" +
                                "          \"6\": "+ThreadLocalRandom.current().nextInt(0, 10000000)+",\n" +
                                "          \"7\": 1,\n" +
                                "          \"8\": 0,\n" +
                                "          \"9\": 0,\n" +
                                "          \"15\": {\n" +
                                "            \"1\": 0,\n" +
                                "            \"2\": 0,\n" +
                                "            \"3\": 0,\n" +
                                "            \"4\": \"\",\n" +
                                "            \"5\": \"\"\n" +
                                "          }\n" +
                                "        },\n" +
                                "        \"3\": {\n" +
                                "          \"1\": {\n" +
                                "            \"2\": " + text +
                                "          }\n" +
                                "        }\n" +
                                "      }\n" +
                                "    ]\n" +
                                "  }\n" +
                                "}}\n".trim();

                        Logger.d("data", data);
                        byte[] protoBytes = QPacketHelperKt.buildMessage(data);
                        byte[] compressedData = compressData(protoBytes);

                        long target = Long.parseLong(chatType == GROUP ? peer : getUinFromUid(peer));

                        String json = "{\n" +
                                "  \"2\": {\n" +
                                "    \"1\": " + (chatType == C2C ? 1 : 3) + ",\n" +
                                "    \"2\": {\n" +
                                "      \"2\": "+ target +"\n" +
                                "    },\n" +
                                "    \"4\": \"hex->"+bytesToHex(compressedData)+"\"\n" +
                                "  },\n" +
                                "  \"15\": {\n" +
                                "    \"1\": 4,\n" +
                                "    \"2\": 2,\n" +
                                "    \"3\": 9,\n" +
                                "    \"4\": 0\n" +
                                "  }\n" +
                                "}".trim();

                        Logger.d("ElementSender-send-by-forward", json);
                        QPacketHelperKt.sendPacket("trpc.group.long_msg_interface.MsgService.SsoSendLongMsg", json);
                    }
                    Toasts.success(getContext(), "请求成功");

                    if (rbSendBy == R.id.rb_send_by_directly) dialog.dismiss();
                    fadeOutAndClearBlur(decorView);
                } catch (Exception e) {
                    Logger.e("未适配的消息结构", e);
                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
                    builder.setTitle("未适配的消息结构，请联系开发者");
                    builder.setMessage(e.toString());
                    builder.show();
                    Toasts.info(getContext(), "未适配的消息结构，请联系开发者");
                }

            });

            btnSend.setOnLongClickListener(v -> {
                String send_type = rb[0].getText().toString();
                String text = editText.getText().toString();
                ContactCompat contactCompat = getContact();
                try {
                    if (chat_type == 1) {
                        showRepeatSendDialog(text, peer, false, send_type, contactCompat);
                    } else if (chat_type == 2) {
                        showRepeatSendDialog(text, peer, true, send_type, contactCompat);
                    } else {
                        Toasts.error(getContext(), "失败");
                        return true;
                    }
                } catch (Exception e) {
                    Logger.e("未适配的消息结构", e);
                    Toasts.info(getContext(), "未适配的消息结构，请联系开发者");
                }
                return true;
            });

            btnFullWindows.setOnClickListener(v -> showFullScreenJsonEditor());

            btnFormat.setOnClickListener(v -> {
                try {
                    String text = editText.getText().toString();
                    JsonElement jsonElement = JsonParser.parseString(text);
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    String formattedText = gson.toJson(jsonElement);
                    if (!formattedText.equals("null")){
                        editText.setText(formattedText);
                    }
                } catch (Exception e) {
                    Toasts.error(getContext(), "JSON 不合法");
                }

            });




        }, 100);


    }


    @SuppressLint("ClickableViewAccessibility")
    private void showFullScreenJsonEditor() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Editor");

        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_pb_editor, null);
        TextView tvLineNumbers = view.findViewById(R.id.tv_line_numbers);
        EditText jsonEditor = view.findViewById(R.id.json_editor);
        Button btnEditorFormat = view.findViewById(R.id.btn_editor_format);
        final boolean[] flag = {false};

        jsonEditor.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (jsonEditor.getScrollY() != tvLineNumbers.getScrollY()) {
                tvLineNumbers.scrollTo(0, jsonEditor.getScrollY());
            }
        });

        jsonEditor.setOnTouchListener((v, event) -> {
            tvLineNumbers.dispatchTouchEvent(event);
            return false;
        });


        preContent = editText.getText().toString();
        jsonEditor.setText(preContent);

        btnEditorFormat.setOnClickListener(v -> {
            try {
                String text = jsonEditor.getText().toString();
                JsonElement jsonElement = JsonParser.parseString(text);
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String formattedText = gson.toJson(jsonElement);
                if (formattedText.equals("null")){
                    return;
                }
                if (preContent.length() > MAX_JSON_LENGTH) {
                    jsonEditor.setText(formattedText);  // 这里不改，因为直接设置大文本会更方便
                    return;
                }

                Editable editable = jsonEditor.getText();
                editable.replace(0, editable.length(), highlightJson(formattedText));
            } catch (Exception e) {
                Toasts.error(getContext(), "JSON 格式不合法");
            }
        });

        jsonEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateLineNumbers(tvLineNumbers, jsonEditor);
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (flag[0]) {
                    return;
                }

                if (!isUpdatingText) {
                    isUpdatingText = true;
                    int cursorPosition = jsonEditor.getSelectionStart();

                    Editable editable = jsonEditor.getText();
                    editable.replace(0, editable.length(), highlightJson(s.toString()));

                    jsonEditor.setSelection(cursorPosition);
                    isUpdatingText = false;
                }
            }
        });

        jsonEditor.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            updateLineNumbers(tvLineNumbers, jsonEditor);
            if (preContent.length() > MAX_JSON_LENGTH) {
                btnEditorFormat.setText("字符超限，不再支持高亮，点击格式化");
                flag[0] = true;
                return;
            }

            Editable editable = jsonEditor.getText();
            editable.replace(0, editable.length(), highlightJson(String.valueOf(jsonEditor.getText())));
        });

        builder.setView(view);

        builder.setPositiveButton("保存", (dialog, which) -> {
            preContent = jsonEditor.getText().toString();
            editText.setText(preContent);
        });

        builder.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());

        builder.setCancelable(true);

        AlertDialog dialog = builder.create();
        Objects.requireNonNull(dialog.getWindow()).setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
        );
        dialog.show();
    }



    private void updateLineNumbers(TextView tvLineNumbers, EditText jsonEditor) {
        int lineCount = jsonEditor.getLineCount();
        StringBuilder lineNumbers = new StringBuilder();
        for (int i = 1; i <= lineCount; i++) {
            lineNumbers.append(i).append("\n");
        }
        tvLineNumbers.setText(lineNumbers.toString());
    }

    private Spannable highlightJson(String json) {
        // TODO: 优化高亮性能
        SpannableString spannable = new SpannableString(json);

        Pattern keyPattern = Pattern.compile("\"(.*?)\"\\s*:");
        Pattern valuePattern = Pattern.compile(":\\s*(\".*?\"|\\d+|true|false|null)");
        Pattern bracePattern = Pattern.compile("[{}\\[\\]]");
        Pattern stringPattern = Pattern.compile("\"(.*?)\""); // 匹配所有字符串（包括引号内的内容）

        Matcher matcher;

        // 高亮键
        matcher = keyPattern.matcher(json);
        while (matcher.find()) {
            spannable.setSpan(
                    new ForegroundColorSpan(Color.parseColor("#00C853")), // 绿色
                    matcher.start(1), matcher.end(1),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        // 高亮值
        matcher = valuePattern.matcher(json);
        while (matcher.find()) {
            spannable.setSpan(
                    new ForegroundColorSpan(getContext().getColor(R.color.firstTextColor)), // 从资源中获取颜色
                    matcher.start(1), matcher.end(1),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        // 高亮大括号和中括号
        matcher = bracePattern.matcher(json);
        while (matcher.find()) {
            spannable.setSpan(
                    new ForegroundColorSpan(getContext().getColor(R.color.theme_color_gol)), // 从资源中获取颜色
                    matcher.start(), matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        // 高亮字符串
        matcher = stringPattern.matcher(json);
        while (matcher.find()) {
            spannable.setSpan(
                    new ForegroundColorSpan(Color.parseColor("#66BB6A")), // 柔和绿色
                    matcher.start(0), matcher.start(0) + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            spannable.setSpan(
                    new ForegroundColorSpan(Color.parseColor("#66BB6A")), // 柔和绿色
                    matcher.end() - 1, matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            // 高亮字符串内部内容
            spannable.setSpan(
                    new ForegroundColorSpan(Color.parseColor("#00C853")), // 绿色
                    matcher.start(1), matcher.end(1),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        return spannable;
    }


    public static void setContent(String content, Boolean longmsg) {
        SyncUtils.runOnUiThread(() -> {
            try {
                if (longmsg) {
                    isSendByLongmsg = true;
                    originalPbContent = String.valueOf(editText.getText());
                }
                editText.setText(content);
                mRgSendType.check(R.id.rb_element);
                mRgSendBy.check(R.id.rb_send_by_directly);
            } catch (Exception e) {
                Logger.e(e);
            }
        });


    }

    public static void setContentForLongmsg(String content) {
        SyncUtils.runOnUiThread(() -> {
            try {
                editText.setText(content);
                mRgSendType.check(R.id.rb_element);
                mRgSendBy.check(R.id.rb_send_by_longmsg);
            } catch (Exception e) {
                Logger.e(e);
            }
        });


    }


    public static void send_ark_msg(String text, ContactCompat contactCompat) throws JSONException {
        new JSONObject(text);
        ArrayList<MsgElement> elements = new ArrayList<>();
        MsgElement msgElement = nt_build_ark(text);
        elements.add(msgElement);
        Nt_kernel_bridge.send_msg(contactCompat, elements);
    }

    public static void send_text_msg(String text, ContactCompat contactCompat) {
        ArrayList<MsgElement> elements = new ArrayList<>();
        MsgElement msgElement = nt_build_text(text);
        elements.add(msgElement);
        Nt_kernel_bridge.send_msg(contactCompat, elements);
    }

    @SuppressLint("SetTextI18n")
    private void showRepeatSendDialog(String content, String uid, boolean isGroupMsg, String type, ContactCompat contactCompat) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle("重复发包");

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        final EditText inputCount = new EditText(getContext());
        inputCount.setHint("次数");
        inputCount.setText("1");
        layout.addView(inputCount);

        final EditText inputInterval = new EditText(getContext());
        inputInterval.setHint("发包间隔 (毫秒)");
        inputInterval.setText("500");
        layout.addView(inputInterval);

        builder.setView(layout);

        final TextView warningText = new TextView(getContext());
        warningText.setText("警告：滥用此功能会导致封号");
        warningText.setTextSize(12);
        warningText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        layout.addView(warningText);

        builder.setPositiveButton("确定", (d, which) -> {
            String countStr = inputCount.getText().toString();
            String intervalStr = inputInterval.getText().toString();

            if (!countStr.isEmpty() && !intervalStr.isEmpty()) {
                int count = Integer.parseInt(countStr);
                long interval = Long.parseLong(intervalStr);

                repeatSendMessages(count, interval, content, uid, isGroupMsg, type, contactCompat);
                dialog.dismiss();
                fadeOutAndClearBlur(decorView);
            } else {
                Toasts.error(getContext(), "请填写所有字段");
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void repeatSendMessages(int count, long interval, String content, String uid, boolean isGroupMsg, String type, ContactCompat contactCompat) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            int currentCount = 0;

            @Override
            public void run() {
                if (currentCount < count) {
                    try {
                        if (type.equals("ark")) {
                            try {
                                send_ark_msg(content, contactCompat);
                            } catch (JSONException e) {
                                Toasts.error(getContext(), "JSON语法错误");
                            }
                        } else if (type.equals("text")){
                            send_text_msg(content, contactCompat);
                        } else {
                            QPacketHelperKt.sendMessage(content, uid, isGroupMsg, type, isSendByLongmsg, originalPbContent);
                        }

                        currentCount++;
                        new Handler(Looper.getMainLooper()).postDelayed(this, interval);
                    } catch (Exception e) {
                        Logger.e("发送消息失败", e);
                        Toasts.error(getContext(), "发送消息失败");
                    }
                } else {
                    Toasts.success(getContext(), "重复发包完成");
                }
            }
        }, interval);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void showCustomElemDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle("预设管理");

        RecyclerView recyclerView = new RecyclerView(getContext());
        CustomRecyclerViewAdapter adapter = new CustomRecyclerViewAdapter(presetelems, item -> {
            String content = elemContentMap.get(item);
            editText.setText(content);
            elem_dialog.dismiss();
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);


        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();

                // 更新数据源
                String movedItem = presetelems.remove(fromPosition);
                presetelems.add(toPosition, movedItem);
                adapter.notifyItemMoved(fromPosition, toPosition);
                savePresetElem(); // 保存顺序
                return true;
            }


            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                String deletedItem = presetelems.get(position);


                new MaterialAlertDialogBuilder(getContext())
                        .setTitle("确认删除")
                        .setMessage("你确定要删除元素 " + deletedItem + " 吗？")
                        .setPositiveButton("确定", (dialog, which) -> {
                            presetelems.remove(position);
                            elemContentMap.remove(deletedItem);
                            adapter.notifyItemRemoved(position);
                            deleteElement(deletedItem);
                            Toasts.success(getContext(), "已删除元素: " + deletedItem);
                        })
                        .setNegativeButton("取消", (dialog, which) -> {
                            adapter.notifyItemChanged(position); // 恢复滑动前的状态
                            dialog.cancel();
                        })
                        .show();
            }

        });
        itemTouchHelper.attachToRecyclerView(recyclerView);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(recyclerView);

        builder.setView(layout);

        builder.setNegativeButton("添加", (dialog, which) -> {
            MaterialAlertDialogBuilder addElemDialog = new MaterialAlertDialogBuilder(getContext());
            addElemDialog.setTitle("添加新元素");

            LinearLayout addLayout = new LinearLayout(getContext());
            addLayout.setOrientation(LinearLayout.VERTICAL);

            EditText inputElemName = new EditText(getContext());
            inputElemName.setHint("输入新元素名称");
            addLayout.addView(inputElemName);

            EditText inputElemContent = new EditText(getContext());
            inputElemContent.setHint("输入元素内容");
            addLayout.addView(inputElemContent);

            addElemDialog.setView(addLayout);
            addElemDialog.setPositiveButton("确定", (d, w) -> {
                String newElemName = inputElemName.getText().toString();
                String newElemContent = inputElemContent.getText().toString();

                if (presetelems.contains(newElemName)) {
                    Toasts.error(getContext(), "元素名称已存在，请使用其他名称");
                    return;
                }

                if (!newElemName.isEmpty() && !newElemContent.isEmpty()) {
                    elemContentMap.put(newElemName, newElemContent);
                    presetelems.add(newElemName);
                    savePresetElem();
                    saveElemContentMap();
                    adapter.notifyDataSetChanged(); // 刷新列表
                    Toasts.success(getContext(), "已保存元素: " + newElemName);
                } else {
                    Toasts.error(getContext(), "请填写所有字段");
                }
            });
            addElemDialog.setNegativeButton("取消", (dialog2, which2) -> dialog2.cancel());
            addElemDialog.show();
        });

        builder.setNeutralButton("取消", (dialog, which) -> dialog.cancel());
        elem_dialog = builder.show();
    }

    private void exportPresetElemsToFile() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String currentDate = dateFormat.format(new Date());
        String fileName = "preset_elems_" + currentDate + ".json";

        SafUtils.requestSaveFile(getContext())
                .setDefaultFileName(fileName)
                .setMimeType("application/json")
                .onResult(uri -> {
                    try (OutputStream outputStream = getContext().getContentResolver().openOutputStream(uri)) {
                        JSONArray jsonArray = new JSONArray();
                        for (String elem : presetelems) {
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("name", elem);
                            jsonObject.put("content", elemContentMap.get(elem));
                            jsonArray.put(jsonObject);
                        }
                        if (outputStream != null) {
                            outputStream.write(jsonArray.toString().getBytes());
                        } else {
                            Toasts.error(getContext(), "outputStream == null");
                            return;
                        }
                        Toasts.success(getContext(), "导出成功");
                        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
                        builder.setTitle("导出成功")
                                .setMessage(uri.toString())
                                .show();
                    } catch (IOException e) {
                        Logger.e(e);
                        Toasts.error(getContext(), "导出失败");
                    } catch (JSONException e) {
                        Logger.e(e);
                        Toasts.error(getContext(), "导出数据格式错误");
                    }
                })
                .onCancel(() -> Toasts.info(getContext(), "导出被取消"))
                .commit();
    }

    private void importPresetElemsFromFile() {
        SafUtils.requestOpenFile(getContext())
                .setMimeType("application/json")
                .onResult(this::loadPresetElemsFromFile)
                .onCancel(() -> Toasts.info(getContext(), "导入被取消"))
                .commit();
    }

    private void loadPresetElemsFromFile(Uri uri) {
        try (InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            StringBuilder jsonStringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonStringBuilder.append(line);
            }

            JSONArray jsonArray = new JSONArray(jsonStringBuilder.toString());
            presetelems.clear();
            elemContentMap.clear();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String name = jsonObject.getString("name");
                String content = jsonObject.getString("content");
                presetelems.add(name);
                elemContentMap.put(name, content);
            }

            savePresetElem();
            saveElemContentMap();
            Toasts.success(getContext(), "导入成功");
        } catch (Exception e) {
            Logger.e(e);
            Toasts.error(getContext(), "导入失败");
        }
    }




    private void savePresetElem() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("elem_count", presetelems.size());
        for (int i = 0; i < presetelems.size(); i++) {
            editor.putString("OvO_elem_" + i, presetelems.get(i));
        }
        editor.apply();
    }

    private void saveElemContentMap() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        for (Map.Entry<String, String> entry : elemContentMap.entrySet()) {
            editor.putString("OvO_content_" + entry.getKey(), entry.getValue());
        }
        editor.apply();
    }

    private List<String> loadPresetElem() {
        List<String> elems = new ArrayList<>();
        int count = sharedPreferences.getInt("elem_count", 0);
        for (int i = 0; i < count; i++) {
            String elem = sharedPreferences.getString("OvO_elem_" + i, null);
            if (elem != null) {
                elems.add(elem);
            }
        }
        return elems;
    }

    private Map<String, String> loadElemContentMap() {
        Map<String, String> map = new HashMap<>();
        for (String elem : presetelems) {
            String content = sharedPreferences.getString("OvO_content_" + elem, null);
            if (content != null) {
                map.put(elem, content);
            }
        }
        return map;
    }

    private void deleteElement(String element) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove("OvO_content_" + element);
        editor.remove("OvO_elem_" + element);
        editor.apply();

        presetelems.remove(element);
        elemContentMap.remove(element);

        savePresetElem();
    }

    public static byte[] compressData(byte[] protoBytes) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(protoBytes);
        }
        return outputStream.toByteArray();
    }


    private static void animateBlurEffect(View decorView) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return;
            }

            ValueAnimator blurAnimator = ValueAnimator.ofFloat(1f, 25f);
            blurAnimator.setDuration(200);
            blurAnimator.setInterpolator(new AccelerateInterpolator());
            blurAnimator.addUpdateListener(animation -> {
                float blurRadius = (float) animation.getAnimatedValue();
                decorView.setRenderEffect(RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP));
            });
            blurAnimator.start();
        } catch (Exception ignored) {}

    }

    private static void fadeOutAndClearBlur(View decorView) {
        if (decorView == null){
            return;
        }
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return;
            }

            ValueAnimator fadeOutAnimator = ValueAnimator.ofFloat(25f, 1f);
            fadeOutAnimator.setDuration(100);
            fadeOutAnimator.setInterpolator(new DecelerateInterpolator());
            fadeOutAnimator.addUpdateListener(animation -> {
                float blurRadius = (float) animation.getAnimatedValue();
                decorView.setRenderEffect(RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP));
            });

            fadeOutAnimator.addListener(new AnimatorListenerAdapter() {
                @RequiresApi(api = Build.VERSION_CODES.S)
                @Override
                public void onAnimationEnd(Animator animation) {
                    decorView.setRenderEffect(null);
                }
            });

            fadeOutAnimator.start();
        } catch (Exception ignored) {}
    }


    @Override
    protected void beforeDismiss() {
        fadeOutAndClearBlur(decorView);
        super.beforeDismiss();
    }

    @Override
    protected void onDismiss() {
        fadeOutAndClearBlur(decorView);
        super.onDismiss();
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.element_sender_layout;
    }

    @Override
    public Handler getHandler() {
        return handler;
    }
}


