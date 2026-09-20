package ru.vyarus.java.generics.resolver.support.trace;

/**
 * Non static inner class, using owner generic.
 *
 * @param <O> owner generic
 */
public class OwnerCase<O> {

    public Inner inner;

    public class Inner {
        public O value;
    }

    public static class OwnerRoot extends OwnerCase<String> {
    }
}
