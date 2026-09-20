package ru.vyarus.java.generics.resolver

import ru.vyarus.java.generics.resolver.context.GenericsContext
import ru.vyarus.java.generics.resolver.context.GenericsInfoFactory
import ru.vyarus.java.generics.resolver.context.container.ExplicitTypeVariable
import ru.vyarus.java.generics.resolver.context.trace.ResolutionTrace
import ru.vyarus.java.generics.resolver.context.trace.ResolutionTracer
import ru.vyarus.java.generics.resolver.context.trace.TraceEvent
import ru.vyarus.java.generics.resolver.context.trace.TraceEventType
import ru.vyarus.java.generics.resolver.error.GenericsResolutionException
import ru.vyarus.java.generics.resolver.error.IncompatibleTypesException
import ru.vyarus.java.generics.resolver.error.UnknownGenericException
import ru.vyarus.java.generics.resolver.error.WrongGenericsContextException
import ru.vyarus.java.generics.resolver.support.trace.ArrCase
import ru.vyarus.java.generics.resolver.support.trace.CacheCase
import ru.vyarus.java.generics.resolver.support.trace.Diamond
import ru.vyarus.java.generics.resolver.support.trace.ExplCase
import ru.vyarus.java.generics.resolver.support.trace.OtherCase
import ru.vyarus.java.generics.resolver.support.trace.OwnerCase
import ru.vyarus.java.generics.resolver.support.trace.ShadowCase
import ru.vyarus.java.generics.resolver.support.trace.WildCase
import spock.lang.Specification

import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

/**
 * Resolution trace tests.
 *
 * @author Vyacheslav Rusakov
 */
class ResolutionTraceTest extends Specification {

    def "Check tracing disabled by default"() {

        when: "context without tracing"
        GenericsContext context = GenericsResolver.resolve(ExplCase.ExplRoot)
        then: "no-op tracer used (no events list allocation)"
        !context.isTracing()
        context.getTracer() == ResolutionTracer.NONE
        context.getTrace().isEmpty()

        when: "resolving type"
        context.type(ExplCase).resolveType(ExplCase.getTypeParameters()[0])
        then: "trace still empty"
        context.getTrace().isEmpty()

        when: "resolution fails"
        context.genericType("UNKNOWN")
        then: "exception without trace"
        def ex = thrown(UnknownGenericException)
        ex.getResolutionTrace() == null
    }

    def "Check simple resolution trace"() {

        when: "trace enabled context"
        GenericsContext context = GenericsResolver.resolve(ExplCase.ExplRoot).type(ExplCase)
        context.enableTracing()
        Type res = context.resolveType(ExplCase.getTypeParameters()[0])
        then: "type resolved"
        res == String

        and: "trace recorded in order"
        ResolutionTrace trace = context.getTrace()
        trace.events*.type == [TraceEventType.SCOPE, TraceEventType.READ_TYPE,
                               TraceEventType.SUBSTITUTE, TraceEventType.RESULT]
        trace.events[0].message.startsWith("class ExplCase<T>")
        trace.events[1].message == "T"
        trace.events[2].message == "T => String"
        trace.events[3].message == "String"

        and: "derived contexts share the same trace"
        context.type(ExplCase.ExplRoot).getTrace().events == trace.events
    }

    def "Check interface diamond trace"() {

        when: "inlying resolution of diamond hierarchy"
        GenericsContext context = GenericsResolver.resolve(Diamond.DiaRoot)
        context.enableTracing()
        GenericsContext inlying = context.type(Diamond.DiaHolder)
                .fieldType(Diamond.DiaHolder.getField("mid"))
        then: "diamond resolved"
        inlying.type(Diamond.DiaBase).genericType("T") == Long

        and: "substitutions from all diamond branches recorded"
        List<String> substitutions = context.getTrace().events
                .findAll { it.type == TraceEventType.SUBSTITUTE }*.message
        substitutions.contains("T => Long")
        substitutions.contains("M => Long")
        substitutions.contains("L => Long")
        substitutions.contains("R => Long")
    }

    def "Check inner class owner trace"() {

        when: "inlying resolution of inner class"
        GenericsContext context = GenericsResolver.resolve(OwnerCase.OwnerRoot)
        context.enableTracing()
        GenericsContext inner = context.type(OwnerCase)
                .fieldType(OwnerCase.getField("inner"))
        then: "owner generic visible"
        inner.resolveType(OwnerCase.Inner.getField("value").getGenericType()) == String

        and: "owner passing recorded"
        List<TraceEvent> events = context.getTrace().events
        events.any { it.type == TraceEventType.OWNER }
        events.findAll { it.type == TraceEventType.SUBSTITUTE }*.message.contains("O => String")
    }

    def "Check method generic hides class generic"() {

        when: "method context with hiding generic"
        GenericsContext context = GenericsResolver.resolve(ShadowCase.ShadowRoot)
        context.enableTracing()
        def methodContext = context.method(ShadowCase.getMethod("shadow", Object))
        List<Class<?>> params = methodContext.resolveParameters()
        then: "method generic resolved by bound (class generic hidden)"
        params == [Object]

        and: "method generic narrowed to bound"
        List<TraceEvent> events = context.getTrace().events
        events.any { it.type == TraceEventType.NARROW_BOUND && it.message == "T => Object" }
        and: "method scope recorded"
        events.any { it.type == TraceEventType.SCOPE && it.message.startsWith("method ") }
    }

