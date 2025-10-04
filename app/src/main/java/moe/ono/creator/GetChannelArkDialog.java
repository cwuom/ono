/**
 * License
 * 本文件及代码仅供 cwuom/ono 使用
 * 基于 cwuom/ono 开发的开源项目需保证文件中声明本信息
 * 禁止 私有项目、闭源项目和以收费形式二次分发的项目 使用
 */

package moe.ono.creator;

import static moe.ono.util.Session.getCurrentChatType;
import static moe.ono.util.Session.getCurrentPeerID;
import static moe.ono.util.analytics.ActionReporter.reportVisitor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.util.XPopupUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Base64;
import java.util.Objects;

import moe.ono.R;
import moe.ono.hooks.base.util.Toasts;
import moe.ono.hooks.item.developer.GetCookie;
import moe.ono.ui.CommonContextWrapper;
import moe.ono.util.AppRuntimeHelper;
import moe.ono.util.Logger;
import moe.ono.util.Session;
import moe.ono.util.SyncUtils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@SuppressLint("ResourceType")
public class GetChannelArkDialog extends BottomPopupView {
    private static BasePopupView popupView;

    public GetChannelArkDialog(@NonNull Context context) {
        super(context);
    }

    public static void createView(Context context) {
        Context fixContext = CommonContextWrapper.createAppCompatContext(context);
        XPopup.Builder NewPop = new XPopup.Builder(fixContext).moveUpToKeyboard(true).isDestroyOnDismiss(true);
        NewPop.maxHeight((int) (XPopupUtils.getScreenHeight(context) * .7f));
        NewPop.popupHeight((int) (XPopupUtils.getScreenHeight(context) * .63f));


        reportVisitor(AppRuntimeHelper.getAccount(), "CreateView-GetChannelArkDialog");

        popupView = NewPop.asCustom(new GetChannelArkDialog(fixContext));
        popupView.show();
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate() {
        super.onCreate();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Button btnSend = findViewById(R.id.btn_send);
            TextView tvTarget = findViewById(R.id.tv_target);

            EditText title = findViewById(R.id.ark_title);
            EditText desc = findViewById(R.id.ark_desc);
            EditText jump_url = findViewById(R.id.jump_url);
            EditText ark_preview_url = findViewById(R.id.ark_preview_url);
            EditText ark_tag_name = findViewById(R.id.ark_tag_name);
            EditText ark_tag_icon = findViewById(R.id.ark_tag_icon);
            EditText ark_prompt = findViewById(R.id.ark_prompt);

            title.setVisibility(VISIBLE);
            desc.setVisibility(VISIBLE);
            jump_url.setVisibility(VISIBLE);
            ark_preview_url.setVisibility(VISIBLE);
            ark_tag_name.setVisibility(VISIBLE);
            ark_tag_icon.setVisibility(VISIBLE);
            ark_prompt.setVisibility(VISIBLE);

            title.clearFocus();
            desc.clearFocus();
            jump_url.clearFocus();
            ark_preview_url.clearFocus();
            ark_tag_name.clearFocus();
            ark_tag_icon.clearFocus();
            ark_prompt.clearFocus();

            int chat_type = getCurrentChatType();
            if (chat_type == 1) {
                tvTarget.setText("当前会话: " + getCurrentPeerID() + " | " + "好友");
            } else if (chat_type == 2) {
                tvTarget.setText("当前会话: " + getCurrentPeerID() + " | " + "群聊");
            } else {
                tvTarget.setText("当前会话: " + getCurrentPeerID() + " | " + "未知");
            }

            btnSend.setOnClickListener(v -> {
                try {
                    String cookie = GetCookie.Companion.getCookie("qun.qq.com");
                    assert cookie != null;
                    String gtk = GetCookie.Companion.getBknByCookie(cookie);

                    OkHttpClient client = new OkHttpClient();
                    String json = "{\n" +
                            "  \"appId\": \"com.tencent.tuwen.lua\",\n" +
                            "  \"bizSrc\": \"guild.share\",\n" +
                            "  \"prompt\": \""+((ark_prompt.getText().toString().isEmpty()) ? title.getText().toString() : ark_prompt.getText().toString())+"\",\n" +
                            "  \"meta\": \"{\\\"news\\\":{\\\"title\\\":\\\""+title.getText().toString()+"\\\",\\\"desc\\\":\\\""+desc.getText().toString()+"\\\",\\\"tag\\\":\\\""+((ark_tag_name.getText().toString().isEmpty()) ? "QQ频道" : ark_tag_name.getText().toString())+"\\\",\\\"tagIcon\\\":\\\""+((ark_tag_icon.getText().toString().isEmpty()) ? "https://tianxuan.gtimg.cn/47329_bd95e16e/assets/guild-icon.png" : ark_tag_icon.getText().toString())+"\\\",\\\"preview\\\":\\\""+ark_preview_url.getText().toString()+"\\\",\\\"jumpUrl\\\":\\\""+jump_url.getText().toString()+"\\\"}}\"\n" +
                            "}";
                    MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
                    RequestBody body = RequestBody.create(json, mediaType);
                    Request request = new Request.Builder().url("https://qun.qq.com/qunng/http2rpc/gotrpc/auth/trpc.group_pro.guild_activity.Components/GetArkMsgWithSign?bkn=" + gtk)
                            .header("Host", "qun.qq.com")
                            .header("User-Agent", "24117RK2CC Build/AQ3A.240829.003; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/139.0.7258.143 Mobile Safari/537.36 V1_AND_SQ_9.1.35_8708_YYB_D QQ/9.1.35.22670 NetType/WIFI WebP/0.4.1 AppId/537265587 Pixel/1440 StatusBarHeight/138 SimpleUISwitch/0 QQTheme/1103 StudyMode/0 CurrentMode/0 CurrentFontScale/1.0 GlobalDensityScale/0.96 AllowLandscape/false InMagicWin/0")
                            .header("X-Request-With", "com.tencent.mobileqq")
                            .header("Cookie", cookie)
                            .header("x-oidb", "{\"uint32_service_type\":2,\"uint32_command\":\"0x9064\"}")
                            .header("Referer", "https://qun.qq.com/qunng/guild/tianxuan/p/53049_a53f0edz?traceTint=tianxuan_copy")
                            .post(body)
                            .build();

                    client.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            SyncUtils.runOnUiThread(() -> Toasts.error(v.getContext(), "网络错误"));
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) {
                            try {
                                if (!response.isSuccessful()) {
                                    SyncUtils.runOnUiThread(() -> Toasts.error(v.getContext(), "请求失败: " + response.code()));
                                    return;
                                }

                                assert response.body() != null;
                                String result = response.body().string();

                                SyncUtils.runOnUiThread(() -> {
                                    JSONObject json = null;
                                    try {
                                        json = new JSONObject(result);
                                    } catch (JSONException e) {
                                        Toasts.error(v.getContext(), "JSON 解析失败");
                                    }
                                    assert json != null;
                                    String base64 = Objects.requireNonNull(json.optJSONObject("data")).optString("signed_ark");
                                    byte[] decodedBytes = Base64.getDecoder().decode(base64);
                                    String ark = new String(decodedBytes);
                                    try {
                                        PacketHelperDialog.send_ark_msg(ark, Session.getContact());
                                    } catch (JSONException e) {
                                        Toasts.error(v.getContext(), "发送失败");
                                    }
                                });
                            } catch (Exception e) {
                                Logger.e(e);
                            }
                        }
                    });
                } catch (Exception e) {
                    Logger.e(e);
                }

                popupView.dismiss();
            });
        }, 100);


    }




    @Override
    protected void beforeDismiss() {
        super.beforeDismiss();
    }

    @Override
    protected void onDismiss() {
        super.onDismiss();
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.get_channel_ark;
    }
}
