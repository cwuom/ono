from pathlib import Path
import os
import shutil
import subprocess
import sys
repo = Path(__file__).resolve().parents[2]
root = Path(__file__).parent / 'generated' / 'sticker-entry'
root.mkdir(parents=True, exist_ok=True)
relative_source = 'app/src/main/java/moe/ono/hooks/item/chat/StickerPanelEntry.kt'
source = subprocess.check_output(['git', 'show', 'HEAD:' + relative_source], cwd=repo, encoding='utf-8') if '--baseline' in sys.argv else (repo / relative_source).read_text(encoding='utf-8')
methods = source[source.index('    private fun hookEmoBtn()'):source.index('    override fun entry')]
fixture = r'''
open class View {
    fun interface OnClickListener { fun onClick(view: View) }
    val context = Any()
    var description: CharSequence? = null
    fun getContentDescription() = description
    val contentDescription get() = description
    var click: OnClickListener? = null
    var longClick: ((View) -> Boolean)? = null
    var clickInstalls = 0
    var longInstalls = 0
    private val tags = mutableMapOf<Int, Any?>()
    fun getTag(key: Int) = tags[key]
    fun setTag(key: Int, value: Any?) { tags[key] = value }
    fun setOnClickListener(listener: OnClickListener) { click = listener; clickInstalls++ }
    fun setOnLongClickListener(listener: (View) -> Boolean) { longClick = listener; longInstalls++ }
}
open class ViewGroup(vararg views: View) : View() {
    val children = views.toMutableList()
    val childCount get() = children.size
    val size get() = children.size
    fun getChildAt(index: Int) = children[index]
}
class LinearLayout(vararg views: View) : ViewGroup(*views)
class ImageView(description: String? = null) : View() { init { this.description = description } }
class MethodHookParam(val thisObject: Any, val result: Any?)
object R { object id { const val ono_sticker_panel_bound = 1 } }
object Constants { const val PrekCfgXXX = "cfg" }
const val MethodCacheKey_ChatPanelBtn = "panel"
object ConfigManager { var replacement = false; fun dGetBoolean(key: String) = replacement.also { check(key.isNotEmpty()) } }
object ICreator { var opens = 0; fun createPanel(context: Any) { check(context != Unit); opens++ } }
object Logger { fun e(name: String, e: Exception) { error("$name: $e") } }
object XposedHelpers {
    fun callMethod(view: View, name: String): Any? { check(name == "getListenerInfo"); return view }
    fun getObjectField(value: Any, name: String): Any? { check(name == "mOnClickListener"); return (value as View).click }
}
fun requireMethod(key: String): Any { check(key.isNotEmpty()); return Unit }
class Subject {
    val itemName = "test"
    lateinit var callback: (MethodHookParam) -> Unit
    fun hookAfter(method: Any, action: (MethodHookParam) -> Unit) { check(method == Unit); callback = action }
    fun install() = hookEmoBtn()
    fun bind(view: View, result: Any? = ImageView()) = callback(MethodHookParam(view, result))
__METHODS__
}
fun main() {
    val failures = mutableListOf<String>()
    fun test(name: String, block: () -> Unit) {
        ICreator.opens = 0
        try { block(); println("PASS $name") } catch (e: Throwable) { failures += "$name: $e"; println("FAIL ${failures.last()}") }
    }
    test("nested icon preserves QQ click and opens ONO on long click") {
        ConfigManager.replacement = false
        var original = 0
        val icon = ImageView("表情").apply { setOnClickListener { original++ } }
        val subject = Subject().apply { install() }
        subject.bind(LinearLayout(ViewGroup(ViewGroup(icon))))
        check(icon.longClick?.invoke(icon) == true) { "nested icon was not bound" }
        check(ICreator.opens == 1)
        icon.click!!.onClick(icon)
        check(original == 1)
    }
    test("replacement repeated binding preserves original QQ click on long click") {
        ConfigManager.replacement = true
        var original = 0
        val icon = ImageView("表情").apply { setOnClickListener { original++ } }
        val subject = Subject().apply { install() }
        val root = LinearLayout(icon)
        subject.bind(root)
        subject.bind(root)
        subject.bind(root)
        check(icon.longClick?.invoke(icon) == true)
        check(original == 1 && ICreator.opens == 0) { "long click called ONO or lost QQ listener" }
        icon.click!!.onClick(icon)
        check(ICreator.opens == 1)
        check(icon.clickInstalls == 2 && icon.longInstalls == 1) { "same icon rebound" }
    }
    test("normal mode repeated binding installs once") {
        ConfigManager.replacement = false
        val icon = ImageView("表情")
        val subject = Subject().apply { install() }
        val root = LinearLayout(icon)
        subject.bind(root)
        subject.bind(root)
        check(icon.longInstalls == 1) { "same icon rebound" }
    }
    test("unrelated and null description icons unchanged") {
        ConfigManager.replacement = true
        val icons = listOf(ImageView("图片"), ImageView())
        val subject = Subject().apply { install() }
        subject.bind(LinearLayout(*icons.toTypedArray()))
        check(icons.all { it.longInstalls == 0 && it.clickInstalls == 0 })
    }
    test("replacement nested icon opens QQ on long click") {
        ConfigManager.replacement = true
        var original = 0
        val icon = ImageView("表情").apply { setOnClickListener { original++ } }
        val subject = Subject().apply { install() }
        subject.bind(LinearLayout(ViewGroup(icon)))
        check(icon.longClick?.invoke(icon) == true)
        check(original == 1 && ICreator.opens == 0)
        icon.click!!.onClick(icon)
        check(ICreator.opens == 1)
    }
    test("recreated host icons are each bound") {
        ConfigManager.replacement = false
        val subject = Subject().apply { install() }
        val first = ImageView("表情")
        val second = ImageView("表情")
        val root = LinearLayout(first)
        subject.bind(root)
        root.children.clear()
        root.children += ViewGroup(second)
        subject.bind(root)
        check(first.longInstalls == 1 && second.longInstalls == 1)
    }
    test("void result is accepted") {
        ConfigManager.replacement = false
        val subject = Subject().apply { install() }
        subject.bind(LinearLayout(ImageView("表情")), null)
    }
    check(failures.isEmpty()) { "${failures.size} regressions failed" }
}
'''
def main():
    # model Android getters without a JVM accessor collision
    compiled_fixture = fixture.replace('fun getContentDescription() = description', 'fun getContentDescription(): CharSequence? = description').replace('val contentDescription get() = description', '')
    compiled_methods = methods.replace('.contentDescription', '.getContentDescription()')
    (root / 'Harness.kt').write_text(compiled_fixture.replace('__METHODS__', compiled_methods), encoding='utf-8')
    compiler = shutil.which('kotlinc') or r'C:\Program Files\Android\Android Studio\plugins\Kotlin\kotlinc\bin\kotlinc.bat'
    stdlib = str(Path(compiler).resolve().parent.parent / 'lib' / 'kotlin-stdlib.jar')
    compile_result = subprocess.run([compiler, str(root / 'Harness.kt'), '-no-stdlib', '-classpath', stdlib, '-d', str(root / 'harness.jar')], text=True, capture_output=True)
    print(compile_result.stdout + compile_result.stderr)
    if compile_result.returncode:
        return compile_result.returncode
    java = str(Path(os.environ['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')) if os.environ.get('JAVA_HOME') else shutil.which('java') or 'C:/Program Files/Android/Android Studio/jbr/bin/java.exe'
    result = subprocess.run([java, '-cp', str(root / 'harness.jar') + os.pathsep + stdlib, 'HarnessKt'], text=True, capture_output=True)
    print(result.stdout + result.stderr)
    return result.returncode


if __name__ == '__main__':
    raise SystemExit(main())
