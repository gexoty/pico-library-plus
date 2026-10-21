package com.gexoty.picolibrary;

import java.util.Set;

/** Local presentation choices never change Android package state. */
public final class CatalogRules {
    private CatalogRules() {}
    public static boolean validGroupName(String name) {
        if(name==null || name.trim().isEmpty() || name.trim().length()>40)return false;
        for(int i=0;i<name.length();i++)if(Character.isISOControl(name.charAt(i)))return false;
        return true;
    }
    public static boolean visible(String pkg,Set<String> hidden,Set<String> members) {
        return pkg!=null && !hidden.contains(pkg) && (members==null || members.contains(pkg));
    }
}
