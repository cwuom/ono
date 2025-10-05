package moe.ono.hooks.item.developer;

import static moe.ono.constants.Constants.CLAZZ_PANEL_ICON_LINEAR_LAYOUT;
import static moe.ono.util.SyncUtils.runOnUiThread;

import android.annotation.SuppressLint;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;

import moe.ono.config.ConfigManager;
import moe.ono.constants.Constants;
import moe.ono.hooks._base.BaseClickableFunctionHookItem;
import moe.ono.hooks._core.annotation.HookItem;
import moe.ono.creator.PacketHelperDialog;
import moe.ono.reflex.XMethod;
import moe.ono.util.Logger;

@SuppressLint("DiscouragedApi")
@HookItem(
        path = "开发者选项/QQPacketHelper",
        description = "* 此功能极度危险，滥用可能会导致您的账号被冻结；为了您的人身安全，请勿发送恶意代码\n\n开启后需在聊天界面长按加号呼出 (可关闭)，或长按发送按钮呼出"
)
public class QQPacketHelperEntry extends BaseClickableFunctionHookItem {
    private void hook() {
        try {
            if (ConfigManager.dGetBooleanDefTrue(Constants.PrekCfgXXX + "QQPacketHelperHookPanel")) {
                Method method = XMethod.clz(CLAZZ_PANEL_ICON_LINEAR_LAYOUT).ret(ImageView.class).ignoreParam().get();
                hookAfter(method, param -> {
                    ImageView imageView = (ImageView) param.getResult();
                    if ("更多功能".contentEquals(imageView.getContentDescription())){
                        imageView.setOnLongClickListener(view -> {
                            runOnUiThread(() -> PacketHelperDialog.createView(null, view.getContext(), ""));
                            return true;
                        });
                    }
                });
            }
        } catch (NoSuchMethodException e) {
            Logger.e(this.getItemName(), e);
        }
    }


    @Override
    public void entry(@NonNull ClassLoader classLoader) {
        hook();
    }

}