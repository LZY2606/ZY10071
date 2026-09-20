package ru.vyarus.java.generics.resolver.trace

import ru.vyarus.java.generics.resolver.GenericsResolver
import ru.vyarus.java.generics.resolver.context.GenericsInfoFactory
import ru.vyarus.java.generics.resolver.trace.support.TraceTypes
import spock.lang.Specification

/**
 * Cache hit events must report the caller scope and must not cross context/thread boundaries.
 *
 * @since 3.1.0
 */
class ResolutionCacheTraceTest extends Specification {

    def setup() {
        // ensure cache globally enabled for these tests (other specs may disable it)
        System.setProperty(GenericsInfoFactory.CACHE_PROPERTY, "true")
        GenericsInfoFactory.clearCache()
    }

    def "Cache hit is recorded on second descriptor resolution"() {
        setup:
        GenericsResolver.resolve(TraceTypes.Root)
        def tracer = ResolutionTracer.start()

        when:
        GenericsResolver.resolve(TraceTypes.Root)
        def trace = tracer.snapshot()
        tracer.stop()

        then:
        trace.events.any {
            it.kind == TraceEventKind.CACHE_HIT && it.type == TraceTypes.Root
        }
    }

    def "Cached descriptor reused by different context keeps caller scope"() {
        setup:
        GenericsResolver.resolve(TraceTypes.Root)
        def baseContext = GenericsResolver.resolve(TraceTypes.Root).type(TraceTypes.Base)
        def tracer = baseContext.startTracing()

        when: "the same cached descriptor is used while active scope says Base"
        // explicit re-enter Base scope on the traced context
        baseContext.enterActiveScope()
        GenericsResolver.resolve(TraceTypes.Root)
        def trace = tracer.snapshot()
        tracer.stop()

        then: "cache-hit event carries the caller (Base) scope, not the descriptor root"
        def hit = trace.events.find { it.kind == TraceEventKind.CACHE_HIT }
        hit != null
        hit.scope.currentClass == TraceTypes.Base
    }

    def "No cache hit when caching is disabled"() {
        setup:
        GenericsInfoFactory.disableCache()

        when:
        GenericsResolver.resolve(TraceTypes.Root)
        def tracer = ResolutionTracer.start()
        GenericsResolver.resolve(TraceTypes.Root)
        def trace = tracer.snapshot()
        tracer.stop()

        then:
        !trace.events.any { it.kind == TraceEventKind.CACHE_HIT }

        cleanup:
        System.setProperty(GenericsInfoFactory.CACHE_PROPERTY, "true")
    }
}
