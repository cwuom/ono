package moe.ono.hooks.dispatcher

import de.robv.android.xposed.XC_MethodHook
import moe.ono.hooks._base.ApiHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks.item.chat.MessageEncryptor
import moe.ono.hooks.item.chat.FakeFileRecall
import moe.ono.hooks.item.chat.StickerPanelEntry
import moe.ono.hooks.item.developer.QQMessageFetcher
import moe.ono.hooks.item.entertainment.ModifyTextMessage
import moe.ono.hooks.item.entertainment.RespondFace
import moe.ono.hooks.item.sigma.QQMessageTracker
import moe.ono.util.Logger
import java.lang.reflect.Modifier

@HookItem(path = "API/对应类型消息菜单构建时回调接口")
class MenuBuilderHook : ApiHookItem() {
    private val decorators: Array<OnMenuBuilder> = arrayOf(
        StickerPanelEntry(),
        QQMessageTracker(),
        QQMessageFetcher(),
        ModifyTextMessage(),
        RespondFace(),
        MessageEncryptor(),
        FakeFileRecall(),
    )

    override fun entry(classLoader: ClassLoader) {
        val baseClass = classLoader.loadClass("com.tencent.mobileqq.aio.msglist.holder.component.BaseContentComponent")
        val getMsgMethodName = baseClass.declaredMethods.first {
            it.returnType == classLoader.loadClass("com.tencent.mobileqq.aio.msg.AIOMsgItem") && it.parameterTypes.isEmpty()
        }.name
        val getListMethodName = baseClass.declaredMethods.first {
            Modifier.isAbstract(it.modifiers) && it.returnType == MutableList::class.java && it.parameterTypes.isEmpty()
        }.name
        for (target in decorators.flatMap { it.targetTypes.asIterable() }.toMutableSet()) {
//            Logger.d("target: $target")
            try {
                hookAfter(classLoader.loadClass(target).getDeclaredMethod(getListMethodName)) { param ->
                    val getMsgMethod = baseClass.getDeclaredMethod(getMsgMethodName).apply { isAccessible = true }
                    val aioMsgItem = getMsgMethod.invoke(param.thisObject)!!
                    Logger.d(aioMsgItem.toString())
                    for (decorator in decorators) {
                        if (target in decorator.targetTypes) {
                            decorator.onGetMenu(aioMsgItem, target, param)
                        }
                    }
                }
            } catch (_: NoSuchMethodException) {

            } catch (_: ClassNotFoundException) {

            }

        }
    }
}

