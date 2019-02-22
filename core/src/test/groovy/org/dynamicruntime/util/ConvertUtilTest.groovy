package org.dynamicruntime.util

import org.dynamicruntime.exception.DnException
import spock.lang.Shared

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnDateUtil.*;

import spock.lang.Specification

class ConvertUtilTest extends Specification {
    @Shared dateStr = '2018-01-01T01:00:00.032Z'
    @Shared date = parseDate(dateStr)

    def "Test formatting  objects for friendly display"() {
        expect: "Formatted result to be as expected"
        output == fmtObject(input)

        where:
        input                                         || output
        // Test maps, arrays dates, and putting double quotes around strings that have backslashes or commas in them.
        [x : "\\", y: [1, date], z: ["a,b", "c"]]     || "[x:\"\\\\\",y:[1,${dateStr}],z:[\"a,b\",c]]"
        // Test too deep nesting.
        [x : [y : [z : [aa:1], bb:[2]]]]              || "[x:[y:[z:[.:.],bb:[...]]]]"
    }

    def "Test conversion functions"() {
        Date now = new Date();
        String now_s = fmtObject(now)
        def map = [a: now, b: "2", c: now_s, d: "Yes", e: "", f: -22.45, g: "False", h: 22.49, i: 22.502]

        when: "Converting values to strings."
        DnException e1 = null;
        try {
            getReqStr(map, "testDoesNotExist")
        } catch (DnException e) {
            e1 = e;
        }
        DnException e2 = null;
        try {
                getReqStr(map, 'a')
        } catch (DnException e) {
            e2 = e;
        }
        String b_s= getReqStr(map, 'b')
        String c_s = getReqStr(map, 'c')
        String e_s = getOptStr(map, 'e')

        then: "Should get expected strings"
        e1 != null && e1.fullMessage.contains("testDoesNotExist")
        e2 != null && e2.fullMessage.contains("not present")
        b_s == "2"
        c_s == now_s
        e_s == null

        when: "Converting values to longs"
        Long a_l = getOptLong(map, 'a')
        DnException e3 = null;
        try {
            getOptLong(map, 'c')
        } catch (DnException e) {
            e3 = e;
        }
        Long b_l = getOptLong(map, 'b')
        Long f_l = getOptLong(map, 'f')
        Long h_l = getOptLong(map, 'h')
        Long i_l = getOptLong(map, 'i')

        then: "Should get expected results for longs"
        e3 != null && e3.fullMessage.contains(now_s)
        a_l == null
        b_l == 2
        f_l == -22
        h_l == 22
        i_l == 23

        when: "Converting values to booleans"
        def a_b = getBoolWithDefault(map, 'a', true)
        def b_b = getOptBool(map, 'b')
        def d_b = getOptBool(map, 'd')
        def f_b = getOptBool(map, 'f')
        def g_b = getOptBool(map, 'g')

        then: "Should get expected booleans"
        a_b
        b_b == null
        d_b
        f_b
        g_b != null && !g_b

        when: "Converting values to dates"
        DnException e4 = null
        try {
            getOptDate(map, 'd')
        } catch(DnException e) {
            e4 = e;
        }
        DnException e5 = null
        try {
            getReqDate(map, 'doesNotExistDate')
        } catch (DnException e) {
            e5 = e;
        }
        def a_d = getOptDate(map, 'a')
        def c_d = getReqDate(map, 'c')

        then: "Should get expected dates values"
        e4 != null && e4.fullMessage.contains("Yes")
        e5 != null && e5.fullMessage.contains('doesNotExistDate')
        a_d == now
        c_d == now

    }
}
