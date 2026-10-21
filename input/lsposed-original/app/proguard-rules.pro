-keep class com.picoxr.appmanagerfix.AppManagerLibraryFix implements de.robv.android.xposed.IXposedHookLoadPackage {
    <init>();
    void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam);
}
-dontwarn de.robv.android.xposed.**
