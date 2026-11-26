/*
 * Filtering for Python method hooks.
 *
 * Ported from exteraGram's plugins/hooks/HookFilter.kt (GPLv3 or later, with the additional terms
 * in TMessagesProj/src/main/python/EXTERAGRAM_TERMS.md), including its filter type names so that
 * plugin code behaves the same on both clients.
 */

package tw.nekomimi.nekogram.plugins.hooks;

import org.mvel2.MVEL;
import org.telegram.messenger.FileLog;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

import de.robv.android.xposed.XC_MethodHook;

public final class PythonHookFilter {

    public static final String RESULT_IS_NULL = "result_is_null";
    public static final String RESULT_IS_TRUE = "result_is_true";
    public static final String RESULT_IS_FALSE = "result_is_false";
    public static final String RESULT_NOT_NULL = "result_not_null";
    public static final String RESULT_IS_INSTANCE_OF = "result_is_instance_of";
    public static final String RESULT_EQUAL = "result_equal";
    public static final String RESULT_NOT_EQUAL = "result_not_equal";
    public static final String ARGUMENT_IS_NULL = "argument_is_null";
    public static final String ARGUMENT_IS_TRUE = "argument_is_true";
    public static final String ARGUMENT_IS_FALSE = "argument_is_false";
    public static final String ARGUMENT_NOT_NULL = "argument_not_null";
    public static final String ARGUMENT_IS_INSTANCE_OF = "argument_is_instance_of";
    public static final String ARGUMENT_EQUAL = "argument_equal";
    public static final String ARGUMENT_NOT_EQUAL = "argument_not_equal";
    public static final String CONDITION = "condition";
    public static final String OR = "or";

    private static final int TYPE_UNKNOWN = 0;
    private static final int TYPE_OR = 1;
    private static final int TYPE_CONDITION = 2;
    private static final int TYPE_ARGUMENT_EQUAL = 3;
    private static final int TYPE_ARGUMENT_NOT_EQUAL = 4;
    private static final int TYPE_ARGUMENT_IS_INSTANCE_OF = 5;
    private static final int TYPE_ARGUMENT_IS_FALSE = 6;
    private static final int TYPE_ARGUMENT_IS_NULL = 7;
    private static final int TYPE_ARGUMENT_IS_TRUE = 8;
    private static final int TYPE_ARGUMENT_NOT_NULL = 9;
    private static final int TYPE_RESULT_EQUAL = 10;
    private static final int TYPE_RESULT_NOT_EQUAL = 11;
    private static final int TYPE_RESULT_IS_FALSE = 12;
    private static final int TYPE_RESULT_IS_INSTANCE_OF = 13;
    private static final int TYPE_RESULT_IS_NULL = 14;
    private static final int TYPE_RESULT_IS_TRUE = 15;
    private static final int TYPE_RESULT_NOT_NULL = 16;

    public final String filterType;
    private final int typeId;

    public Integer argIndex;
    public ArrayList<PythonHookFilter> orFilters;
    public String mvelExpression;
    public Class<?> instanceOf;
    public Object object;

    private volatile String compiledSource;
    private volatile Serializable compiledExpression;

    public PythonHookFilter(String filterType) {
        this.filterType = filterType;
        this.typeId = typeIdFor(filterType);
    }

