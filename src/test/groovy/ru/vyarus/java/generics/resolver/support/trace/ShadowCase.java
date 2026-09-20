package ru.vyarus.java.generics.resolver.support.trace;

/**
 * Method generic hides class generic.
 *
 * @param <T> class generic
 */
public class ShadowCase<T> {

    public <T> T shadow(final T arg) {
        return arg;
    }

    public static class ShadowRoot extends ShadowCase<String> {
    }
}
