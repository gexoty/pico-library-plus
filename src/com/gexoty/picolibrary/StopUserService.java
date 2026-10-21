package com.gexoty.picolibrary;

import android.os.Binder;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.concurrent.TimeUnit;

/** Shizuku UserService, loaded by Shizuku; deliberately not an exported Android service. */
public final class StopUserService extends Binder {
    public static final String DESCRIPTOR = "com.gexoty.picolibrary.StopUserService";
    public StopUserService() { attachInterface(null, DESCRIPTOR); }
    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code == INTERFACE_TRANSACTION) { reply.writeString(DESCRIPTOR); return true; }
        if (code != 1) return super.onTransact(code, data, reply, flags);
        data.enforceInterface(DESCRIPTOR);
        String pkg = data.readString(); int user = data.readInt();
        if ((!StopPolicy.mayStop(pkg, "com.gexoty.picolibrary") || !StopPolicy.mayStop(pkg, "com.pvr.appmanager")) || user < 0)
            throw new SecurityException("Invalid force-stop target");
        try {
            Process p = new ProcessBuilder("/system/bin/am", "force-stop", "--user",
                Integer.toString(user), pkg).redirectErrorStream(true).start();
            if (!p.waitFor(8, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new Exception("am timeout"); }
            byte[] output = new byte[8192]; int n=p.getInputStream().read(output);
            String text=n>0?new String(output,0,n,java.nio.charset.StandardCharsets.UTF_8):"";
            if(p.exitValue()!=0 || !text.trim().isEmpty()) throw new Exception("am: "+text);
            reply.writeNoException(); reply.writeString("Команда force-stop выполнена: " + pkg);
        } catch (Exception e) { reply.writeException(new IllegalStateException(e)); }
        return true;
    }
}
