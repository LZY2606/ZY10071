package ru.vyarus.java.generics.resolver.trace;

import ru.vyarus.java.generics.resolver.context.GenericDeclarationScope;
import ru.vyarus.java.generics.resolver.util.TypeToStringUtils;
import ru.vyarus.java.generics.resolver.util.map.PrintableGenericsMap;

import java.lang.reflect.Constructor;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable description of the declaration scope active during resolution: the class (and possible owner)
 * together with method or constructor (when resolution is performed inside such context).
 * <p>
 * Scope is captured by value at the moment of event recording, so cached descriptor reused by different
 * contexts could never mix up caller scopes (each event holds its own scope snapshot).
 *
 * @author Vyacheslav Rusakov
 * @since 3.1.0
 */
public final class TraceScope {

    private final GenericDeclarationScope kind;
    private final Class<?> currentClass;
    private final String member;
    private final Map<String, String> generics;

    /**
     * @param kind       scope kind (class, method or constructor)
     * @param current    current context class
     * @param source     declaration source (method, constructor or class itself)
     * @param generics   visible generics of scope (rendered as named type strings)
     */
    public TraceScope(final GenericDeclarationScope kind,
                      final Class<?> current,
                      final GenericDeclaration source,
                      final Map<String, ?> generics) {
        this.kind = kind;
        this.currentClass = current;
        this.member = formatMember(kind, source);
        this.generics = generics.isEmpty()
                ? Collections.<String, String>emptyMap() : snapshotGenerics(generics);
    }

    /**
     * @return scope kind
     */
    public GenericDeclarationScope getKind() {
        return kind;
    }

    /**
     * @return current context class
     */
    public Class<?> getCurrentClass() {
        return currentClass;
    }

    /**
     * @return method or constructor declaration string or null for pure class scope
     */
    public String getMember() {
        return member;
    }

    /**
     * @return immutable ordered snapshot of visible generics name -&gt; rendered type
     */
    public Map<String, String> getGenerics() {
        return Collections.unmodifiableMap(generics);
    }

    /**
     * @return stable one line scope description (without generics)
     */
    public String getDisplayName() {
        final String cls = TypeToStringUtils.toStringType(currentClass);
        return member == null ? cls : cls + '#' + member;
    }

    @Override
    public String toString() {
        return kind + " " + getDisplayName();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TraceScope)) {
            return false;
        }
        final TraceScope that = (TraceScope) o;
        return kind == that.kind
                && currentClass.equals(that.currentClass)
                && (member == null ? that.member == null : member.equals(that.member));
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + currentClass.hashCode();
        result = 31 * result + (member != null ? member.hashCode() : 0);
        return result;
    }

    private static String formatMember(final GenericDeclarationScope kind, final GenericDeclaration source) {
        String res = null;
        if (kind == GenericDeclarationScope.METHOD) {
            final Method method = (Method) source;
            res = method.getName() + '(' + formatParams(method.getParameterTypes()) + ')';
        } else if (kind == GenericDeclarationScope.CONSTRUCTOR) {
            final Constructor ctor = (Constructor) source;
            final String name = TypeToStringUtils.toStringType(ctor.getDeclaringClass());
            res = name + '(' + formatParams(ctor.getParameterTypes()) + ')';
        }
        return res;
    }

    private static String formatParams(final Class<?>... params) {
        final StringBuilder res = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                res.append(", ");
            }
            res.append(TypeToStringUtils.toStringType(params[i]));
        }
        return res.toString();
    }

    @SuppressWarnings("PMD.LooseCoupling")
    private static Map<String, String> snapshotGenerics(final Map<String, ?> generics) {
        final PrintableGenericsMap printable = new PrintableGenericsMap();
        final Map<String, String> res = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : generics.entrySet()) {
            final Object value = entry.getValue();
            // all context values are types, but stay defensive against explicit variable containers
            final String rendered = value instanceof java.lang.reflect.Type
                    ? TypeToStringUtils.toStringType((java.lang.reflect.Type) value, printable)
                    : String.valueOf(value);
            res.put(entry.getKey(), rendered);
        }
        return res;
    }
}
