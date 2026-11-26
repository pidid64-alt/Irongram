/*
 * Python <-> exteraHook bridge for method hooks.
 *
 * The heavy lifting is done by the vendored exteraHook artifacts (Tools/maven): hook-core provides
 * de.robv.android.xposed on top of its ART hook runtime, whose native half is linked into
 * libtmessages.49.so (see TMessagesProj/jni/jni.c). This class only adapts Python callbacks to
 * XC_MethodHook/XC_MethodReplacement and keeps the small amount of per-plugin bookkeeping.
 */

package tw.nekomimi.nekogram.plugins.hooks;

import com.chaquo.python.PyObject;
import com.exteragram.hook.ExteraHook;

import org.telegram.messenger.FileLog;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public final class HookBridge {

    /** Plugins whose hooks must run. Updated by plugin_runtime on load/unload. */
    private static final Map<String, Boolean> ACTIVE_PLUGINS = new ConcurrentHashMap<>();

    private static volatile boolean initialized;
    private static volatile boolean available;
    private static volatile String unavailableReason;

    private HookBridge() {
    }

    public static void setPluginActive(String pluginId, boolean active) {
        if (pluginId == null) {
            return;
        }
        if (active) {
            ACTIVE_PLUGINS.put(pluginId, Boolean.TRUE);
        } else {
            ACTIVE_PLUGINS.remove(pluginId);
        }
    }

    public static boolean isPluginActive(String pluginId) {
        return pluginId != null && ACTIVE_PLUGINS.containsKey(pluginId);
    }

    /**
     * Start the ART hook runtime. Safe to call repeatedly; the hook-core content provider may have
     * failed before the native library was loaded, in which case this retries with the embedded
     * runtime flag set.
     */
    public static synchronized boolean ensureInitialized() {
        if (initialized) {
            return available;
        }
        initialized = true;
        try {
            ExteraHook.useEmbeddedRuntime();
            available = ExteraHook.initialize();
            if (!available) {
                unavailableReason = "the ART hook runtime refused to start";
            }
        } catch (Throwable error) {
            available = false;
            unavailableReason = error.getMessage() == null
                    ? error.getClass().getSimpleName()
                    : error.getMessage();
            FileLog.e("ART hook runtime is unavailable: " + unavailableReason, error);
        }
        return available;
    }

    public static boolean isAvailable() {
        return ensureInitialized();
    }

    public static String getUnavailableReason() {
        ensureInitialized();
        return unavailableReason;
    }

    public static boolean isHooked(Member method) {
        try {
            return ensureInitialized() && XposedBridge.isHooked(method);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Object invokeOriginalMethod(Member method, Object thisObject, Object[] args) {
        try {
            return XposedBridge.invokeOriginalMethod(method, thisObject, args);
        } catch (Throwable error) {
            throw new IllegalStateException(error.getMessage(), error);
        }
    }

    private static XC_MethodHook createCallback(String pluginId, PyObject before, PyObject after,
                                                Integer priority, List<PythonHookFilter> beforeFilters,
                                                List<PythonHookFilter> afterFilters) {
        PythonMethodHook hook = priority == null
                ? new PythonMethodHook(pluginId, before, after)
                : new PythonMethodHook(pluginId, before, after, priority);
        if (beforeFilters != null && !beforeFilters.isEmpty()) {
            hook.setBeforeFilters(new ArrayList<>(beforeFilters));
        }
        if (afterFilters != null && !afterFilters.isEmpty()) {
            hook.setAfterFilters(new ArrayList<>(afterFilters));
        }
        return hook;
    }

    public static XC_MethodHook.Unhook hookMethod(Member method, String pluginId, PyObject before,
                                                  PyObject after, Integer priority,
                                                  List<PythonHookFilter> beforeFilters,
                                                  List<PythonHookFilter> afterFilters) {
        if (!ensureInitialized()) {
            throw new IllegalStateException("ART hooking is unavailable: " + unavailableReason);
        }
        XC_MethodHook callback = createCallback(
                pluginId, before, after, priority, beforeFilters, afterFilters);
        return XposedBridge.hookMethod(method, callback);
    }

    public static XC_MethodHook.Unhook hookReplacement(Member method, String pluginId,
                                                       PyObject callback, Integer priority) {
        if (!ensureInitialized()) {
            throw new IllegalStateException("ART hooking is unavailable: " + unavailableReason);
        }
        PythonMethodReplacement replacement = priority == null
                ? new PythonMethodReplacement(pluginId, callback)
                : new PythonMethodReplacement(pluginId, callback, priority);
        return XposedBridge.hookMethod(method, replacement);
    }

    public static List<XC_MethodHook.Unhook> hookAllMethods(Class<?> target, String methodName,
                                                            String pluginId, PyObject before,
                                                            PyObject after, Integer priority,
                                                            List<PythonHookFilter> beforeFilters,
                                                            List<PythonHookFilter> afterFilters) {
        if (!ensureInitialized()) {
            throw new IllegalStateException("ART hooking is unavailable: " + unavailableReason);
        }
        XC_MethodHook callback = createCallback(
                pluginId, before, after, priority, beforeFilters, afterFilters);
        Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllMethods(target, methodName, callback);
        return unhooks == null ? Collections.emptyList() : new ArrayList<>(unhooks);
    }

    public static List<XC_MethodHook.Unhook> hookAllConstructors(Class<?> target, String pluginId,
                                                                 PyObject before, PyObject after,
                                                                 Integer priority,
                                                                 List<PythonHookFilter> beforeFilters,
                                                                 List<PythonHookFilter> afterFilters) {
        if (!ensureInitialized()) {
            throw new IllegalStateException("ART hooking is unavailable: " + unavailableReason);
        }
        XC_MethodHook callback = createCallback(
                pluginId, before, after, priority, beforeFilters, afterFilters);
        Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllConstructors(target, callback);
        return unhooks == null ? Collections.emptyList() : new ArrayList<>(unhooks);
    }

    public static boolean unhook(XC_MethodHook.Unhook unhook) {
        if (unhook == null) {
            return false;
        }
        try {
            unhook.unhook();
            return true;
        } catch (Throwable error) {
            FileLog.e("Could not remove a plugin hook", error);
            return false;
        }
    }

    public static int unhookAll(List<XC_MethodHook.Unhook> unhooks) {
        if (unhooks == null) {
            return 0;
        }
        int removed = 0;
        for (XC_MethodHook.Unhook unhook : new ArrayList<>(unhooks)) {
            if (unhook(unhook)) {
                removed++;
            }
        }
        unhooks.clear();
        return removed;
    }

    /** Build a filter object for plugin code; see PythonHookFilter for the filter type names. */
    public static PythonHookFilter createFilter(String filterType) {
        return new PythonHookFilter(filterType);
    }
}
