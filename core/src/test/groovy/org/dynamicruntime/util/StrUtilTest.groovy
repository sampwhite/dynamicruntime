package org.dynamicruntime.util

import spock.lang.Specification

class StrUtilTest extends Specification {
    def "Custom string split routine works as desired"() {
        when: "Doing a validation of split string"
        then: "Should get expected results"
        StrUtil.splitString("abc", ":") == ["abc"]
        StrUtil.splitString("a,b,c,d", ",") == ["a", "b", "c", "d"]
        StrUtil.splitString("a,b,c,d", ",", 3) == ["a", "b", "c,d"]
        StrUtil.splitString("ab::cd(rx::uv)", "::", 2) == ["ab", "cd(rx::uv)"]
        StrUtil.splitString("ab_cd_xy.cd", "cd") == ["ab_", "_xy.", ""]
    }

    def "Verify that the internal Java name check works as desired"() {
        when: "Doing validation of Java name check"
        then: "Should get expected results"
        !StrUtil.isJavaName("a@b")
        !StrUtil.isJavaName("null")
        !StrUtil.isJavaName("class")
        StrUtil.isJavaName("a_b")
        !StrUtil.isJavaName("a:b")
        !StrUtil.isJavaName("1abc")
    }
}
