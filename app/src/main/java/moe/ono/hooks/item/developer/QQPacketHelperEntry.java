package moe.ono.hooks.item.developer;

import static moe.ono.constants.Constants.MethodCacheKey_ChatPanelBtn;
import static moe.ono.dexkit.TargetManager.requireMethod;
import static moe.ono.util.SyncUtils.runOnUiThread;

import android.annotation.SuppressLint;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;

import moe.ono.config.ConfigManager;
import moe.ono.constants.Constants;
import moe.ono.hooks._base.BaseClickableFunctionHookItem;
import moe.ono.hooks._core.annotation.HookItem;
import moe.ono.creator.PacketHelperDialog;

@SuppressLint("DiscouragedApi")
@HookItem(
        path = "开发者选项/QQPacketHelper",
        description = "* 此功能极度危险，滥用可能会导致您的账号被冻结；为了您的人身安全，请勿发送恶意代码\n\n开启后需在聊天界面长按加号呼出 (可关闭)，或长按发送按钮呼出"
)
public class QQPacketHelperEntry extends BaseClickableFunctionHookItem {
    private void hook() {
        if (ConfigManager.dGetBooleanDefTrue(Constants.PrekCfgXXX + "QQPacketHelperHookPanel")) {
            Method method = requireMethod(MethodCacheKey_ChatPanelBtn);
            hookAfter(method, param -> {
                LinearLayout layout = (LinearLayout) param.thisObject;
                for (int i = 0; i < layout.getChildCount(); i++) {
                    View child = layout.getChildAt(i);
                    if (child instanceof ImageView && child.getContentDescription() != null) {
                        ImageView panelIcon = (ImageView) child;
                        if ("更多功能".contentEquals(panelIcon.getContentDescription())) {
                            panelIcon.setOnLongClickListener(view -> {
                                runOnUiThread(() -> PacketHelperDialog.createView(null, view.getContext(), ""));
                                return true;
                            });
                        }
                    }
                }
            });
        }
    }


    @Override
    public void entry(@NonNull ClassLoader classLoader) {
        hook();
    }

}