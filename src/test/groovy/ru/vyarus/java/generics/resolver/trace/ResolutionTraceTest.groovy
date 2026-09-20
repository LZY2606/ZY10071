package ru.vyarus.java.generics.resolver.trace

import ru.vyarus.java.generics.resolver.GenericsResolver
import ru.vyarus.java.generics.resolver.context.MethodGenericsContext
import ru.vyarus.java.generics.resolver.context.container.ExplicitTypeVariable
import ru.vyarus.java.generics.resolver.error.UnknownGenericException
import ru.vyarus.java.generics.resolver.error.WrongGenericsContextException
import ru.vyarus.java.generics.resolver.support.nestedtype.direct.BadRoot
import ru.vyarus.java.generics.resolver.trace.support.TraceTypes
import ru.vyarus.java.generics.resolver.util.GenericsResolutionUtils
import ru.vyarus.java.generics.resolver.util.TypeVariableUtils
import spock.lang.Specification

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

/**
 * @author Resolution trace feature
 * @since 3.1.0
 */
class ResolutionTraceTest extends Specification {

    def "Trace is off by default and context returns null"() {
        when:
        def context = GenericsResolver.resolve(TraceTypes.Root).type(TraceTypes.Base)
        then:
        context.trace() == null
        ResolutionTracer.active() == null
    }

    def "Successful resolution trace contains scope, read, substitute and complete"() {
        setup:
        def context = GenericsResolver.resolve(TraceTypes.Root).type(TraceTypes.Base)
        def tracer = context.startTracing()

        when:
        def result = context.resolveClass(TraceTypes.Base.getDeclaredMethod("getT").genericReturnType)
        def trace = tracer.finish(result)
        tracer.stop()

        then:
        result == Integer
        trace.successful
        trace.complete
        trace.result == Integer
        def kinds = trace.events*.kind
        kinds.contains(TraceEventKind.SCOPE)
        kinds.contains(TraceEventKind.READ_TYPE)
        kinds.contains(TraceEventKind.VARIABLE_SUBSTITUTE)
        kinds.last() == TraceEventKind.COMPLETE

        and: "substitution recorded actual values"
        def sub = trace.events.find { it.kind == TraceEventKind.VARIABLE_SUBSTITUTE }
        sub.subject == 'T'
        sub.toType == Integer
    }

    def "Multi level interface diamond traces scope transitions"() {
        setup:
        def context = GenericsResolver.resolve(TraceTypes.Root)
        def tracer = context.startTracing()

        when:
        def iaContext = context.type(TraceTypes.IA)
        def value = iaContext.generic('T')
        def trace = tracer.finish(value)
        tracer.stop()

        then:
        value == Integer
        trace.events.find { it.kind == TraceEventKind.SCOPE && it.scope.currentClass == TraceTypes.IA }
        trace.lastScope.currentClass == TraceTypes.IA
    }

    def "Owner transfer appears during inlying resolution"() {
        setup:
        def rootCtx = GenericsResolver.resolve(TraceTypes.SimpleHolder)
        def tracer = rootCtx.startTracing()

        when:
        def inlying = rootCtx.fieldType(TraceTypes.SimpleHolder.getDeclaredField('field'))
        def trace = tracer.snapshot()
        tracer.stop()

        then:
        trace.events.any { it.kind == TraceEventKind.INLYING }
        trace.events.any { it.kind == TraceEventKind.OWNER_TRANSFER }
        inlying.ownerClass() == TraceTypes.SimpleOuter
    }

    def "Method own variable shadows class variable in scope snapshot"() {
        setup:
        def context = GenericsResolver.resolve(TraceTypes.Root).type(TraceTypes.Base)
        def method = TraceTypes.Base.getDeclaredMethod('shadow', Number[])
        MethodGenericsContext mc = context.method(method)
        def tracer = mc.startTracing()

        when:
        def types = mc.resolveParametersTypes()
        def trace = tracer.snapshot()
        tracer.stop()

        then: "method T shadows class T: resolved to method bound Number"
        types[0] == Number[]
        def scope = trace.lastScope
        scope.kind.name() == 'METHOD'
        scope.member.startsWith('shadow')
        // method T resolved to its bound Number, not class T = Integer
        scope.generics['T'] == 'Number'
        // and substitution used method scope value, not class Integer
        trace.events.any {
            it.kind == TraceEventKind.VARIABLE_SUBSTITUTE && it.subject == 'T' && it.toType == Number
        }
    }

