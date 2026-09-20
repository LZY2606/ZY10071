package ru.vyarus.java.generics.resolver.context.trace

import ru.vyarus.java.generics.resolver.context.container.ParameterizedTypeImpl
import spock.lang.Specification

import java.lang.reflect.TypeVariable

/**
 * Trace type renderer tests (recursive types termination).
 *
 * @author Vyacheslav Rusakov
 */
class TraceTypeRendererTest extends Specification {

    def "Check recursive type terminated with reference number"() {

        when: "rendering recursive variable T -> Comparable<T>"
        TypeVariable variable = Rec.getTypeParameters()[0]
        String res = TraceTypeRenderer.render(variable, [T: new ParameterizedTypeImpl(Comparable, variable)])
        then: "recursion terminated with reference"
        res == '#1: Comparable<#1>'
    }

    def "Check recursive generics map rendering"() {

        when: "rendering map with recursive value"
        TypeVariable variable = Rec.getTypeParameters()[0]
        String res = TraceTypeRenderer.renderGenerics(
                [T: new ParameterizedTypeImpl(Comparable, variable), K: String])
        then: "recursion terminated with reference"
        res == '{T=#1: Comparable<#1>, K=String}'
    }

    def "Check unknown variable rendered as name"() {
        expect:
        TraceTypeRenderer.render(Rec.getTypeParameters()[0], [:]) == 'T'
    }

    static class Rec<T extends Comparable<T>> {
    }
}