interface OnMenuBuilder {
    val targetTypes: Array<String>
        get() = arrayOf(
            "com.tencent.mobileqq.aio.aiogift.AIOTroopGiftComponent",                     // 群礼物
            "com.tencent.mobileqq.aio.msglist.holder.component.anisticker.AIOAniStickerContentComponent", // 动态表情
            "com.tencent.mobileqq.aio.msglist.holder.component.ark.AIOArkContentComponent",               // Ark 卡片
            "com.tencent.mobileqq.aio.msglist.holder.component.ark.AIOCenterArkContentComponent",         // Ark 中央卡片
            "com.tencent.mobileqq.aio.msglist.holder.component.avatar.AIOAvatarContentComponent",         // 头像
            "com.tencent.mobileqq.aio.msglist.holder.component.chain.ChainAniStickerContentComponent",    // 连续动态表情
            "com.tencent.mobileqq.aio.msglist.holder.component.facebubble.AIOFaceBubbleContentComponent", // 表情泡泡
            "com.tencent.mobileqq.aio.msglist.holder.component.file.AIOFileContentComponent",             // 本地文件
            "com.tencent.mobileqq.aio.msglist.holder.component.file.AIOOnlineFileContentComponent",       // 在线文件
            "com.tencent.mobileqq.aio.msglist.holder.component.flashpic.AIOFlashPicContentComponent",     // 闪照
            "com.tencent.mobileqq.aio.msglist.holder.component.fold.AIOFoldContentComponent",             // 折叠消息
            "com.tencent.mobileqq.aio.msglist.holder.component.ickbreak.AIOIceBreakContentComponent",     // 破冰消息
            "com.tencent.mobileqq.aio.msglist.holder.component.leftswipearea.AIOLeftSwipeAreaComponent",  // 左滑区域
            "com.tencent.mobileqq.aio.msglist.holder.component.longmsg.AIOLongMsgContentComponent",       // 长消息
            "com.tencent.mobileqq.aio.msglist.holder.component.markdown.AIORichContentComponent",         // 富文本 / Markdown
            "com.tencent.mobileqq.aio.msglist.holder.component.markdown.AIOMarkdownContentComponent",     // Markdown 富文本
            "com.tencent.mobileqq.aio.msglist.holder.component.marketface.AIOMarketFaceComponent",        // 商城表情
            "com.tencent.mobileqq.aio.msglist.holder.component.mask.AIOContentMaskComponent",             // 内容蒙版
            "com.tencent.mobileqq.aio.msglist.holder.component.mix.AIOMixContentComponent",               // 图文混排
            "com.tencent.mobileqq.aio.msglist.holder.component.msgaction.AIOMsgRecommendComponent",       // 推荐操作
            "com.tencent.mobileqq.aio.msglist.holder.component.msgfollow.AIOMsgFollowComponent",          // 关注提示
            "com.tencent.mobileqq.aio.msglist.holder.component.msgreply.AIOMsgItemReplyComponent",        // 回复子项
            "com.tencent.mobileqq.aio.msglist.holder.component.msgstatus.AIOMsgStatusComponent",          // 发送状态
            "com.tencent.mobileqq.aio.msglist.holder.component.msgtail.AIOGeneralMsgTailContentComponent",// 消息尾
            "com.tencent.mobileqq.aio.msglist.holder.component.multifoward.AIOMultifowardContentComponent",// 合并转发
            "com.tencent.mobileqq.aio.msglist.holder.component.multipci.AIOMultiPicContentComponent",     // 多图
            "com.tencent.mobileqq.aio.msglist.holder.component.nick.AIONickComponent",                    // 昵称
            "com.tencent.mobileqq.aio.msglist.holder.component.pic.AIOPicContentComponent",               // 单图
            "com.tencent.mobileqq.aio.msglist.holder.component.poke.AIOPokeContentComponent",             // 戳一戳
            "com.tencent.mobileqq.aio.msglist.holder.component.position.AIOPositionMsgComponent",         // 地图位置
            "com.tencent.mobileqq.aio.msglist.holder.component.prologue.AIOPrologueContentComponent",     // 新手引导
            "com.tencent.mobileqq.aio.msglist.holder.component.ptt.AIOPttContentComponent",               // 语音
            "com.tencent.mobileqq.aio.msglist.holder.component.reply.AIOReplyComponent",                  // 回复面板
            "com.tencent.mobileqq.aio.msglist.holder.component.select.AIOSelectComponent",                // 选择器
            "com.tencent.mobileqq.aio.msglist.holder.component.sysface.AIOSingleSysFaceContentComponent", // 系统表情
            "com.tencent.mobileqq.aio.msglist.holder.component.template.AIOTemplateMsgComponent",         // 模板消息
            "com.tencent.mobileqq.aio.msglist.holder.component.text.AIOTextContentComponent",             // 文本
            "com.tencent.mobileqq.aio.msglist.holder.component.text.AIOUnsuportContentComponent",         // 不支持的文本
            "com.tencent.mobileqq.aio.msglist.holder.component.timestamp.AIOTimestampComponent",          // 时间戳
            "com.tencent.mobileqq.aio.msglist.holder.component.tofu.AIOTofuContentComponent",             // 豆腐卡片
            "com.tencent.mobileqq.aio.msglist.holder.component.video.AIOVideoContentComponent",           // 视频
            "com.tencent.mobileqq.aio.msglist.holder.component.videochat.AIOVideoResultContentComponent", // 视频通话结果
            "com.tencent.mobileqq.aio.msglist.holder.component.zplan.AIOZPlanContentComponent",           // Z 运营位
            "com.tencent.mobileqq.aio.msglist.holder.component.graptips.common.CommonGrayTipsComponent",  // 提示文本
            "com.tencent.mobileqq.aio.msglist.holder.component.graptips.revoke.RevokeGrayTipsComponent",  // 撤回提示
            "com.tencent.mobileqq.aio.msglist.holder.component.LocationShare.AIOLocationShareComponent",  // 位置共享
            "com.tencent.mobileqq.aio.qwallet.AIOQWalletComponent",                                       // 红包转账
            "com.tencent.mobileqq.aio.shop.AIOShopArkContentComponent",                                   // 商城 Ark 卡片
            "com.tencent.qqnt.aio.sample.BusinessSampleContentComponent",                                 // 业务示例
            "com.tencent.mobileqq.aio.msglist.holder.component.template.AIOTemplateMsgComponent",          // 新版合并转发
            "com.tencent.mobileqq.aio.msglist.holder.component.troopdragonladder.TroopDragonLadderComponent",// 群接龙
            "com.tencent.mobileqq.aio.msglist.holder.component.filtervideo.AIOLiveVideoContentComponent", // 泡泡消息
            "com.tencent.mobileqq.aio.msglist.holder.component.aiaction.AIAvatarActionContentComponent",  // 超级 QQ 秀动作表情
        )

    fun onGetMenu(
        aioMsgItem: Any,
        targetType: String,
        param: XC_MethodHook.MethodHookParam
    )
}
