package ru.vyarus.java.generics.resolver.support.trace;

/**
 * Multi level interface diamond: DiaMid -> DiaLeft/DiaRight -> DiaBase.
 */
public class Diamond {

    public interface DiaBase<T> {
    }

    public interface DiaLeft<L> extends DiaBase<L> {
    }

    public interface DiaRight<R> extends DiaBase<R> {
    }

    public interface DiaMid<M> extends DiaLeft<M>, DiaRight<M> {
    }

    public static class DiaHolder<T> {
        public DiaMid<T> mid;
    }

    public static class DiaRoot extends DiaHolder<Long> {
    }
}
