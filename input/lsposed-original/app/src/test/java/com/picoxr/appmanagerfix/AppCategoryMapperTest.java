package com.picoxr.appmanagerfix;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AppCategoryMapperTest {
    @Test
    public void movesOnlyTheAppManagerUnknownSdkCategory() {
        assertTrue(AppCategoryMapper.shouldMoveToApps("other", "sdk_app"));
        assertFalse(AppCategoryMapper.shouldMoveToApps("store", "sdk_app"));
        assertFalse(AppCategoryMapper.shouldMoveToApps("other", "2d_app"));
        assertFalse(AppCategoryMapper.shouldMoveToApps("other", "pre_app"));
    }
}
