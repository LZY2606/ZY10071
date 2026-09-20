package ru.vyarus.java.generics.resolver.support.trace;

import java.util.List;

/**
 * Generic array declarations.
 *
 * @param <T> generic
 */
public class ArrCase<T> {

    public T[] array;
    public List<T[]> list;

    public static class ArrRoot extends ArrCase<String> {
    }
}
