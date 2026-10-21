import com.gexoty.picolibrary.StopPolicy;
public final class StopPolicyTest {
    public static void main(String[] args) {
        String self="com.gexoty.picolibrary";
        for(String name:new String[]{"",null,"a","-x.y","com.foo;reboot","com.foo\nreboot","com.foo $(id)","com.foo'","com.foo/Activity","com..foo"})
            if(StopPolicy.validPackage(name))throw new AssertionError("Accepted shell/package input: "+name);
        for(String name:new String[]{self,"android","com.android.systemui","com.picovr.vrshell","moe.shizuku.privileged.api"})
            if(StopPolicy.mayStop(name,self))throw new AssertionError("Allowed protected target: "+name);
        for(String name:new String[]{"com.example.app","com.Example_2.game","com.pvr.appmanager"})
            if(!StopPolicy.mayStop(name,self))throw new AssertionError("Rejected valid target: "+name);
        System.out.println("StopPolicyTest: passed");
    }
}
