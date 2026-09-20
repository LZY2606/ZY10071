package ru.vyarus.java.generics.resolver.trace;

import ru.vyarus.java.generics.resolver.context.container.ExplicitTypeVariable;
import ru.vyarus.java.generics.resolver.util.TypeToStringUtils;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.IdentityHashMap;

/**
 * Renders a single {@link Type} for trace output in a stable, recursion-safe manner.
 * <p>
 * Composite type objects (parameterized types, wildcards, generic arrays and variables) receive identity
 * reference numbers. The first occurrence is rendered as {@code #n=Type...} and all later occurrences
 * as {@code @n}, so recursive (self referencing) types terminate instead of expanding infinitely.
 * References are assigned in discovery order and only to types actually encountered more then once,
 * keeping output of simple types clean.
 * <p>
 * Simple {@link Class} values are rendered as leaves (never referenced) and use
 * {@link TypeToStringUtils#toStringType(Type)} so inner classes look the same as in error messages.
 *
 * @author Vyacheslav Rusakov
 * @since 3.1.0
 */
public final class TraceTypeRenderer {

    private TraceTypeRenderer() {
    }

    /**
     * @param type type to render (may be null)
     * @return stable, recursion safe representation
     */
    public static String render(final Type type) {
        if (type == null) {
            return "null";
        }
        final RenderState state = new RenderState();
        count(type, state);
        // assign numbers only to composite types appearing more then once
        int sequence = 0;
        for (Type repeated : state.order) {
            if (state.count(repeated) > 1 && !state.ids.containsKey(repeated)) {
                state.ids.put(repeated, ++sequence);
            }
        }
        return expand(type, state);
    }

    /**
     * First pass: count identity occurrences of each composite type (cycle safe).
     */
    private static void count(final Type type, final RenderState state) {
        if (type == null || type instanceof Class) {
            return;
        }
        if (state.visiting.containsKey(type)) {
            // already counted this expansion subtree (cycle or currently walking)
            state.register(type);
            return;
        }
        state.visiting.put(type, Boolean.TRUE);
        state.register(type);
        if (type instanceof ParameterizedType) {
            final ParameterizedType p = (ParameterizedType) type;
            count(p.getOwnerType(), state);
            count(p.getRawType(), state);
            for (Type arg : p.getActualTypeArguments()) {
                count(arg, state);
            }
        } else if (type instanceof WildcardType) {
            final WildcardType w = (WildcardType) type;
            for (Type t : w.getLowerBounds()) {
                count(t, state);
            }
            for (Type t : w.getUpperBounds()) {
                count(t, state);
            }
        } else if (type instanceof GenericArrayType) {
            count(((GenericArrayType) type).getGenericComponentType(), state);
        }
        // ExplicitTypeVariable and plain TypeVariable are leaves (name only)
        state.visiting.remove(type);
    }

    /**
     * Second pass: render with preassigned reference numbers (cycle safe).
     */
    private static String expand(final Type type, final RenderState state) {
        if (type instanceof Class) {
            return TypeToStringUtils.toStringType(type);
        }
        final Integer id = state.ids.get(type);
        if (id != null) {
            if (state.expanded.containsKey(type)) {
                return '@' + String.valueOf(id);
            }
            state.expanded.put(type, Boolean.TRUE);
            return '#' + String.valueOf(id) + '=' + expandBody(type, state);
        }
        return expandBody(type, state);
    }

    private static String expandBody(final Type type, final RenderState state) {
        final String res;
        if (type instanceof ParameterizedType) {
            res = expandParameterized((ParameterizedType) type, state);
        } else if (type instanceof WildcardType) {
            res = expandWildcard((WildcardType) type, state);
        } else if (type instanceof GenericArrayType) {
            res = expand(((GenericArrayType) type).getGenericComponentType(), state) + "[]";
        } else if (type instanceof ExplicitTypeVariable) {
            res = type.toString();
        } else if (type instanceof TypeVariable) {
            res = ((TypeVariable) type).getName();
        } else {
            res = String.valueOf(type);
        }
        return res;
    }

    private static String expandParameterized(final ParameterizedType type, final RenderState state) {
        final StringBuilder res = new StringBuilder(expand(type.getRawType(), state));
        final Type[] args = type.getActualTypeArguments();
        if (args.length > 0) {
            res.append('<');
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    res.append(", ");
                }
                res.append(expand(args[i], state));
            }
            res.append('>');
        }
        if (type.getOwnerType() != null) {
            res.insert(0, expand(type.getOwnerType(), state) + '.');
        }
        return res.toString();
    }

    private static String expandWildcard(final WildcardType type, final RenderState state) {
        final Type[] lower = type.getLowerBounds();
        if (lower.length > 0) {
            return "? super " + expand(lower[0], state);
        }
        final Type[] upper = type.getUpperBounds();
        if (upper.length == 1 && upper[0] == Object.class) {
            return "?";
        }
        final StringBuilder res = new StringBuilder("? extends ");
        for (int i = 0; i < upper.length; i++) {
            if (i > 0) {
                res.append(" & ");
            }
            res.append(expand(upper[i], state));
        }
        return res.toString();
    }

    /**
     * Mutable render state, allocated only while rendering one type.
     */
    @SuppressWarnings("PMD.LooseCoupling")
    private static final class RenderState {
        // types currently walked in first pass (cycle guard)
        private final IdentityHashMap<Type, Boolean> visiting = new IdentityHashMap<>();
        // identity occurrence count
        private final IdentityHashMap<Type, Integer> counts = new IdentityHashMap<>();
        // discovery order (LinkedHashMap semantics via two structures)
        private final IdentityHashMap<Type, Boolean> orderMark = new IdentityHashMap<>();
        private final java.util.ArrayList<Type> order = new java.util.ArrayList<>();
        // assigned reference number
        private final IdentityHashMap<Type, Integer> ids = new IdentityHashMap<>();
        // types already rendered with "#n=" prefix
        private final IdentityHashMap<Type, Boolean> expanded = new IdentityHashMap<>();

        private void register(final Type type) {
            final Integer cnt = counts.get(type);
            counts.put(type, cnt == null ? 1 : cnt + 1);
            if (!orderMark.containsKey(type)) {
                orderMark.put(type, Boolean.TRUE);
                order.add(type);
            }
        }

        private int count(final Type type) {
            final Integer cnt = counts.get(type);
            return cnt == null ? 0 : cnt;
        }
    }
}