    def "Check wildcard bounds trace"() {

        when: "resolving wildcard types"
        GenericsContext context = GenericsResolver.resolve(WildCase.WildRoot).type(WildCase)
        context.enableTracing()
        Type upper = context.resolveFieldType(WildCase.getField("upper"))
        Type lower = context.resolveFieldType(WildCase.getField("lower"))
        then: "wildcards resolved"
        context.toStringType(upper) == "List<Number>"
        context.toStringType(lower) == "List<? super Number>"

        and: "upper bound wildcard narrowed"
        List<TraceEvent> events = context.getTrace().events
        events.any { it.type == TraceEventType.NARROW_BOUND && it.message == "? extends T => Number" }
        events.findAll { it.type == TraceEventType.SUBSTITUTE }*.message.count("T => Number") == 2
    }

    def "Check generic array trace"() {

        when: "resolving generic array"
        GenericsContext context = GenericsResolver.resolve(ArrCase.ArrRoot).type(ArrCase)
        context.enableTracing()
        Type array = context.resolveFieldType(ArrCase.getField("array"))
        Type list = context.resolveFieldType(ArrCase.getField("list"))
        then: "arrays resolved"
        array == String[]
        context.toStringType(list) == "List<String[]>"

        and: "substitutions recorded"
        context.getTrace().events.findAll { it.type == TraceEventType.SUBSTITUTE }
                *.message.count("T => String") == 2
    }

    def "Check explicit variable container trace"() {

        when: "resolving explicit variable container"
        GenericsContext context = GenericsResolver.resolve(ExplCase.ExplRoot).type(ExplCase)
        context.enableTracing()
        TypeVariable variable = ExplCase.getTypeParameters()[0]
        Type res = context.resolveType(new ExplicitTypeVariable(variable))
        then: "explicit variable preserved"
        res instanceof ExplicitTypeVariable
        ((ExplicitTypeVariable) res).getName() == "T"
        context.getTrace().events.last().message == "T"
    }

    def "Check cache events and trace isolation between contexts"() {

        setup: "clear descriptors cache"
        GenericsInfoFactory.clearCache()

        when: "first context resolves cacheable inlying type"
        GenericsContext first = GenericsResolver.resolve(CacheCase)
        first.enableTracing()
        first.fieldType(CacheCase.getField("target"))
        then: "cache miss recorded"
        first.getTrace().events.any { it.type == TraceEventType.CACHE && it.message.contains("miss") }
        !first.getTrace().events.any { it.type == TraceEventType.CACHE && it.message.contains("hit") }

        when: "second context resolves the same type (cached descriptor shared)"
        GenericsContext second = GenericsResolver.resolve(CacheCase)
        second.enableTracing()
        second.fieldType(CacheCase.getField("target"))
        then: "cache hit recorded"
        second.getTrace().events.any { it.type == TraceEventType.CACHE && it.message.contains("hit") }

        and: "traces are not mixed (only own scope events)"
        first.getTrace().events.findAll { it.type == TraceEventType.CACHE }*.message !=
                second.getTrace().events.findAll { it.type == TraceEventType.CACHE }*.message
        first.getTrace().events.every { !it.message.contains("hit") }
    }

    def "Check unknown generic failure trace"() {

        when: "resolution fails with unknown generic"
        GenericsContext context = GenericsResolver.resolve(ExplCase.ExplRoot).type(ExplCase)
        context.enableTracing()
        context.genericType("UNKNOWN")
        then: "exception contains trace"
        def ex = thrown(UnknownGenericException)
        ex.getResolutionTrace() != null
        ex.getResolutionTrace().lastEvent.type == TraceEventType.ERROR
        ex.getResolutionTrace().lastEvent.message.startsWith("UnknownGenericException")
        ex.getResolutionTrace().events.any { it.type == TraceEventType.SCOPE }
    }

    def "Check wrong context failure trace"() {

        when: "type with foreign generic resolved"
        GenericsContext context = GenericsResolver.resolve(ExplCase.ExplRoot)
        context.enableTracing()
        context.resolveType(OtherCase.getField("value").getGenericType())
        then: "exception contains trace"
        def ex = thrown(WrongGenericsContextException)
        ex.getResolutionTrace() != null
        ex.getResolutionTrace().lastEvent.type == TraceEventType.ERROR
    }

    def "Check incompatible types failure trace"() {

        when: "inlying resolution of incompatible hierarchy"
        GenericsContext context = GenericsResolver.resolve(BadHolder)
        context.enableTracing()
        context.fieldType(BadHolder.getDeclaredField("bad"))
        then: "exception contains trace, also on cause"
        def ex = thrown(GenericsResolutionException)
        ex.getResolutionTrace() != null
        ex.getResolutionTrace().lastEvent.type == TraceEventType.ERROR
        ex.getCause() instanceof IncompatibleTypesException
        ((IncompatibleTypesException) ex.getCause()).getResolutionTrace() != null
    }

    def "Check trace immutability"() {

        when: "trace recorded"
        GenericsContext context = GenericsResolver.resolve(ExplCase.ExplRoot).type(ExplCase)
        context.enableTracing()
        context.resolveType(ExplCase.getTypeParameters()[0])
        ResolutionTrace trace = context.getTrace()
        then: "events list is unmodifiable"
        trace.events.size() > 0
        when: "modifying events"
        trace.events.add(null)
        then:
        thrown(UnsupportedOperationException)

        when: "modifying scope event generics"
        TraceEvent scope = trace.events.find { it.type == TraceEventType.SCOPE }
        scope.generics.put("X", String)
        then:
        thrown(UnsupportedOperationException)
    }

    static class BadHolder {
        ImplBad bad
    }

    static interface Iface<T> {
    }

    static interface IfaceLeft<T> extends Iface<T> {
    }

    // same interface with incompatible generics (allowed in groovy)
    static class ImplBad implements IfaceLeft<String>, Iface<Integer> {
    }
}
