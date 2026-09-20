# Resolution trace

When a DI framework (or any other reflective tool) receives an unexpected type, knowing only the final
`Type` or an exception is often not enough: you need to understand **how** that value was derived.
For such cases the resolver can record a *resolution trace*: an ordered, immutable description of
declaration scopes visited, types read, variable substitutions, bounds narrowing, owner transfers,
cache hits and the terminal value (or failure).

## Enabling tracing

Tracing is **off by default** and is enabled per resolution session on a context:

```java
GenericsContext context = GenericsResolver.resolve(Root.class).type(Base.class);
ResolutionTracer tracer = context.startTracing();
try {
    Type value = context.resolveType(someType);
    ResolutionTrace trace = tracer.finish(value);   // immutable result
    System.out.println(trace);
} finally {
    tracer.stop();
}
```

A tracer is bound to the current thread only. Concurrent resolutions on other threads (including
resolutions using the same cached descriptors) never mix scopes or events: each event snapshots the
calling scope by value.

Mid-resolution you can always take an immutable snapshot with `context.trace()` or
`tracer.snapshot()`.

## Trace output

Events appear in the actual derivation order. A successful resolution looks like:

```text
CLASS Doc.Base
  generics: T=Long
  [scope] enter Doc.Base
  [read] List<T>
  [substitute] T: T -> Long
  [complete] List<Long>
```

Event kinds:

- `scope` &mdash; entered a class, method or constructor declaration scope (with visible generics)
- `read` &mdash; a declared type was read as resolution input
- `substitute` &mdash; named variable replaced by its resolved value
- `narrow` &mdash; wildcard/declaration bound or interface diamond merge narrowed a type
- `owner` &mdash; non static inner class received outer (owner) generics
- `inlying` &mdash; a new (possibly inlying) context was built for a type
- `cache-hit` &mdash; a cached hierarchy descriptor was reused
- `track` &mdash; a root generic was backtracked from known middle type generics
- `switch` &mdash; context was automatically switched to a compatible scope
- `complete` / `failure` &mdash; terminal events

## Recursive types

Types rendered in events use reference numbers to guarantee termination. The first occurrence of a
repeated composite type is rendered as `#n=...` and later occurrences as `@n`, so self referencing
generics like `X extends Comparable<X>` never expand infinitely.

## Failure traces

Traces work even when resolution throws. The trace is attached to the exception (without changing
exception message or stack trace) and is safely retrieved from the caught exception, including
wrapped exceptions (cause chain is searched):

```java
ResolutionTracer tracer = context.startTracing();
try {
    context.generic("K");
} catch (GenericsException ex) {
    ResolutionTrace trace = TraceSupport.traceOf(ex);
} finally {
    tracer.stop();
}
```

For `UnknownGenericException`:

```text
CLASS Doc.Root
  [scope] enter Doc.Root
  [failure] ru.vyarus.java.generics.resolver.error.UnknownGenericException: Generic 'K' is not declared on type Doc$Root
```

The same mechanism covers `WrongGenericsContextException` and `IncompatibleTypesException`
(the latter may appear wrapped in `GenericsResolutionException`).

## Overhead

When tracing is not enabled, instrumentation points perform a single thread local check and allocate
nothing: no event lists, events, strings or maps are created on the hot path. Exception stack traces
and messages are identical with tracing on or off.
