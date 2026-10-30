package com.adeadfed.preferences;

import burp.api.montoya.persistence.Preferences;

public class PwnFoxForChromiumPreferences {
    private Preferences montoyaPreferences;
    public BrowserPathPreference browserPath;
    public ProfilesDirPreference profilesDir;
    public ProfileNamesPreference profileNames;
    public BurpBrowserDataPreference burpBrowserData;

    public PwnFoxForChromiumPreferences(Preferences montoyaPreferences) throws Exception {
        this.montoyaPreferences = montoyaPreferences;
        this.browserPath = new BrowserPathPreference();
        this.profilesDir = new ProfilesDirPreference();
        this.profileNames = new ProfileNamesPreference();
        this.burpBrowserData = new BurpBrowserDataPreference();

        initDefaultPreferences();
    }

    private void initDefaultPreferences() throws Exception {
        if (!preferenceExists(browserPath.getPersistentKey())) {
            String defaultBrowserPath = browserPath.getDefault();
            if (defaultBrowserPath != null) {
                set(browserPath, defaultBrowserPath);
            }
        }
        if (!preferenceExists(profilesDir.getPersistentKey())) {
            set(profilesDir, profilesDir.getDefault());
        }
        if (!preferenceExists(burpBrowserData.getPersistentKey())) {
            String defaultBurpBrowserData = burpBrowserData.getDefault();
            if (defaultBurpBrowserData != null) {
                set(burpBrowserData, defaultBurpBrowserData);
            }
        }
    }

    public String get(Preference preference) {
        return montoyaPreferences.getString(preference.getPersistentKey());
    }

    public void set(Preference preference, String value) {
        montoyaPreferences.setString(preference.getPersistentKey(), value);
    }

    public String getProfileName(String color) {
        String profileNameKey = profileNames.getPersistentKey(color);
        if (preferenceExists(profileNameKey)) {
            return montoyaPreferences.getString(profileNameKey);
        }
        return profileNames.getDefault(color);
    }

    public void setProfileName(String color, String value) {
        String profileNameKey = profileNames.getPersistentKey(color);
        montoyaPreferences.setString(profileNameKey, value);
    }

    private boolean preferenceExists(String settingName) {
        return montoyaPreferences.getString(settingName) != null;
    }
}
