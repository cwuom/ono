from pathlib import Path
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
BASE = Path(__file__).parent / 'generated' / 'cache-regression'
JAVA = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else Path(shutil.which('javac') or 'C:/Program Files/Android/Android Studio/jbr/bin/javac.exe').parent
EXE = '.exe' if os.name == 'nt' else ''
STUBS = {
    'androidx/annotation/NonNull.java': 'package androidx.annotation; public @interface NonNull {}',
    'android/app/Activity.java': 'package android.app; public class Activity { public void runOnUiThread(Runnable r) { r.run(); } }',
    'android/content/pm/ApplicationInfo.java': 'package android.content.pm; public class ApplicationInfo { public String sourceDir; }',
    'android/content/SharedPreferences.java': 'package android.content; public interface SharedPreferences { interface Editor { Editor remove(String k); void apply(); } }',
    'moe/ono/config/ConfigManager.java': '''package moe.ono.config;
import java.util.*; import android.content.SharedPreferences;
public class ConfigManager implements SharedPreferences.Editor {
 public static final Map<String,Object> data = new HashMap<>();
 private static final ConfigManager INSTANCE = new ConfigManager();
 public static ConfigManager getCache(){return INSTANCE;} public ConfigManager edit(){return this;}
 public Map<String,Object> getAll(){return new HashMap<>(data);}
 public ConfigManager remove(String k){data.remove(k);return this;} public void apply(){}
 public static boolean cGetBoolean(String k,boolean d){return (boolean)data.getOrDefault(k,d);}
 public static String cGetString(String k,String d){return (String)data.getOrDefault(k,d);}
 public static int cGetInt(String k,int d){return (int)data.getOrDefault(k,d);}
 public static void cPutBoolean(String k,boolean v){data.put(k,v);}
 public static void cPutString(String k,String v){data.put(k,v);}
 public static void cPutInt(String k,int v){data.put(k,v);}
}''',
    'moe/ono/util/Logger.java': 'package moe.ono.util; public class Logger { public static void e(String s,Throwable t){} }',
    'moe/ono/util/Initiator.java': '''package moe.ono.util; public class Initiator {
 public static ClassLoader getHostClassLoader(){return Initiator.class.getClassLoader();}
 public static Class<?> loadClass(String s) throws ClassNotFoundException{return Class.forName(s);}
}''',
    'moe/ono/util/Utils.java': '''package moe.ono.util; import java.lang.reflect.*; public class Utils {
 public static Method findMethodByName(Class<?> c,String n){for(Method m:c.getDeclaredMethods())if(m.getName().equals(n))return m;throw new IllegalArgumentException();}
}''',
    'org/luckypray/dexkit/query/matchers/MethodMatcher.java': '''package org.luckypray.dexkit.query.matchers; public class MethodMatcher {
 public static MethodMatcher create(){return new MethodMatcher();}
 public MethodMatcher usingStrings(String... v){return this;} public MethodMatcher paramCount(int n){return this;}
 public MethodMatcher declaredClass(String v){return this;} public MethodMatcher returnType(String v){return this;}
 public MethodMatcher paramTypes(String... v){return this;}
}''',
    'org/luckypray/dexkit/query/matchers/ClassMatcher.java': '''package org.luckypray.dexkit.query.matchers; public class ClassMatcher {
 public static ClassMatcher create(){return new ClassMatcher();} public ClassMatcher usingStrings(String... s){return this;}
}''',
    'org/luckypray/dexkit/query/FindMethod.java': '''package org.luckypray.dexkit.query; import org.luckypray.dexkit.query.matchers.MethodMatcher;
public class FindMethod {public static FindMethod create(){return new FindMethod();} public FindMethod matcher(MethodMatcher m){return this;} public FindMethod searchPackages(String... p){return this;}}''',
    'org/luckypray/dexkit/query/FindClass.java': '''package org.luckypray.dexkit.query; import org.luckypray.dexkit.query.matchers.ClassMatcher;
public class FindClass {public static FindClass create(){return new FindClass();} public FindClass searchPackages(String p){return this;} public FindClass matcher(ClassMatcher m){return this;}}''',
    'org/luckypray/dexkit/result/MethodData.java': '''package org.luckypray.dexkit.result; import java.lang.reflect.*;
public class MethodData {public Method getMethodInstance(ClassLoader c) throws Exception {return String.class.getMethod("length");}}''',
    'org/luckypray/dexkit/result/ClassData.java': 'package org.luckypray.dexkit.result; public class ClassData {public Class<?> getInstance(ClassLoader c){return String.class;}}',
    'org/luckypray/dexkit/DexKitBridge.java': '''package org.luckypray.dexkit;
import java.util.function.*; import org.luckypray.dexkit.query.*; import org.luckypray.dexkit.result.*;
public class DexKitBridge implements AutoCloseable {
 public static boolean failCreate; public static DexKitBridge create(String p){if(failCreate)throw new IllegalStateException("unavailable");return new DexKitBridge();} public void close(){}
 public static boolean failQuery; public Methods findMethod(FindMethod f){if(failQuery)throw new UnsatisfiedLinkError("unavailable");return new Methods();} public Classes findClass(FindClass f){return new Classes();}
 public static class Methods {public MethodData single(){throw new IllegalStateException("no result");}}
 public static class Classes {public ClassData singleOrThrow(Supplier<? extends RuntimeException> s){throw s.get();}}
}''',
    'moe/ono/dexkit/TargetManagerRegression.java': '''package moe.ono.dexkit;
import java.lang.reflect.*; import java.util.concurrent.*;
import moe.ono.config.ConfigManager; import moe.ono.constants.Constants;
import org.luckypray.dexkit.*; import org.luckypray.dexkit.query.matchers.*;
public class TargetManagerRegression {
 public static class Overloads { public void bind(String s){} public void bind(int i){} }
 static int passed,failed;
 static void check(String name,Runnable r){ConfigManager.data.clear();try{r.run();passed++;System.out.println("PASS "+name);}catch(Throwable t){failed++;System.out.println("FAIL "+name+": "+t);}}
 static void expect(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args)throws Exception {
  check("first run requires scan",()->expect(TargetManager.isNeedFindTarget()));
  check("old cache schema forces rescan",()->{ConfigManager.cPutBoolean("isNeedFindTarget",false);expect(TargetManager.isNeedFindTarget());});
  check("completed current scan stays completed",()->{TargetManager.setIsNeedFindTarget(false);expect(!TargetManager.isNeedFindTarget());});
  check("explicit rescan stays requested",()->{TargetManager.setIsNeedFindTarget(false);TargetManager.setIsNeedFindTarget(true);expect(TargetManager.isNeedFindTarget());});
  check("full signature selects exact overload",()->{try{Method want=Overloads.class.getMethod("bind",int.class);ConfigManager.cPutString("method_test",new DexMethodDescriptor(want).getDescriptor());expect(want.equals(TargetManager.requireMethod("method_test")));}catch(Exception e){throw new RuntimeException(e);}});
  check("legacy unique method remains readable",()->{ConfigManager.cPutString("method_test","java.lang.String#length");expect(TargetManager.requireMethod("method_test").getName().equals("length"));});
  check("missing cache returns null",()->expect(TargetManager.requireMethod("method_missing")==null));
  check("failed scan clears stale method and reports failure",()->{try{
   ConfigManager.cPutString("method_test","stale#bind");StringBuilder log=new StringBuilder();
   Method f=TargetManager.class.getDeclaredMethod("findAndCache",DexKitBridge.class,ClassLoader.class,MethodMatcher.class,String.class,StringBuilder.class);f.setAccessible(true);
   f.invoke(null,new DexKitBridge(),TargetManagerRegression.class.getClassLoader(),MethodMatcher.create(),"method_test",log);
   expect(!ConfigManager.data.containsKey("method_test"));expect(log.toString().contains("method_test"));
  }catch(Exception e){throw new RuntimeException(e);}});
  check("failed targets appear in finder report",()->{try{
   CountDownLatch done=new CountDownLatch(1);String[] result={null};
   TargetManager.runMethodFinder(new android.content.pm.ApplicationInfo(),TargetManagerRegression.class.getClassLoader(),new android.app.Activity(),(s,ok)->{result[0]=s;done.countDown();});
   expect(done.await(5,TimeUnit.SECONDS));expect(result[0].contains(Constants.MethodCacheKey_ChatPanelBtn));
  }catch(Exception e){throw new RuntimeException(e);}});
  check("global scanner failure reports incomplete",()->{try{
   DexKitBridge.failCreate=true;CountDownLatch done=new CountDownLatch(1);boolean[] complete={true};
   TargetManager.runMethodFinder(new android.content.pm.ApplicationInfo(),TargetManagerRegression.class.getClassLoader(),new android.app.Activity(),(s,ok)->{complete[0]=ok;done.countDown();});
   expect(done.await(5,TimeUnit.SECONDS));expect(!complete[0]);
  }catch(Exception e){throw new RuntimeException(e);}finally{DexKitBridge.failCreate=false;}});
  check("query linkage failure reports incomplete",()->{try{
   DexKitBridge.failQuery=true;CountDownLatch done=new CountDownLatch(1);boolean[] complete={true};
   TargetManager.runMethodFinder(new android.content.pm.ApplicationInfo(),TargetManagerRegression.class.getClassLoader(),new android.app.Activity(),(s,ok)->{complete[0]=ok;done.countDown();});
   expect(done.await(5,TimeUnit.SECONDS));expect(!complete[0]);
  }catch(Exception e){throw new RuntimeException(e);}finally{DexKitBridge.failQuery=false;}});
  System.out.println("RESULT "+passed+" passed, "+failed+" failed");if(failed>0)System.exit(1);
 }
}''',
}
def main():
    for name, content in STUBS.items():
        path = BASE / 'src' / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8')
    sources = list((BASE / 'src').rglob('*.java')) + [
        ROOT / 'app/src/main/java/moe/ono/dexkit/TargetManager.java',
        ROOT / 'app/src/main/java/moe/ono/dexkit/DexMethodDescriptor.java',
        ROOT / 'app/src/main/java/moe/ono/dexkit/DexKitExecutor.java',
        ROOT / 'app/src/main/java/moe/ono/dexkit/DexKitTask.java',
        ROOT / 'app/src/main/java/moe/ono/constants/Constants.java',
    ]
    classes = BASE / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(JAVA/('javac'+EXE)), '-encoding', 'UTF-8', '-Xlint:unchecked,deprecation', '-d', str(classes), *map(str,sources)],check=True)
    return subprocess.run([str(JAVA/('java'+EXE)),'-cp',str(classes),'moe.ono.dexkit.TargetManagerRegression']).returncode


if __name__ == '__main__':
    raise SystemExit(main())
