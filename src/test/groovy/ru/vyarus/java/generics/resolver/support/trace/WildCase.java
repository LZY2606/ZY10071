package ru.vyarus.java.generics.resolver.support.trace;

import java.util.List;

/**
 * Wildcard upper and lower bounds.
 *
 * @param <T> generic
 */
public class WildCase<T> {

    public List<? extends T> upper;
    public List<? super T> lower;

    public static class WildRoot extends WildCase<Number> {
    }
}
