package com.picoxr.appmanagerfix;

final class AppCategoryMapper {
    static final String OTHER = "other";
    static final String SDK_APP = "sdk_app";
    static final String NORMAL_APP = "2d_app";

    private AppCategoryMapper() {
    }

    static boolean shouldMoveToApps(String source, String subSource) {
        return OTHER.equals(source) && SDK_APP.equals(subSource);
    }
}
