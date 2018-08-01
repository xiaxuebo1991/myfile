# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in d:\android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

#-keep class com.jboss.netty.**{*;}
#-keep class android.support.**{*;}
#-keep class javax.lang.model.element.VariableElement
#-keep class butterknife.internal.ButterKnifeProcessor
#-keep class org.apache.** {*;}
#-dontwarn butterknife.**
#指定代码的压缩级别
-optimizationpasses 5
#包明不混合大小写
-dontusemixedcaseclassnames
#不去忽略非公共的库类
-dontskipnonpubliclibraryclasses
#优化 不优化输入的类文件
#-dontoptimize
#-dontshrink
#预校验
-dontpreverify
#混淆时是否记录日志
-verbose
#混淆时所采用的算法
-optimizations !code/simplification/arithmetic,!field/,!class/merging/
#保护注解
-keepattributes Annotation

#忽略警告
#-ignorewarning
#记录生成的日志数据,gradle build时在本项目根目录输出
#apk 包内所有 class 的内部结构
-dump class_files.txt
#未混淆的类和成员
-printseeds seeds.txt
#列出从 apk 中删除的代码
-printusage unused.txt
#混淆前后的映射
-printmapping mapping.txt

#-dontwarn butterknife.internal.**
#-keep class **$$ViewInjector { *; }
#-keepnames class * { @butterknife.InjectView *;}
#-dontwarn butterknife.Views$InjectViewProcessor
#-dontwarn com.gc.materialdesign.views.**


#-keep class butterknife.** { *; }
#-dontwarn butterknife.internal.**
#-keep class **$$ViewBinder { *; }
#-keepclasseswithmembernames class * {
#    @butterknife.* <fields>;
#}
#-keepclasseswithmembernames class * {
#    @butterknife.* <methods>;
#}

-keep class com.google.** { *; }
-dontwarn com.google.**

-keep class com.squareup.wire.** { *; }
-dontwarn com.squareup.wire.**

-keep class javax.** { *; }
-dontwarn javax.**

-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses,EnclosingMethod

-keep class org.apache.** { *; }
-dontwarn org.apache.**

-keep class org.** { *; }
-dontwarn org.**

-dontwarn kotlin.**
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}

-keep class com.wws.** { *; }
-dontwarn com.wws.**

-keep class com.ucloudlink.base.Framework {
 public static com.ucloudlink.base.Framework INSTANCE;
 public final void environmentInit(android.content.Context);
 public final com.ucloudlink.framework.access.AccessManager getAccessManager();
 public final com.ucloudlink.framework.com.ucloudlink.access.AccessManager getAccessManager();
}
-dontwarn com.ucloudlink.base.Framework

-keep class com.ucloudlink.framework.access.AccessManager {
    public final void startSession(java.lang.String,java.lang.String);
    public final void stopSession();
}

-keep class com.ucloudlink.framework.com.ucloudlink.access.AccessManager {
    public final void startSession(java.lang.String,java.lang.String);
    public final void stopSession();
}

#-keep class com.ucloudlink.remotesimser.UimRemoteClient { *; }
#-dontwarn com.ucloudlink.remotesimser.UimRemoteClient

#-keep class com.ucloudlink.remotesimser.UimRemoteClient$* {*;}
#-dontwarn com.ucloudlink.remotesimser.UimRemoteClient$*

#-keep interface com.ucloudlink.remotesimser.IUimRemoteClientService.** { *; }
#-dontwarn com.ucloudlink.remotesimser.IUimRemoteClientService.**

#xlog相关
-keep class com.tencent.mars.** {
  public protected private *;
}