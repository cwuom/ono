from pathlib import Path
import os
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
BASE = Path(__file__).parent / 'generated' / 'markdown-regression'
JAVA = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else Path(shutil.which('javac') or 'C:/Program Files/Android/Android Studio/jbr/bin/javac.exe').parent
EXE = '.exe' if os.name == 'nt' else ''
source_path = 'app/src/main/java/moe/ono/hooks/item/bugfix/MarkdownGuard.java'
source = subprocess.check_output(['git','show','HEAD:'+source_path],cwd=ROOT,encoding='utf-8') if '--baseline' in sys.argv else (ROOT/source_path).read_text(encoding='utf-8')
utils = (ROOT/'app/src/main/java/moe/ono/util/Utils.java').read_text(encoding='utf-8')
sanitize = utils[utils.index('    public static String[] replaceInvalidLinks('):utils.index('    public static Activity getActivityFromView')]
STUBS = {
 'android/annotation/SuppressLint.java':'package android.annotation; public @interface SuppressLint { String[] value(); }',
 'androidx/annotation/NonNull.java':'package androidx.annotation; public @interface NonNull {}',
 'android/util/Log.java':'package android.util; public class Log {}',
 'moe/ono/BuildConfig.java':'package moe.ono; public class BuildConfig {}',
 'moe/ono/hooks/_core/annotation/HookItem.java':'package moe.ono.hooks._core.annotation; public @interface HookItem {String path();String description();}',
 'moe/ono/constants/Constants.java':'package moe.ono.constants; public class Constants {public static final String MethodCacheKey_MarkdownAIO="markdown";}',
 'moe/ono/util/Logger.java':'package moe.ono.util; public class Logger {public static void d(String s){} public static void e(String s,Object e){}}',
 'moe/ono/config/ConfigManager.java':'''package moe.ono.config; import moe.ono.dexkit.TargetManager; public class ConfigManager {
 public static ConfigManager getCache(){return new ConfigManager();}
 public String getString(String k,String d){return TargetManager.selected.getDeclaringClass().getName()+"#"+TargetManager.selected.getName();}
 }''',
 'moe/ono/dexkit/TargetManager.java':'''package moe.ono.dexkit; import java.lang.reflect.*; public class TargetManager {
 public static Method selected; public static Method requireMethod(String k){return selected;}
 }''',
 'moe/ono/util/Utils.java':'''package moe.ono.util; import java.lang.reflect.*; import java.util.regex.*;
 public class Utils {
 public static Method findMethodByName(Class<?> c,String n){for(Method m:c.getDeclaredMethods())if(m.getName().equals(n))return m;throw new IllegalArgumentException();}
 '''+sanitize+'}',
 'com/tencent/qqnt/kernel/nativeinterface/MarkdownElement.java':'''package com.tencent.qqnt.kernel.nativeinterface;
 public class MarkdownElement {private String content; public String extra="keep"; public MarkdownElement(String s){content=s;}public String getContent(){return content;}}
 ''',
 'moe/ono/hooks/_base/BaseSwitchFunctionHookItem.java':'''package moe.ono.hooks._base; import java.lang.reflect.*;
 public abstract class BaseSwitchFunctionHookItem {
 public static class Param { public Object[] args; public Object thisObject; public Param(Object o,Object... a){thisObject=o;args=a;}}
 public interface Action {void run(Param p)throws Throwable;} public static Action action;
 public void hookBefore(Method m,Action a){action=a;}
 public abstract void entry(ClassLoader c)throws Throwable;
 }''',
 'moe/ono/hooks/item/bugfix/MarkdownGuard.java':source,
 'MarkdownGuardRegression.java':'''import java.lang.reflect.*;
 import com.tencent.qqnt.kernel.nativeinterface.MarkdownElement;
 import moe.ono.dexkit.TargetManager; import moe.ono.hooks.item.bugfix.MarkdownGuard;
 import moe.ono.hooks._base.BaseSwitchFunctionHookItem;
 public class MarkdownGuardRegression {
  public static class Modern {private final MarkdownElement payload; public Modern(MarkdownElement e){payload=e;} public void parse(){}}
  public static class Legacy {public void bind(MarkdownElement e,Object o){}}
  public static class Ambiguous {private MarkdownElement one,two; public void parse(){}}
  static int pass,fail;
  interface Test {void run()throws Throwable;}
  static void test(String name,Test t){BaseSwitchFunctionHookItem.action=null;try{t.run();pass++;System.out.println("PASS "+name);}catch(Throwable e){fail++;System.out.println("FAIL "+name+": "+e);}}
  static void expect(boolean b){if(!b)throw new AssertionError();}
  static void install(Class<?> c,String n,Class<?>...p)throws Exception{TargetManager.selected=c.getDeclaredMethod(n,p);try{new MarkdownGuard().entry(MarkdownGuardRegression.class.getClassLoader());}catch(Throwable t){throw new RuntimeException(t);}}
  static void invoke(Object o,Object...args)throws Throwable{expect(BaseSwitchFunctionHookItem.action!=null);BaseSwitchFunctionHookItem.action.run(new BaseSwitchFunctionHookItem.Param(o,args));}
  public static void main(String[] args)throws Exception {
   test("modern parse sanitizes element before rendering",()->{MarkdownElement e=new MarkdownElement("![x](https://example.com/a.png)");Modern m=new Modern(e);install(Modern.class,"parse");invoke(m);expect(!e.getContent().contains("https://example.com"));expect(e.extra.equals("keep"));});
   test("legacy argument keeps element identity and metadata",()->{MarkdownElement e=new MarkdownElement("![x](https://example.com/a.png)");install(Legacy.class,"bind",MarkdownElement.class,Object.class);BaseSwitchFunctionHookItem.Param p=new BaseSwitchFunctionHookItem.Param(new Legacy(),e,new Object());BaseSwitchFunctionHookItem.action.run(p);expect(p.args[0]==e);expect(!e.getContent().contains("https://example.com"));expect(e.extra.equals("keep"));});
   test("official image URL remains unchanged",()->{String text="![x](https://qqbot.ugcimg.cn/a.png)";MarkdownElement e=new MarkdownElement(text);install(Modern.class,"parse");invoke(new Modern(e));expect(text.equals(e.getContent()));});
   test("repeated parse does not duplicate notice",()->{MarkdownElement e=new MarkdownElement("![x](https://example.com/a.png)");Modern m=new Modern(e);install(Modern.class,"parse");invoke(m);String once=e.getContent();invoke(m);expect(once.equals(e.getContent()));});
   test("null element does not throw",()->{install(Modern.class,"parse");invoke(new Modern(null));});
   test("ambiguous element fields do not install unsafe hook",()->{install(Ambiguous.class,"parse");expect(BaseSwitchFunctionHookItem.action==null);});
   System.out.println("RESULT "+pass+" passed, "+fail+" failed");if(fail>0)System.exit(1);
  }
 }''',
}
def main():
    for name, content in STUBS.items():
     path=BASE/'src'/name
     path.parent.mkdir(parents=True,exist_ok=True)
     path.write_text(content,encoding='utf-8')
    classes=BASE/'classes'
    classes.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(JAVA/('javac'+EXE)),'-encoding','UTF-8','-Xlint:unchecked,deprecation','-d',str(classes),*map(str,(BASE/'src').rglob('*.java'))],check=True)
    return subprocess.run([str(JAVA/('java'+EXE)),'-cp',str(classes),'MarkdownGuardRegression']).returncode


if __name__ == '__main__':
    raise SystemExit(main())