    def "Wildcard bounds and generic array substitution are reflected"() {
        setup:
        def context = GenericsResolver.resolve(TraceTypes.Root).type(TraceTypes.Base)
        def tracer = context.startTracing()

        when:
        def lowerType = context.resolveType(TraceTypes.Base.getDeclaredField('lower').genericType)
        def arrayType = context.resolveType(TraceTypes.Base.getDeclaredField('array').genericType)
        def trace = tracer.snapshot()
        tracer.stop()

        then:
        lowerType.toString().contains('? super Integer')
        arrayType == Integer[]
        trace.events.findAll {
            it.kind == TraceEventKind.VARIABLE_SUBSTITUTE && it.subject == 'T' && it.toType == Integer
        }.size() == 2
    }

    def "Explicit variable container resolution is traced"() {
        setup:
        def tv = TraceTypes.Base.typeParameters[0]
        def explicit = new ExplicitTypeVariable(tv)
        Map<String, Type> generics = [(tv.name): Integer]
        def tracer = ResolutionTracer.start()

        when:
        def resolved = TypeVariableUtils.resolveAllTypeVariables(explicit, generics)
        def trace = tracer.snapshot()
        tracer.stop()

        then:
        resolved == Integer
        trace.events.any { it.kind == TraceEventKind.VARIABLE_SUBSTITUTE && it.subject == 'T' }
    }

    def "UnknownGeneric failure trace is obtainable from exception"() {
        setup:
        def context = GenericsResolver.resolve(TraceTypes.Root)
        def tracer = context.startTracing()
        def expected = null

        when:
        try {
            context.generic('ZZZ')
            assert false
        } catch (UnknownGenericException ex) {
            expected = ex
        }
        def trace = TraceSupport.traceOf(expected)
        tracer.stop()

        then:
        expected != null
        trace != null
        !trace.successful
        trace.complete
        trace.events.last().kind == TraceEventKind.FAILURE
        trace.events.last().detail.contains('UnknownGenericException')
    }

    def "WrongGenericsContext failure trace is obtainable"() {
        setup:
        def context = GenericsResolver.resolve(TraceTypes.Root)
        def tracer = context.startTracing()
        def type = TraceTypes.Foreign.getDeclaredField('field').genericType
        def expected = null

        when:
        try {
            context.resolveClass(type)
            assert false
        } catch (WrongGenericsContextException ex) {
            expected = ex
        }
        def trace = TraceSupport.traceOf(expected)
        tracer.stop()

        then:
        expected != null
        trace != null
        trace.events.last().kind == TraceEventKind.FAILURE
    }

    def "IncompatibleTypes wrapped failure trace is reachable via cause chain"() {
        setup:
        def tracer = ResolutionTracer.start()
        def expected = null

        when:
        try {
            GenericsResolutionUtils.resolve(BadRoot,
                    [] as LinkedHashMap<String, Type>,
                    [:] as Map<Class<?>, LinkedHashMap<String, Type>>,
                    [] as List<Class>)
            assert false
        } catch (ru.vyarus.java.generics.resolver.error.GenericsResolutionException ex) {
            expected = ex
        }
        def trace = TraceSupport.traceOf(expected)
        tracer.stop()

        then:
        expected != null
        trace != null
        trace.events.last().kind == TraceEventKind.FAILURE
        trace.events.last().detail.contains('IncompatibleTypesException')
    }

    def "Events from other threads do not leak into active trace"() {
        setup:
        def context = GenericsResolver.resolve(TraceTypes.Root).type(TraceTypes.Base)
        def tracer = context.startTracing()
        def pool = Executors.newSingleThreadExecutor()
        AtomicReference<Throwable> err = new AtomicReference<>()

        when:
        Future<?> f = pool.submit({
            try {
                context.type(TraceTypes.IA).genericsMap()
            } catch (Throwable t) {
                err.set(t)
            }
        })
        f.get()
        def events = tracer.snapshot().events
        tracer.stop()
        pool.shutdownNow()

        then:
        err.get() == null
        // other thread did not register into this tracer (only initial scope)
        events.size() == 1
    }

    def "Recursive type rendering terminates via reference number"() {
        setup:
        def holder = new Object() { Type arg }
        ParameterizedType rec = new ParameterizedType() {
            Type[] getActualTypeArguments() { [holder.arg] as Type[] }
            Type getRawType() { List }
            Type getOwnerType() { null }
        }
        holder.arg = rec

        expect:
        TraceTypeRenderer.render(rec) == '#1=List<@1>'
    }

    def "Text rendering contains headers and ordered events"() {
        setup:
        def context = GenericsResolver.resolve(TraceTypes.Root).type(TraceTypes.Base)
        def tracer = context.startTracing()
        def result = context.resolveClass(TraceTypes.Base.getDeclaredMethod("getT").genericReturnType)
        def text = tracer.finish(result).toString()
        tracer.stop()

        expect:
        text.contains('CLASS')
        text.contains('[read]')
        text.contains('[substitute]')
        text.contains('[complete] Integer')
    }
}
