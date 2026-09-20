package ru.vyarus.java.generics.resolver.support.trace;

/**
 * Inlying resolution of type without generics (cacheable).
 */
public class CacheCase {

    public CacheTarget target;

    public static class CacheTarget {
        public String name;
    }
}
