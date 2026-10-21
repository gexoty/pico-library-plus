package com.gexoty.picolibrary;

/** Shared by all backends. No shell metacharacters or cross-user target input. */
public final class StopPolicy {
    private StopPolicy() {}
    public static boolean validPackage(String name) {
        return name != null && name.length() <= 255
            && name.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");
    }
    public static boolean mayStop(String name, String self) {
        return validPackage(name) && !name.equals(self) && !name.equals("android")
            && !name.equals("com.android.systemui") && !name.equals("com.picovr.vrshell")
            && !name.equals("com.pvr.vrshell") && !name.equals("moe.shizuku.privileged.api")
            && !name.equals("com.topjohnwu.magisk");
    }
}
