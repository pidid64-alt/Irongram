/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins;

import android.content.SharedPreferences;

import java.util.Map;

/**
 * Typed wrapper around the {@link SharedPreferences} file that belongs to a single plugin.
 * All values are stored in the app private storage, so they are removed together with the plugin.
 */
public final class PluginPreferences {

    private final SharedPreferences preferences;

    PluginPreferences(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return preferences.getBoolean(key, defaultValue);
    }

    public void putBoolean(String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    public int getInt(String key, int defaultValue) {
        return preferences.getInt(key, defaultValue);
    }

    public void putInt(String key, int value) {
        preferences.edit().putInt(key, value).apply();
    }

    public long getLong(String key, long defaultValue) {
        return preferences.getLong(key, defaultValue);
    }

    public void putLong(String key, long value) {
        preferences.edit().putLong(key, value).apply();
    }

    public float getFloat(String key, float defaultValue) {
        return preferences.getFloat(key, defaultValue);
    }

    public void putFloat(String key, float value) {
        preferences.edit().putFloat(key, value).apply();
    }

    public String getString(String key, String defaultValue) {
        return preferences.getString(key, defaultValue);
    }

    public void putString(String key, String value) {
        preferences.edit().putString(key, value).apply();
    }

    public boolean contains(String key) {
        return preferences.contains(key);
    }

    public void remove(String key) {
        preferences.edit().remove(key).apply();
    }

    public Map<String, ?> getAll() {
        return preferences.getAll();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }

    public SharedPreferences getRaw() {
        return preferences;
    }
}
