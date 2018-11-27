package org.dynamicruntime.util

import static ParsingUtil.*;

import spock.lang.Specification

class ParsingUtilTest extends Specification {
    def "Test JSON parsing"() {
        when: "Verifying round trip on JSON"
        Date now = new Date();
        def m = [a : [1, null, "hello"], b: [[c : null, d: 10.1234],
                [e: 10.9996, f: 10*1000*1000*1000L, _g: now]], h: true, i: null]
        String s = toJsonString(m)
        println(s)
        def m2 = toJsonMap(s)

        then: "Should get round trip"
        isJsonEqual(m, m2)
    }
}
