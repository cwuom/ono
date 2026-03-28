package moe.ono.hooks.base.api

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.annotation.HookItem
import moe.ono.util.Logger
import java.util.concurrent.ConcurrentHashMap

@HookItem(path = "API/MqqapiHandler", description = "监听 Mqqapi")
class MqqapiHandler : BaseSwitchFunctionHookItem() {

    private val hookedClasses = ConcurrentHashMap.newKeySet<String>()
    companion object {
        val listeners = mutableListOf<(param: XC_MethodHook.MethodHookParam) -> Unit>()
    }

    override fun entry(classLoader: ClassLoader) {
        Logger.d("MqqapiHandler loaded")
        hookBr(classLoader)
    }

    private fun hookBr(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.tencent.mobileqq.utils.br",
                classLoader,
                "c",
                XposedHelpers.findClass("com.tencent.common.app.business.BaseQQAppInterface", classLoader),
                Context::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val url = param.args.getOrNull(2) as? String ?: return
                            if (!url.startsWith("mqqapi://")) return

                            val result = param.result ?: run {
                                Logger.d("""
                                    [br.c][after]
                                    url=$url
                                    result=null
                                """.trimIndent())
                                return
                            }

                            val actualClass = result.javaClass
//                            Logger.d("""
//                                [br.c][after]
//                                url=$url
//                                actionClass=${actualClass.name}
//                                action=$result
//                            """.trimIndent())

                            hookActualAzClass(actualClass)
                        } catch (t: Throwable) {
                            Logger.e(t)
                        }
                    }
                }
            )
            Logger.d("MqqapiHandler hook br.c() success")
        } catch (t: Throwable) {
            Logger.e(t)
        }
    }

    private fun hookActualAzClass(clazz: Class<*>) {
        try {
            val name = clazz.name
            if (!hookedClasses.add(name)) return

            XposedBridge.hookAllMethods(clazz, "b", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val obj = param.thisObject ?: return

                        val source = getField(obj, "c")
                        val serverName = getField(obj, "d")
                        val actionName = getField(obj, "e")
                        val params = getField(obj, "f")
                        val isFromJumpActivity = getField(obj, "g")
                        val from = getField(obj, "h")
                        val pkgName = getField(obj, "i")
                        val pkgSig = getField(obj, "j")
                        val isInternalAction = getField(obj, "z")

//                        Logger.d(
//                            """
//                            [${clazz.name}.b][before]
//                            source=$source
//                            server_name=$serverName
//                            action_name=$actionName
//                            params=$params
//                            isFromJumpActivity=$isFromJumpActivity
//                            from=$from
//                            pkgName=$pkgName
//                            pkgSig=$pkgSig
//                            isInternalAction=$isInternalAction
//                            stack=
//                            ${Log.getStackTraceString(Throwable())}
//                            """.trimIndent()
//                        )

                        listeners.forEach { listener ->
                            listener(param)
                        }
                    } catch (t: Throwable) {
                        Logger.e(t)
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        Logger.d("""
                            [${clazz.name}.b][after]
                            result=${param.result}
                        """.trimIndent())
                    } catch (t: Throwable) {
                        Logger.e(t)
                    }
                }
            })

            Logger.d("Hooked actual az subclass: $name")
        } catch (t: Throwable) {
            Logger.e(t)
        }
    }

    private fun getField(obj: Any, name: String): Any? {
        return try {
            var cls: Class<*>? = obj.javaClass
            while (cls != null && cls != Any::class.java) {
                try {
                    val field = cls.getDeclaredField(name)
                    field.isAccessible = true
                    return field.get(obj)
                } catch (_: NoSuchFieldException) {
                }
                cls = cls.superclass
            }
            null
        } catch (_: Throwable) {
            null
        }
    }
}