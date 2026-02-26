package moe.ono.hooks._core

import moe.ono.config.ConfigManager
import moe.ono.constants.Constants.PrekClickableXXX
import moe.ono.constants.Constants.PrekXXX
import moe.ono.hooks._base.ApiHookItem
import moe.ono.hooks._base.BaseClickableFunctionHookItem
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.factory.HookItemFactory
import moe.ono.util.Logger

class HookItemLoader {
    /**
     * 加载并判断哪些需要加载
     */
    fun loadHookItem(process: Int) {
        val allHookItems = HookItemFactory.getAllItemList()
        allHookItems.forEach { hookItem ->
            val path = hookItem.path
            if (hookItem is BaseSwitchFunctionHookItem) {
                hookItem.isEnabled = ConfigManager.getDefaultConfig().getBooleanOrFalse("$PrekXXX${hookItem.path}")
                if (hookItem.isEnabled && (process and hookItem.targetProcess) != 0) {
                    Logger.i("[BaseSwitchFunctionHookItem] Initializing $path...")
                    hookItem.startLoad()
                }
            }
            else if (hookItem is BaseClickableFunctionHookItem) {
                hookItem.isEnabled = ConfigManager.getDefaultConfig().getBooleanOrFalse("$PrekClickableXXX${hookItem.path}")
                if (hookItem.isEnabled && (process and hookItem.targetProcess) != 0) {
                    Logger.i("[BaseClickableFunctionHookItem] Initializing $path...")
                    hookItem.startLoad()
                }

                if (hookItem.alwaysRun) {
                    Logger.i("[BaseClickableFunctionHookItem-AlwaysRun] Initializing $path...")
                    hookItem.startLoad()
                }
            }
            else {
                if (hookItem is ApiHookItem && (process and hookItem.targetProcess) != 0){
                    Logger.i("[API] Initializing $path...")
                    hookItem.startLoad()
                }
            }
        }
    }
}