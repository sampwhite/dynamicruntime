package org.dynamicruntime.util

import spock.lang.Specification

class HttpUtilTest extends Specification {
    def "Encoding arguments for URLs should work as expected"() {
        when: "Encoding arguments"
        Map m = [a: "b=c%d&e\nf g\"", h: "+\t?:/\r\\+"]

        then: "Should get expected results"
        HttpUtil.encodeHttpArgs(m) == "a=b%3Dc%25d%26e%0Af+g%22&h=%2B%09%3F%3A%2F%0D%5C%2B"
    }
}
