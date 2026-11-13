# 保留自定义 View，XML 中按类名实例化，混淆后必须保留
-keep public class com.mocitianxia.ai.LockPatternView { *; }

-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# 保留 JavaScript 接口
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 保留四大组件
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver