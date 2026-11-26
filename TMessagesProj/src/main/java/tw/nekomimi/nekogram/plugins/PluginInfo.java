/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Immutable plugin metadata, parsed from the {@code plugin.json} file inside a plugin archive.
 * <pre>
 * {
 *   "id": "com.example.hello",          // required, unique
 *   "name": "Hello world",              // required
 *   "entrypoint": "com.example.hello.HelloPlugin", // required, must extend BasePlugin
 *   "version": "1.0",                   // optional
 *   "author": "Someone",                // optional
 *   "description": "Does something",    // optional
 *   "apiVersion": 1,                    // optional, defaults to 1
 *   "minAppVersionCode": 7175           // optional
 * }
 * </pre>
 */
public final class PluginInfo {

    private static final String DEFAULT_VERSION = "1.0";

    private final String id;
    private final String name;
    private final String version;
    private final String author;
    private final String description;
    private final String entrypoint;
    private final int apiVersion;
    private final int minAppVersionCode;

    private PluginInfo(String id, String name, String version, String author, String description,
                       String entrypoint, int apiVersion, int minAppVersionCode) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        this.entrypoint = entrypoint;
        this.apiVersion = apiVersion;
        this.minAppVersionCode = minAppVersionCode;
    }

    public static PluginInfo parse(String json) throws PluginException {
        final JSONObject object;
        try {
            object = new JSONObject(json);
        } catch (JSONException e) {
            throw new PluginException("plugin.json is not a valid JSON object", e);
        }
        return parse(object);
    }

    public static PluginInfo parse(JSONObject object) throws PluginException {
        final String id = trimToNull(object.optString("id", null));
        final String name = trimToNull(object.optString("name", null));
        final String entrypoint = trimToNull(object.optString("entrypoint", null));

        if (id == null) {
            throw new PluginException("plugin.json: \"id\" is missing");
        }
        if (!isValidId(id)) {
            throw new PluginException("plugin.json: invalid \"id\": " + id);
        }
        if (name == null) {
            throw new PluginException("plugin.json: \"name\" is missing");
        }
        if (entrypoint == null) {
            throw new PluginException("plugin.json: \"entrypoint\" is missing");
        }
        if (!isValidClassName(entrypoint)) {
            throw new PluginException("plugin.json: invalid \"entrypoint\": " + entrypoint);
        }

        int apiVersion = object.optInt("apiVersion", PluginsConstants.API_VERSION);
        if (apiVersion <= 0) {
            apiVersion = PluginsConstants.API_VERSION;
        }
        if (apiVersion > PluginsConstants.API_VERSION) {
            throw new PluginException(String.format(Locale.US,
                    "Plugin requires API %d, this build provides API %d", apiVersion, PluginsConstants.API_VERSION));
        }

        final int minAppVersionCode = object.optInt("minAppVersionCode", 0);

        String version = trimToNull(object.optString("version", null));
        if (version == null) {
            version = DEFAULT_VERSION;
        }
        String author = trimToNull(object.optString("author", null));
        if (author == null) {
            author = "";
        }
        String description = trimToNull(object.optString("description", null));
        if (description == null) {
            description = "";
        }

        return new PluginInfo(id, name, version, author, description, entrypoint, apiVersion, minAppVersionCode);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private static boolean isValidId(String id) {
        if (id.length() > 64) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            final char c = id.charAt(i);
            final boolean valid = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-';
            if (!valid) {
                return false;
            }
        }
        final char first = id.charAt(0);
        return (first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z') || (first >= '0' && first <= '9');
    }

    private static boolean isValidClassName(String name) {
        if (name.length() > 255) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            final boolean valid = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '$';
            if (!valid) {
                return false;
            }
        }
        return name.indexOf('.') > 0;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public String getEntrypoint() {
        return entrypoint;
    }

    public int getApiVersion() {
        return apiVersion;
    }

    public int getMinAppVersionCode() {
        return minAppVersionCode;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PluginInfo)) {
            return false;
        }
        return id.equals(((PluginInfo) object).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "PluginInfo{" + id + " " + version + "}";
    }
}
