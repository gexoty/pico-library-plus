import com.gexoty.picolibrary.RemovalPolicy;
public class RemovalPolicyTest {
    private static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        check(RemovalPolicy.allowed("com.pico4.settings","com.pvr.appmanager"));
        check(RemovalPolicy.allowed("com.example.app","com.gexoty.picolibrary"));
        for(String name:new String[]{null,"", "com.example;reboot","com.example\napp","com.pvr.appmanager","com.gexoty.picolibrary","android","com.android.systemui","com.android.packageinstaller","com.android.permissioncontroller","com.picovr.store","com.topjohnwu.magisk"})
            check(!RemovalPolicy.allowed(name,"com.gexoty.picolibrary"));
        System.out.println("RemovalPolicyTest passed");
    }
}
