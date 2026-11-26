/*
 * XC_MethodHook implementation that forwards to Python callbacks, following exteraGram's
 * PyMethodHook (GPLv3 or later, additional terms in TMessagesProj/src/main/python/EXTERAGRAM_TERMS.md).
 */

package tw.nekomimi.nekogram.plugins.hooks;

import com.chaquo.python.PyObject;

import org.telegram.messenger.FileLog;

import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;

public final class PythonMethodHook extends XC_MethodHook {

    private final String pluginId;
    private final PyObject before;
    private final PyObject after;
    private ArrayList<PythonHookFilter> beforeFilters;
    private ArrayList<PythonHookFilter> afterFilters;
    private volatile boolean disabled;

    public PythonMethodHook(String pluginId, PyObject before, PyObject after) {
        super();
        this.pluginId = pluginId;
        this.before = before;
        this.after = after;
    }

    public PythonMethodHook(String pluginId, PyObject before, PyObject after, int priority) {
        super(priority);
        this.pluginId = pluginId;
        this.before = before;
        this.after = after;
    }

    public void setBeforeFilters(ArrayList<PythonHookFilter> filters) {
        beforeFilters = filters;
    }

    public void setAfterFilters(ArrayList<PythonHookFilter> filters) {
        afterFilters = filters;
    }

    public void close() {
        disabled = true;
    }

    @Override
    public void beforeHookedMethod(MethodHookParam param) {
        invoke(before, beforeFilters, param, true, "before_hooked_method");
    }

    @Override
    public void afterHookedMethod(MethodHookParam param) {
        invoke(after, afterFilters, param, false, "after_hooked_method");
    }

    private void invoke(PyObject callback, ArrayList<PythonHookFilter> filters,
                        MethodHookParam param, boolean isBefore, String phase) {
        if (disabled || callback == null) {
            return;
        }
        if (!HookBridge.isPluginActive(pluginId)) {
            return;
        }
        try {
            if (filters != null) {
                for (PythonHookFilter filter : filters) {
                    if (!filter.execute(param, isBefore)) {
                        return;
                    }
                }
            }
            callback.call(param);
        } catch (Throwable error) {
            FileLog.e("Plugin '" + pluginId + "' crashed in " + phase, error);
        }
    }
}
