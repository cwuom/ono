package moe.ono.hooks.item.chat;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import moe.ono.hooks._base.BaseSwitchFunctionHookItem;
import moe.ono.hooks._core.annotation.HookItem;

@SuppressLint("DiscouragedApi")
@HookItem(path = "聊天与消息/获取频道卡片", description = "获取频道卡片\n* 需在 快捷菜单 中使用")
public class GetChannelArk extends BaseSwitchFunctionHookItem {

    @Override
    public void entry(@NonNull ClassLoader classLoader) throws Throwable {

    }
}