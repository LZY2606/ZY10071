package ru.vyarus.java.generics.resolver.context.trace;

import ru.vyarus.java.generics.resolver.context.container.ExplicitTypeVariable;
import ru.vyarus.java.generics.resolver.util.TypeToStringUtils;
import ru.vyarus.java.generics.resolver.util.map.EmptyGenericsMap;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Renders types for trace events. Unlike {@link TypeToStringUtils}, never fails on unknown variables
 * (variable name rendered) and terminates recursive types with reference numbers
 * (e.g. {@code #1: Comparable<#1>} instead of infinite expansion).
 *
 * @author Vyacheslav Rusakov
 * @since 20.09.2026
 */
final class TraceTypeRenderer {

    private final Map<String, Type> generics;
    private final Map<Type, Integer> ids = new IdentityHashMap<>();
    private final Set<Type> inProgress = new HashSet<>();
    private final Set<Integer> referenced = new HashSet<>();

    private TraceTypeRenderer(final Map<String, Type> generics) {
        this.generics = generics;
    }

    /**
     * @param type     type to render
     * @param generics known generics (used to expand variables, may be empty)
     * @return rendered type (recursive types terminated with reference numbers)
     */
    static String render(final Type type, final Map<String, Type> generics) {
        return new TraceTypeRenderer(generics).renderType(type);
    }

    /**
     * @param generics generics map to render
     * @return rendered map (e.g. {@code {T=String, K=List<Long>}})
     */
    static String renderGenerics(final Map<String, Type> generics) {
        final TraceTypeRenderer renderer = new TraceTypeRenderer(generics);
        final StringBuilder res = new StringBuilder(generics.size() * 20 + 2);
        res.append('{');
        boolean first = true;
        for (Map.Entry<String, Type> entry : generics.entrySet()) {
            if (!first) {
                res.append(", ");
            }
            res.append(entry.getKey()).append('=').append(renderer.renderType(entry.getValue()));
            first = false;
        }
        return res.append('}').toString();
    }

    private String renderType(final Type type) {
        final String res;
        if (type instanceof Class) {
            res = TypeToStringUtils.toStringType(type, EmptyGenericsMap.getInstance());
        } else if (type instanceof ExplicitTypeVariable) {
            // preserved variable container - render name
            res = ((ExplicitTypeVariable) type).getName();
        } else if (type instanceof TypeVariable) {
            res = renderVariable((TypeVariable) type);
        } else if (type instanceof ParameterizedType) {
            res = renderParameterized((ParameterizedType) type);
        } else if (type instanceof WildcardType) {
            res = renderWildcard((WildcardType) type);
        } else if (type instanceof GenericArrayType) {
            res = renderType(((GenericArrayType) type).getGenericComponentType()) + "[]";
        } else {
            res = String.valueOf(type);
        }
        return res;
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private String renderVariable(final TypeVariable variable) {
        final String res;
        if (inProgress.contains(variable)) {
            res = reference(variable);
        } else {
            final Type value = generics.get(variable.getName());
            if (value == null || value == variable || value instanceof TypeVariable) {
                // unknown variable or self reference - simply render name
                res = variable.getName();
            } else {
                enter(variable);
                final String rendered = renderType(value);
                res = leave(variable, rendered);
            }
        }
        return res;
    }

    private String renderParameterized(final ParameterizedType type) {
        if (inProgress.contains(type)) {
            return reference(type);
        }
        enter(type);
        final StringBuilder res = new StringBuilder(50);
        final Type owner = type.getOwnerType();
        final Class<?> raw = (Class<?>) type.getRawType();
        if (owner != null) {
            res.append(renderType(owner)).append('.').append(raw.getSimpleName());
        } else {
            res.append(TypeToStringUtils.toStringType(raw, EmptyGenericsMap.getInstance()));
        }
        final Type[] args = type.getActualTypeArguments();
        if (args.length > 0) {
            res.append('<');
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    res.append(", ");
                }
                res.append(renderType(args[i]));
            }
            res.append('>');
        }
        return leave(type, res.toString());
    }

    private String renderWildcard(final WildcardType type) {
        if (inProgress.contains(type)) {
            return reference(type);
        }
        enter(type);
        final String res;
        if (type.getLowerBounds().length > 0) {
            res = "? super " + renderType(type.getLowerBounds()[0]);
        } else {
            final StringBuilder buf = new StringBuilder("? extends ");
            final Type[] upperBounds = type.getUpperBounds();
            for (int i = 0; i < upperBounds.length; i++) {
                if (i > 0) {
                    buf.append(" & ");
                }
                buf.append(renderType(upperBounds[i]));
            }
            res = buf.toString();
        }
        return leave(type, res);
    }

    private void enter(final Type type) {
        if (!ids.containsKey(type)) {
            ids.put(type, ids.size() + 1);
        }
        inProgress.add(type);
    }

    private String leave(final Type type, final String rendered) {
        inProgress.remove(type);
        final Integer id = ids.get(type);
        // prepend reference number to recursive type declaration to make references resolvable
        return referenced.remove(id) ? "#" + id + ": " + rendered : rendered;
    }

    private String reference(final Type type) {
        final Integer id = ids.get(type);
        referenced.add(id);
        return "#" + id;
    }
}
