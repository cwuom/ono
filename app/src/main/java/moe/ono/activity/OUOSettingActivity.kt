package moe.ono.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.annotation.CallSuper
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.ono.BuildConfig
import moe.ono.R
import moe.ono.config.ConfigManager
import moe.ono.config.SettingItem
import moe.ono.config.getDynamicClickableSettings
import moe.ono.config.getDynamicSwitchSettings
import moe.ono.constants.Constants.PrekCfgXXX
import moe.ono.constants.Constants.PrekEnableLog
import moe.ono.constants.Constants.PrekSendFakeFile
import moe.ono.constants.Constants.PrekXXX
import moe.ono.creator.center.ClickableFunctionDialog.showCFGDialogQQMessageTracker
import moe.ono.creator.center.ClickableFunctionDialog.showCFGDialogStickerPanelEntry
import moe.ono.creator.center.ClickableFunctionDialog.showCFGDialogSurnamePredictor
import moe.ono.databinding.ActivitySettingBinding
import moe.ono.dexkit.TargetManager
import moe.ono.dexkit.TargetManager.removeAllMethodSignature
import moe.ono.fragment.BaseSettingFragment
import moe.ono.hooks._base.BaseClickableFunctionHookItem
import moe.ono.hooks._base.BaseSwitchFunctionHookItem
import moe.ono.hooks._core.factory.HookItemFactory.getItem
import moe.ono.hooks.base.util.Toasts
import moe.ono.hooks.item.chat.StickerPanelEntry
import moe.ono.hooks.item.sigma.QQMessageTracker
import moe.ono.hooks.item.sigma.QQSurnamePredictor
import moe.ono.hostInfo
import moe.ono.isInHostProcess
import moe.ono.ui.ThemeAttrUtils
import moe.ono.ui.view.BgEffectPainter
import moe.ono.util.Utils.convertTimestampToDate
import moe.ono.util.Utils.jump
import rikka.material.preference.MaterialSwitchPreference
import rikka.preference.SimpleMenuPreference
import java.lang.Integer.max
import androidx.core.graphics.drawable.toDrawable
import moe.ono.creator.center.ClickableFunctionDialog.showCFGDialogMessageEncryptor
import moe.ono.creator.center.ClickableFunctionDialog.showCFGDialogQQBubbleRedirect
import moe.ono.creator.center.ClickableFunctionDialog.showCFGDialogQQPacketHelperEntry
import moe.ono.creator.center.ClickableFunctionDialog.showCFGDialogSelfMessageReactor
import moe.ono.hooks.item.chat.MessageEncryptor
import moe.ono.hooks.item.chat.QQBubbleRedirect
import moe.ono.hooks.item.chat.SelfMessageReactor
import moe.ono.hooks.item.developer.QQPacketHelperEntry
import moe.ono.hooks.item.entertainment.FuckSport

open class OUOSettingActivity : BaseActivity() {
    private var mAppBarLayoutHeight: Int = 0
    private val mPendingOnStartActions = ArrayList<Runnable>(4)
    private val mPendingOnResumeActions = ArrayList<Runnable>(4)
    private val mPendingActionsLock = Any()
    private val mFragmentStack = ArrayList<BaseSettingFragment>(4)

    private lateinit var mBgEffectView: View
    private lateinit var mBgEffectPainter: BgEffectPainter
    private val startTime = System.nanoTime().toFloat()
    private val mHandler = Handler(Looper.getMainLooper())
    private lateinit var binding: ActivitySettingBinding
    private lateinit var mAppBarLayout: AppBarLayout
    lateinit var mAppToolBar: androidx.appcompat.widget.Toolbar

