package com.scarfaceos.pocketshark;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Stores apps that should retain their normal direct connection during capture. */
public final class AppBypassStore {
    private static final String FILE_NAME = "pocketshark_settings";
    private static final String KEY_PACKAGES = "bypassed_packages";

    private AppBypassStore() {}

    public static Set<String> get(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        Set<String> saved = preferences.getStringSet(KEY_PACKAGES, Collections.emptySet());
        return saved == null ? new HashSet<>() : new HashSet<>(saved);
    }

    public static void set(Context context, Set<String> packages) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_PACKAGES, new HashSet<>(packages))
                .apply();
    }
}
