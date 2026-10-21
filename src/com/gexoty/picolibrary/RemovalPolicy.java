package com.gexoty.picolibrary;

/** Only validated, non-core package names may be sent to the system uninstaller. */
public final class RemovalPolicy {
    private RemovalPolicy() {}
    public static boolean allowed(String pkg,String self) {
        return StopPolicy.mayStop(pkg,self)
            && !"com.pvr.appmanager".equals(pkg) && !"com.gexoty.picolibrary".equals(pkg)
            && !"com.android.packageinstaller".equals(pkg)
            && !"com.android.permissioncontroller".equals(pkg)
            && !"com.picovr.store".equals(pkg);
    }
}
