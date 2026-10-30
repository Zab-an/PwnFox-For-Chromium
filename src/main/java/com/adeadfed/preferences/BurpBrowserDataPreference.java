package com.adeadfed.preferences;

import com.adeadfed.browser.Browser;

import java.nio.file.Files;
import java.nio.file.Path;

public class BurpBrowserDataPreference extends Preference {
    private final String persistentKey = "PWNCHROMIUM_BURP_BROWSER_DATA_DIR";

    @Override
    public String getPersistentKey() {
        return persistentKey;
    }

    @Override
    public String getDefault() {
        Path burpBrowserDataPath = Browser.BURP_USER_DIR.resolve("pre-wired-browser");
        return Files.isDirectory(burpBrowserDataPath) ? burpBrowserDataPath.toString() : null;
    }
}
