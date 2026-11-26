/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins;

/**
 * Thrown when a plugin archive is malformed, incompatible with the current app build or when
 * a plugin cannot be loaded. The message is user facing, so it must stay short and readable.
 */
public class PluginException extends Exception {

    public PluginException(String message) {
        super(message);
    }

    public PluginException(String message, Throwable cause) {
        super(message, cause);
    }
}