    private val runnableBgEffect = object : Runnable {
        override fun run() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mBgEffectPainter.setAnimTime(((System.nanoTime() - startTime) / 1.0E9f) % 62.831852f)
                mBgEffectPainter.setResolution(floatArrayOf(mBgEffectView.width.toFloat(), mBgEffectView.height.toFloat()))
                mBgEffectPainter.updateMaterials()
                mBgEffectView.setRenderEffect(mBgEffectPainter.renderEffect)
                mHandler.postDelayed(this, 16L)
            }
        }
    }

    @CallSuper
    override fun doOnEarlyCreate(savedInstanceState: Bundle?, isInitializing: Boolean) {
        super.doOnEarlyCreate(savedInstanceState, isInitializing)
        setTheme(R.style.Theme_Ono)
    }

    /**
     * Handle saved instance state ourselves
     */
    override fun shouldRetainActivitySavedInstanceState() = false

    @SuppressLint("CommitTransaction")
    override fun doOnCreate(savedInstanceState: Bundle?): Boolean {
        // we don't want the Fragment to be recreated
        DynamicColors.applyToActivitiesIfAvailable(this.application)
        getTheme().applyStyle(rikka.material.preference.R.style.ThemeOverlay_Rikka_Material3_Preference, true)

        val title = intent.getStringExtra("title")
        val subtitle = intent.getStringExtra("subtitle")

        super.doOnCreate(null)
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // update window background, I don't know why, but it's necessary
        val bgColor = ThemeAttrUtils.resolveColorOrDefaultColorInt(this, android.R.attr.windowBackground, 0)
        window.setBackgroundDrawable(bgColor.toDrawable())
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
                    or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        )
        requestTranslucentStatusBar()

        if (isInHostProcess) {
            val isThemeLightMode = resources.getBoolean(R.bool.is_not_night_mode)
            if (isThemeLightMode) {
                // QQ 8.9.78(4548)+
                // We need to tell system we are using a light title bar and we want a dark status bar text
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val insetsController = window.decorView.windowInsetsController
                    val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    insetsController?.setSystemBarsAppearance(flags, flags)
                } else {
                    window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            }
        }

        setHyperBackground()

        mAppBarLayout = findViewById(R.id.topAppBarLayout)
        mAppToolBar = findViewById(R.id.topAppBar)
        mAppBarLayout.background = mAppToolBar.background
        setSupportActionBar(mAppToolBar)
        requestTranslucentStatusBar()
        mAppBarLayout.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            mAppBarLayoutHeight = bottom - top
            for (fragment in mFragmentStack) {
                fragment.notifyLayoutPaddingsChanged()
            }
        }


        if (title != null) {
            mAppToolBar.title = title
            mAppToolBar.menu.clear()
        }

        if (subtitle != null) {
            mAppToolBar.subtitle = subtitle
        }


        supportFragmentManager
            .beginTransaction()
            .replace(
                R.id.settings,
                if (title == null) {
                    SettingsFragment()
                } else {
                    SettingsFragmentNextStep(title)
                }
            )
            .commit()


        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            val version = findPreference<Preference>("version")
            val buildTime = findPreference<Preference>("build_time")
            val buildUUID = findPreference<Preference>("build_uuid")
            val enableLog = findPreference<MaterialSwitchPreference>("prek_enable_log")
            val hookPriority = findPreference<SimpleMenuPreference>("hook_priority")
            version?.setSummary(BuildConfig.VERSION_NAME)
            buildTime?.setSummary(convertTimestampToDate(BuildConfig.BUILD_TIMESTAMP))
            buildUUID?.setSummary(BuildConfig.BUILD_UUID)
            enableLog?.isChecked = ConfigManager.getDefaultConfig().getBooleanOrFalse(PrekEnableLog)
            enableLog?.setOnPreferenceChangeListener { _, newValue ->
                val isEnabled = newValue as Boolean
                ConfigManager.getDefaultConfig().edit().putBoolean(PrekEnableLog, isEnabled).apply()
                true
            }

            hookPriority?.value = ConfigManager.getDefaultConfig().getInt(PrekCfgXXX+"hook_priority", 50).toString()

            hookPriority?.setOnPreferenceChangeListener { _, newValue ->
                ConfigManager.getDefaultConfig().edit().putInt(PrekCfgXXX+"hook_priority",
                    Integer.parseInt(newValue.toString())
                ).apply()
                true
            }
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            val key = preference.key
            when (key) {
                "telegram" -> {
                    jump(requireContext(), "https://t.me/ouom_pub")
                    return super.onPreferenceTreeClick(preference)
                }
                "github" -> {
                    jump(requireContext(), "https://github.com/cwuom/ono")
                    return super.onPreferenceTreeClick(preference)
                }
                "build_time", "build_uuid", "version","prek_enable_log", "hook_priority" -> {
                    return super.onPreferenceTreeClick(preference)
                }
            }

            key?.let {
                val intent = Intent(requireContext(), OUOSettingActivity::class.java).apply {
                    putExtra("title", it)
                    if (it == "Sigma") {
                        putExtra("subtitle", "此窗口内的功能存在一定风险和争议，请谨慎使用")
                    }
                }
                startActivity(intent)
            }
            return super.onPreferenceTreeClick(preference)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        if (intent.getStringExtra("title") == null){
            menuInflater.inflate(R.menu.ouo_setting_menu, menu)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.menu_remove_all_method_signature) {

            MaterialAlertDialogBuilder(this)
                .setTitle("确定重新适配宿主吗？")
                .setMessage("这可能会花费一些时间。")
                .setPositiveButton("确定") { _: DialogInterface?, _: Int ->
                    Toasts.info(this, "重启QQ生效")
                    removeAllMethodSignature()
                    TargetManager.setIsNeedFindTarget(true)
                }
                .setNegativeButton("取消") { _: DialogInterface, _: Int -> }
                .show()

        } else {
            return super@OUOSettingActivity.onOptionsItemSelected(item)
        }
        return true
    }


    open val layoutPaddingTop: Int
        get() = max(mAppBarLayoutHeight, statusBarLayoutInsect)

    open val layoutPaddingBottom: Int
        get() = navigationBarLayoutInsect

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // notifies the fragment that it is attached to the window
        for (fragment in mFragmentStack) {
            fragment.notifyLayoutPaddingsChanged()
        }
    }

    override fun doOnDestroy() {
        super.doOnDestroy()
        synchronized(mPendingActionsLock) {
            mPendingOnStartActions.clear()
            mPendingOnResumeActions.clear()
        }
    }

    

    @CallSuper
    override fun doOnResume() {
        super.doOnResume()
        synchronized(mPendingActionsLock) {
            // on start actions
            for (action in mPendingOnStartActions) {
                action.run()
            }
            mPendingOnStartActions.clear()
            // on resume actions
            for (action in mPendingOnResumeActions) {
                action.run()
            }
            mPendingOnResumeActions.clear()
        }
    }

    @CallSuper
    override fun doOnStart() {
        super.doOnStart()
        // on start actions
        synchronized(mPendingActionsLock) {
            for (action in mPendingOnStartActions) {
                action.run()
            }
            mPendingOnStartActions.clear()
        }
    }

    /*
   * Hyper OS 2.0 !
   * */
    private fun setHyperBackground() {
        val contentView = findViewById<ViewGroup>(android.R.id.content)

        contentView.setBackgroundColor(getColor(R.color.bg))
        mBgEffectView = LayoutInflater.from(this).inflate(R.layout.layout_effect_bg, contentView, false)
        contentView.addView(mBgEffectView, 0)
        mBgEffectView = contentView.findViewById(R.id.bgEffectView)

        mBgEffectView.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mBgEffectPainter = BgEffectPainter(hostInfo.application)
                val isThemeLightMode = resources.getBoolean(R.bool.is_night_mode)
                mBgEffectPainter.showRuntimeShader(hostInfo.application, mBgEffectView, binding.topAppBar, isThemeLightMode)
            }

            mHandler.post(runnableBgEffect)
        }

    }

    class SettingsFragmentNextStep(private val t: String) : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.createPreferenceScreen(requireContext()).apply {
                getDynamicSwitchSettings().forEach { settingItem ->
                    if (settingItem.t == t){
                        val preference = MaterialSwitchPreference(context).apply {
                            key = settingItem.key
                            title = settingItem.title
                            summary = settingItem.summary
                            icon = settingItem.iconResId?.let { context.getDrawable(it) }
                            isChecked = settingItem.isSwitch
                            isPersistent = false
                            setOnPreferenceClickListener {
                                handleItemClick(settingItem, isChecked)
                                true
                            }
                        }
                        addPreference(preference)
                    }

                }

                getDynamicClickableSettings().forEach { settingItem ->
                    if (settingItem.t == t){
                        val preference = Preference(context).apply {
                            key = settingItem.key
                            title = settingItem.title
                            summary = settingItem.summary
                            icon = settingItem.iconResId?.let { context.getDrawable(it) }
                            isPersistent = false
                            setOnPreferenceClickListener {
                                handleItemClick(settingItem, false)
                                true
                            }
                        }
                        addPreference(preference)
                    }

                }

                when (t) {
                    "娱乐功能" -> {
                        val preference = MaterialSwitchPreference(context).apply {
                            key = "发送假文件"
                            title = "发送假文件"
                            summary = "此选项需要配合“聊天与消息/快捷菜单”使用"
                            isChecked = ConfigManager.getDefaultConfig().getBooleanOrFalse(PrekSendFakeFile)
                            isPersistent = false
                            setOnPreferenceClickListener {
                                ConfigManager.getDefaultConfig().edit().putBoolean(PrekSendFakeFile, isChecked).apply()
                                true
                            }
                        }
                        addPreference(preference)
                    }
                }

            }.also { preferenceScreen = it }
        }

        private fun handleItemClick(settingItem: SettingItem, isChecked: Boolean) {
            ConfigManager.getDefaultConfig().edit().putBoolean("$PrekXXX${settingItem.path}", isChecked).apply()
            val item = getItem(settingItem.clazz::class.java)
            if (item is BaseSwitchFunctionHookItem) {
                item.isEnabled = isChecked
                if (isChecked){
                    item.startLoad()
                }
                return
            }

            if (item is BaseClickableFunctionHookItem) {
                when (item.path) {
                    getItem(QQSurnamePredictor::class.java).path -> {
                        showCFGDialogSurnamePredictor(item, requireContext())
                    }
                    getItem(StickerPanelEntry::class.java).path -> {
                        showCFGDialogStickerPanelEntry(item, requireContext())
                    }
                    getItem(QQMessageTracker::class.java).path -> {
                        showCFGDialogQQMessageTracker(item, requireContext())
                    }
                    getItem(SelfMessageReactor::class.java).path -> {
                        showCFGDialogSelfMessageReactor(item, requireContext())
                    }
                    getItem(MessageEncryptor::class.java).path -> {
                        showCFGDialogMessageEncryptor(item, requireContext())
                    }
                    getItem(QQBubbleRedirect::class.java).path -> {
                        showCFGDialogQQBubbleRedirect(item, requireContext())
                    }
                    getItem(QQPacketHelperEntry::class.java).path -> {
                        showCFGDialogQQPacketHelperEntry(item, requireContext())
                    }
                    getItem(FuckSport::class.java).path -> {
                        FuckSport.showDialog(requireContext())
                    }
                }
            }

        }
    }

    companion object {
        const val TARGET_FRAGMENT_KEY: String = "OUOSettingActivity.TARGET_FRAGMENT_KEY"
        const val TARGET_FRAGMENT_ARGS_KEY: String = "OUOSettingActivity.TARGET_FRAGMENT_ARGS_KEY"

        @JvmStatic
        @JvmOverloads
        fun createStartActivityForFragmentIntent(
            context: Context,
            fragmentClass: Class<out BaseSettingFragment>,
            args: Bundle? = null
        ): Intent {
            val intent = Intent(context, OUOSettingActivity::class.java)
            intent.putExtra(TARGET_FRAGMENT_KEY, fragmentClass.name)
            intent.putExtra(TARGET_FRAGMENT_ARGS_KEY, args)
            return intent
        }
    }
}