    public boolean execute(XC_MethodHook.MethodHookParam param, boolean isBefore) {
        try {
            switch (typeId) {
                case TYPE_OR: {
                    if (orFilters != null) {
                        for (PythonHookFilter filter : orFilters) {
                            if (filter.execute(param, isBefore)) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
                case TYPE_CONDITION: {
                    String expression = mvelExpression;
                    if (expression == null) {
                        return false;
                    }
                    Serializable compiled = compile(expression);
                    if (compiled == null) {
                        return false;
                    }
                    HashMap<String, Object> vars = new HashMap<>(4);
                    vars.put("param", param);
                    vars.put("result", isBefore ? null : param.getResult());
                    vars.put("object", object);
                    Object result = MVEL.executeExpression(compiled, param.thisObject, vars, Boolean.class);
                    return result != null && (Boolean) result;
                }
                case TYPE_ARGUMENT_EQUAL:
                case TYPE_ARGUMENT_NOT_EQUAL:
                case TYPE_ARGUMENT_IS_INSTANCE_OF:
                case TYPE_ARGUMENT_IS_FALSE:
                case TYPE_ARGUMENT_IS_NULL:
                case TYPE_ARGUMENT_IS_TRUE:
                case TYPE_ARGUMENT_NOT_NULL: {
                    Integer index = argIndex;
                    Object[] args = param.args;
                    if (index == null || args == null || index < 0 || index >= args.length) {
                        return false;
                    }
                    Object value = args[index];
                    switch (typeId) {
                        case TYPE_ARGUMENT_EQUAL:
                            return valuesEqual(value, object);
                        case TYPE_ARGUMENT_NOT_EQUAL:
                            return !valuesEqual(value, object);
                        case TYPE_ARGUMENT_IS_INSTANCE_OF:
                            return instanceOf != null && instanceOf.isInstance(value);
                        case TYPE_ARGUMENT_IS_FALSE:
                            return Boolean.FALSE.equals(value);
                        case TYPE_ARGUMENT_IS_NULL:
                            return value == null;
                        case TYPE_ARGUMENT_IS_TRUE:
                            return Boolean.TRUE.equals(value);
                        case TYPE_ARGUMENT_NOT_NULL:
                            return value != null;
                        default:
                            return false;
                    }
                }
                case TYPE_RESULT_EQUAL:
                case TYPE_RESULT_NOT_EQUAL:
                case TYPE_RESULT_IS_FALSE:
                case TYPE_RESULT_IS_INSTANCE_OF:
                case TYPE_RESULT_IS_NULL:
                case TYPE_RESULT_IS_TRUE:
                case TYPE_RESULT_NOT_NULL: {
                    if (isBefore) {
                        return false;
                    }
                    Object result = param.getResult();
                    switch (typeId) {
                        case TYPE_RESULT_EQUAL:
                            return valuesEqual(result, object);
                        case TYPE_RESULT_NOT_EQUAL:
                            return !valuesEqual(result, object);
                        case TYPE_RESULT_IS_FALSE:
                            return Boolean.FALSE.equals(result);
                        case TYPE_RESULT_IS_TRUE:
                            return Boolean.TRUE.equals(result);
                        case TYPE_RESULT_IS_INSTANCE_OF:
                            return instanceOf != null && instanceOf.isInstance(result);
                        case TYPE_RESULT_IS_NULL:
                            return result == null;
                        case TYPE_RESULT_NOT_NULL:
                            return result != null;
                        default:
                            return false;
                    }
                }
                default:
                    return false;
            }
        } catch (Throwable error) {
            FileLog.e("Hook filter " + filterType + " failed", error);
            return false;
        }
    }

    private Serializable compile(String expression) {
        String source = compiledSource;
        if (source != null && source.equals(expression)) {
            return compiledExpression;
        }
        try {
            Serializable compiled = MVEL.compileExpression(expression);
            compiledExpression = compiled;
            compiledSource = expression;
            return compiled;
        } catch (Throwable error) {
            FileLog.e("Could not compile hook condition: " + expression, error);
            return null;
        }
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a instanceof Number && b instanceof Number) {
            if (a instanceof Double || a instanceof Float || b instanceof Double || b instanceof Float) {
                return ((Number) a).doubleValue() == ((Number) b).doubleValue();
            }
            return ((Number) a).longValue() == ((Number) b).longValue();
        }
        return a != null && a.equals(b);
    }

    private static int typeIdFor(String filterType) {
        if (filterType == null) {
            return TYPE_UNKNOWN;
        }
        switch (filterType) {
            case OR:
                return TYPE_OR;
            case CONDITION:
                return TYPE_CONDITION;
            case ARGUMENT_EQUAL:
                return TYPE_ARGUMENT_EQUAL;
            case ARGUMENT_NOT_EQUAL:
                return TYPE_ARGUMENT_NOT_EQUAL;
            case ARGUMENT_IS_INSTANCE_OF:
                return TYPE_ARGUMENT_IS_INSTANCE_OF;
            case ARGUMENT_IS_FALSE:
                return TYPE_ARGUMENT_IS_FALSE;
            case ARGUMENT_IS_NULL:
                return TYPE_ARGUMENT_IS_NULL;
            case ARGUMENT_IS_TRUE:
                return TYPE_ARGUMENT_IS_TRUE;
            case ARGUMENT_NOT_NULL:
                return TYPE_ARGUMENT_NOT_NULL;
            case RESULT_EQUAL:
                return TYPE_RESULT_EQUAL;
            case RESULT_NOT_EQUAL:
                return TYPE_RESULT_NOT_EQUAL;
            case RESULT_IS_FALSE:
                return TYPE_RESULT_IS_FALSE;
            case RESULT_IS_INSTANCE_OF:
                return TYPE_RESULT_IS_INSTANCE_OF;
            case RESULT_IS_NULL:
                return TYPE_RESULT_IS_NULL;
            case RESULT_IS_TRUE:
                return TYPE_RESULT_IS_TRUE;
            case RESULT_NOT_NULL:
                return TYPE_RESULT_NOT_NULL;
            default:
                return TYPE_UNKNOWN;
        }
    }
}
