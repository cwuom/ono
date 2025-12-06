package moe.ono.hooks.item.chat

import android.annotation.SuppressLint
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedHelpers
import moe.ono.R
import moe.ono.bridge.ntapi.MsgServiceHelper
import moe.ono.config.CacheConfig.getRKeyGroup
import moe.ono.config.CacheConfig.getRKeyPrivate
import moe.ono.config.ConfigManager
import moe.ono.constants.Constants
import moe.ono.constants.Constants.MethodCacheKey_ChatPanelBtn
import moe.ono.creator.PacketHelperDialog
import moe.ono.creator.stickerPanel.Env
import moe.ono.creator.stickerPanel.ICreator
import moe.ono.creator.stickerPanel.PanelUtils
import moe.ono.dexkit.TargetManager.requireMethod
import moe.ono.hooks._base.BaseClickableFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.hooks._core.factory.HookItemFactory.getItem
import moe.ono.hooks.dispatcher.OnMenuBuilder
import moe.ono.reflex.Reflex
import moe.ono.ui.CommonContextWrapper
import moe.ono.util.AppRuntimeHelper
import moe.ono.util.ContextUtils
import moe.ono.util.CustomMenu
import moe.ono.util.Logger
import moe.ono.util.Session
import moe.ono.util.SyncUtils
import java.util.Locale
import androidx.core.view.size


@SuppressLint("DiscouragedApi")
@HookItem(
    path = "聊天与消息/表情面板",
    description = "点击配置表情面板"
)
class StickerPanelEntry : BaseClickableFunctionHookItem(), OnMenuBuilder {
    private fun hookEmoBtn() {
        try {
            val method = requireMethod(MethodCacheKey_ChatPanelBtn)


            hookAfter(method) { param: MethodHookParam ->
                val layout = param.thisObject as LinearLayout
                for (i in 0..<layout.size) {
                    val child = layout.getChildAt(i)
                    if (child is ImageView && child.getContentDescription() != null) {
                        val panelIcon = child
                        if ("表情".contentEquals(panelIcon.getContentDescription())) {
                            if (!ConfigManager.dGetBoolean(Constants.PrekCfgXXX + "replaceStickerPanelClickEvent")) {
                                panelIcon.setOnLongClickListener { view: View ->
                                    ICreator.createPanel(view.context)
                                    true
                                }
                            } else {
                                val listenerInfo = XposedHelpers.callMethod(panelIcon, "getListenerInfo")
                                if (listenerInfo != null) {
                                    val oldClick = XposedHelpers.getObjectField(listenerInfo, "mOnClickListener")
                                            as? View.OnClickListener

                                    oldClick?.let {
                                        panelIcon.setOnLongClickListener { v ->
                                            it.onClick(v)
                                            true
                                        }
                                    }

                                    panelIcon.setOnClickListener { v ->
                                        ICreator.createPanel(v.context)
                                    }
                                }
                            }
                        }
                    }
                }
                val imageView = param.result as ImageView
            }
        } catch (e: NoSuchMethodException) {
            Logger.e(this.itemName, e)
        }
    }

    override fun entry(classLoader: ClassLoader) {
        hookEmoBtn()
//        createStickerPanelIcon()

    }

    override val targetTypes = arrayOf(
        "com.tencent.mobileqq.aio.msglist.holder.component.pic.AIOPicContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.mix.AIOMixContentComponent",
        "com.tencent.mobileqq.aio.msglist.holder.component.marketface.AIOMarketFaceComponent"
    )

    override fun onGetMenu(aioMsgItem: Any, targetType: String, param: MethodHookParam) {
        if (!getItem(this.javaClass).isEnabled) {
            return
        }
        val item: Any = CustomMenu.createItemIconNt(
            aioMsgItem,
            "保存",
            R.drawable.ic_item_save_72dp,
            R.id.item_save_to_panel
        ) {
            val rkeyGroup = getRKeyGroup()
            val rkeyPrivate = getRKeyPrivate()

            try {
                val msgID = Reflex.invokeVirtual(aioMsgItem, "getMsgId") as Long
                val msgIDs = java.util.ArrayList<Long>()
                msgIDs.add(msgID)
                AppRuntimeHelper.getAppRuntime()
                    ?.let {
                        MsgServiceHelper.getKernelMsgService(
                            it
                        )
                    }?.getMsgsByMsgId(
                        Session.getContact(),
                        msgIDs
                    ) { _, _, msgList ->
                        SyncUtils.runOnUiThread {
                            for (msgRecord in msgList) {
                                val md5s =
                                    java.util.ArrayList<String>()
                                val urls =
                                    java.util.ArrayList<String>()

                                for (element in msgRecord.elements) {
                                    if (element.marketFaceElement != null) {
                                        val marketFaceElement = element.marketFaceElement
                                        val id = marketFaceElement.emojiId
                                        val localPath = Env.app_path + marketFaceElement.staticFacePath
                                        Logger.d("local path: $localPath")
                                        PanelUtils.preSaveMarketPicToList(
                                            localPath, id,
                                            CommonContextWrapper.createAppCompatContext(ContextUtils.getCurrentActivity())
                                        )
                                        return@runOnUiThread
                                    }
                                    if (element.picElement != null) {
                                        val picElement = element.picElement

                                        md5s.add(
                                            picElement.md5HexStr.uppercase(Locale.getDefault())
                                        )
                                        val originUrl = picElement.originImageUrl
                                        if (TextUtils.isEmpty(originUrl)) {
                                            urls.add(
                                                "https://gchat.qpic.cn/gchatpic_new/0/0-0-" + picElement.md5HexStr
                                                    .uppercase(Locale.getDefault()) + "/0"
                                            )
                                        } else {
                                            Logger.d("rkeyGroup: $rkeyGroup, rkeyPrivate: $rkeyPrivate")
                                            if (originUrl.startsWith("/download")) {
                                                if (originUrl.contains("appid=1406")) {
                                                    urls.add("https://multimedia.nt.qq.com.cn$originUrl$rkeyGroup")
                                                } else {
                                                    urls.add("https://multimedia.nt.qq.com.cn$originUrl$rkeyPrivate")
                                                }
                                            } else {
                                                urls.add("https://gchat.qpic.cn" + picElement.originImageUrl)
                                            }
                                        }
                                    }
                                }
                                if (md5s.isNotEmpty()) {
                                    Logger.d("urls: $urls")
                                    if (md5s.size > 1) {
                                        PanelUtils.preSaveMultiPicList(
                                            urls,
                                            md5s,
                                            CommonContextWrapper.createAppCompatContext(ContextUtils.getCurrentActivity())
                                        )
                                    } else {
                                        PanelUtils.preSavePicToList(
                                            urls[0], md5s[0],
                                            CommonContextWrapper.createAppCompatContext(ContextUtils.getCurrentActivity())
                                        )
                                    }
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                Logger.e("StickerPanelEntryHooker.msgLongClickSaveToLocal", e)
            }
            Unit
        }
        param.result = listOf(item) + param.result as List<*>
    }

}