package ru.vyarus.java.generics.resolver.trace.support;

import java.util.List;

/**
 * Static support types for resolution trace tests (generic method can't be declared in Groovy 2.5).
 */
public final class TraceTypes {

    protected TraceTypes() {
        // non instantiable holder of nested support types
    }

    public interface IA<T> {
    }

    public interface IB<T> extends IA<T> {
    }

    public interface IC<T> extends IA<T> {
    }

    public static class Base<T> implements IB<T>, IC<T> {
        public List<? super T> lower;
        public T[] array;

        @SuppressWarnings("PMD.UseVarargs")
        public <T extends Number> T shadow(T[] args) {
            return null;
        }

        public T getT() {
            return null;
        }
    }

    public static class Root extends Base<Integer> {
    }

    public static class Outer<O> {
        public class Inner {
        }
    }

    public static class Foreign<F> {
        public F field;
    }

    public static class SimpleOuter<S> {
        public class Inner {
        }
    }

    public static class SimpleHolder {
        public SimpleOuter<String>.Inner field;
    }

    public interface MidA<T> {
    }

    public static class RootA implements MidA<String> {
    }
}
