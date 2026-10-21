import java.util.*;
import com.gexoty.picolibrary.CatalogRules;
public final class CatalogRulesTest {
    public static void main(String[] args) {
        for(String name:new String[]{null,"","   ","a\nb",String.join("",Collections.nCopies(41,"я"))})
            if(CatalogRules.validGroupName(name))throw new AssertionError("Invalid group name accepted");
        if(!CatalogRules.validGroupName("Мои VR-приложения"))throw new AssertionError("Unicode group rejected");
        Set<String> hidden=new HashSet<>(Arrays.asList("com.hidden.app"));
        Set<String> group=new HashSet<>(Arrays.asList("com.hidden.app","com.visible.app"));
        if(CatalogRules.visible("com.hidden.app",hidden,group))throw new AssertionError("Group bypassed hidden state");
        if(!CatalogRules.visible("com.visible.app",hidden,group))throw new AssertionError("Group member missing");
        if(CatalogRules.visible("com.other.app",hidden,group))throw new AssertionError("Nonmember in group");
        if(!CatalogRules.visible("com.other.app",hidden,null))throw new AssertionError("All-apps incorrectly restricted");
        if(CatalogRules.visible("com.visible.app",hidden,Collections.emptySet()))throw new AssertionError("Empty group leaked apps");
        System.out.println("CatalogRulesTest: passed");
    }
}
