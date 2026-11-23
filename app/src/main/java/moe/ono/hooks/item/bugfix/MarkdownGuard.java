package moe.ono.hooks.item.bugfix;

import static moe.ono.constants.Constants.MethodCacheKey_MarkdownAIO;
import static moe.ono.util.Utils.replaceInvalidLinks;
import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import com.tencent.qqnt.kernel.nativeinterface.MarkdownElement;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import moe.ono.dexkit.TargetManager;
import moe.ono.hooks._base.BaseSwitchFunctionHookItem;
import moe.ono.hooks._core.annotation.HookItem;
import moe.ono.util.Logger;


@SuppressLint("DiscouragedApi")
@HookItem(path = "优化与修复/拦截异常 Markdown 消息", description = "劫持非官方机器人发送的 Markdown 消息，并阻止其中的图片资源加载")
public class MarkdownGuard extends BaseSwitchFunctionHookItem {
    private void fix() {
        Method targetMethod = TargetManager.requireMethod(MethodCacheKey_MarkdownAIO);
        if (targetMethod == null) return;
        try {
            Field markdownField = targetMethod.getParameterCount() == 0
                    ? findMarkdownField(targetMethod.getDeclaringClass()) : null;
            Field contentField = MarkdownElement.class.getDeclaredField("content");
            contentField.setAccessible(true);
            hookBefore(targetMethod, param -> {
                MarkdownElement markdownElement = markdownField == null
                        ? (MarkdownElement) param.args[0]
                        : (MarkdownElement) markdownField.get(param.thisObject);
                if (markdownElement == null || markdownElement.getContent() == null) return;
                String content = markdownElement.getContent();
                String[] replacedMessage = replaceInvalidLinks(content);
                Logger.d("replacedMessage\n" + replacedMessage[0]);
                String content_replaced = replacedMessage[0];
                if (Boolean.parseBoolean(replacedMessage[1])){
                    content_replaced = content_replaced.replace("`", "^");
                    content_replaced += "\n***\n# 以下消息来自ovo!\n\n- 提示: 此markdown不合法！已阻止资源加载\n- 原因：此消息内包含了一个或多个非官方的图片资源链接。";
                }
                // 新版在消息对象中持有元素，保留对象以免丢失附加属性
                contentField.set(markdownElement, content_replaced);
            });
        } catch (Exception e) {
            Logger.e("拦截异常 Markdown 消息","err:"+e);
        }
    }

    private static Field findMarkdownField(Class<?> clazz) throws NoSuchFieldException {
        Field result = null;
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getType() == MarkdownElement.class && !Modifier.isStatic(field.getModifiers())) {
                if (result != null) throw new NoSuchFieldException("Ambiguous MarkdownElement field");
                result = field;
            }
        }
        if (result == null) throw new NoSuchFieldException("MarkdownElement field not found");
        result.setAccessible(true);
        return result;
    }

    @Override
    public void entry(@NonNull ClassLoader classLoader) throws Throwable {
        fix();
    }
}
