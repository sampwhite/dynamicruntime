package org.dynamicruntime.util

import static ParsingUtil.*;

import spock.lang.Specification

class ParsingUtilTest extends Specification {
    def "Test JSON parsing"() {
        when: "Verifying round trip on JSON"
        Date now = new Date();
        def m = [a : [1, null, "hello"], b: [[c : null, d: 10.1234],
                [e: 10.9996, f: 10*1000*1000*1000L, _g: now]], h: true, i: null]

        /*
        long start1 = System.currentTimeMillis()
        (0..1000000).each {
            toJsonString(m)
        }
        println("Duration1 " + (System.currentTimeMillis() - start1))
        */
        String s = toJsonString(m)
        println(s)
        /*
        long start2 = System.currentTimeMillis()
        (0..1000000).each {
            toJsonMap(s)
        }
        println("Duration2 " + (System.currentTimeMillis() - start2))
        */
        def m2 = toJsonMap(s)

        then: "Should get round trip"
        isJsonEqual(m, m2)
    }
}
