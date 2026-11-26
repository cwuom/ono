package moe.ono.hooks.base.api

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import moe.ono.BuildConfig
import moe.ono.config.ConfigManager.cGetInt
import moe.ono.config.ConfigManager.cPutInt
import moe.ono.hooks._base.ApiHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.reflex.ClassUtils
import moe.ono.reflex.FieldUtils
import moe.ono.reflex.MethodUtils
import moe.ono.util.HostInfo
import moe.ono.util.Logger
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.LinkedHashSet

@HookItem(path = "API/适配QQMsg内容ViewID")
class QQMsgViewAdapter : ApiHookItem() {

    companion object {
        private var contentViewId = 0

        @JvmStatic
        fun getContentView(msgItemView: View): View {
            return msgItemView.findViewById(contentViewId)
        }

        @JvmStatic
        fun getContentViewId(): Int {
            return contentViewId
        }

        @JvmStatic
        fun hasContentMessage(messageRootView: ViewGroup): Boolean {
            return messageRootView.childCount >= 5
        }
    }

    private val unhooks = ArrayList<XC_MethodHook.Unhook>()

    private fun findContentViewId(): Int {
        return cGetInt(
            "contentViewId${HostInfo.getVersionName()}:${BuildConfig.VERSION_NAME}",
            -1
        )
    }

    private fun putContentViewId(id: Int) {
        cPutInt(
            "contentViewId${HostInfo.getVersionName()}:${BuildConfig.VERSION_NAME}",
            id
        )
    }

    override fun entry(loader: ClassLoader) {
        val cachedViewId = findContentViewId()
        if (cachedViewId > 0) {
            contentViewId = cachedViewId
            return
        }

        val holderClass = ClassUtils.findClass("com.tencent.mobileqq.aio.msglist.holder.AIOBubbleMsgItemVB")
        val candidateMethods = collectCandidateMethods(holderClass)
        if (candidateMethods.isEmpty()) {
            Logger.e("适配QQMsg内容ViewID", "No candidate update method found in ${holderClass.name}")
            return
        }

        candidateMethods.forEach { method ->
            unhooks.add(hookAfter(method) { param ->
                handleItemViewUpdate(param.thisObject)
            })
        }
    }

    private fun collectCandidateMethods(holderClass: Class<*>): List<Method> {
        val candidates = LinkedHashSet<Method>()
        try {
            MethodUtils.create(holderClass)
                .methodName("handleUIState")
                .returnType(Void.TYPE)
                .getResult()
                .forEach { candidates.add(it) }
        } catch (_: Throwable) {
        }

        holderClass.declaredMethods
            .filterTo(candidates) { isCandidateMethod(it) }
        return candidates.toList()
    }

    private fun isCandidateMethod(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers) || method.returnType != Void.TYPE) {
            return false
        }
        val parameterTypes = method.parameterTypes
        if (parameterTypes.size !in 3..5) {
            return false
        }
        val containsList = parameterTypes.any { List::class.java.isAssignableFrom(it) }
        val containsBundle = parameterTypes.any { Bundle::class.java.isAssignableFrom(it) }
        val containsInt = parameterTypes.any {
            it == Int::class.javaPrimitiveType || it == Int::class.javaObjectType
        }
        return containsList && containsBundle && containsInt
    }

    private fun handleItemViewUpdate(thisObject: Any) {
        if (contentViewId > 0) {
            clearHooks()
            return
        }

        val msgView = FieldUtils.create(thisObject)
            .fieldType(View::class.java)
            .firstValue<View>(thisObject) ?: return

        val aioMsgItem = FieldUtils.create(thisObject)
            .fieldType(ClassUtils.findClass("com.tencent.mobileqq.aio.msg.AIOMsgItem"))
            .firstValue<Any>(thisObject) ?: return

        val msgRecord = try {
            MethodUtils.create(aioMsgItem.javaClass)
                .methodName("getMsgRecord")
                .callFirst<Any>(aioMsgItem)
        } catch (_: Throwable) {
            return
        }

        val elements = try {
            FieldUtils.getField<ArrayList<Any>>(msgRecord, "elements", ArrayList::class.java)
        } catch (_: Throwable) {
            return
        }

        val msgViewGroup = msgView as? ViewGroup ?: return
        for (msgElement in elements) {
            val type = try {
                FieldUtils.getField<Int>(msgElement, "elementType", Int::class.javaPrimitiveType)
            } catch (_: Throwable) {
                continue
            }
            if (type <= 2) {
                findContentView(msgViewGroup)
                if (contentViewId > 0) {
                    clearHooks()
                }
                break
            }
        }
    }

    private fun findContentView(itemView: ViewGroup) {
        for (i in 0..<itemView.childCount) {
            val child = itemView.getChildAt(i)
            if (child.javaClass.name == "com.tencent.qqnt.aio.holder.template.BubbleLayoutCompatPress") {
                contentViewId = child.id
                putContentViewId(child.id)
                break
            }
        }
    }

    private fun clearHooks() {
        unhooks.forEach {
            try {
                it.unhook()
            } catch (_: Throwable) {
            }
        }
        unhooks.clear()
    }
}
