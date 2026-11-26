/*
 * XC_MethodReplacement implementation that forwards to a Python callback, following exteraGram's
 * PyMethodReplacement (GPLv3 or later, additional terms in TMessagesProj/src/main/python/EXTERAGRAM_TERMS.md).
 */

package tw.nekomimi.nekogram.plugins.hooks;

import com.chaquo.python.PyObject;

import org.telegram.messenger.FileLog;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;

public final class PythonMethodReplacement extends XC_MethodReplacement {

    private final String pluginId;
    private final PyObject callback;
    private volatile boolean disabled;

    public PythonMethodReplacement(String pluginId, PyObject callback) {
        super();
        this.pluginId = pluginId;
        this.callback = callback;
    }

    public PythonMethodReplacement(String pluginId, PyObject callback, int priority) {
        super(priority);
        this.pluginId = pluginId;
        this.callback = callback;
    }

    @Override
    public Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) {
        if (disabled || !HookBridge.isPluginActive(pluginId)) {
            return HookBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
        }
        try {
            return callback.call(param).toJava(Object.class);
        } catch (Throwable error) {
            FileLog.e("Plugin '" + pluginId + "' crashed in replace_hooked_method", error);
            return null;
        }
    }

    public void close() {
        disabled = true;
    }
}
