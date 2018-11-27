package org.dynamicruntime.util

import spock.lang.Specification

import static DnDateUtil.*

class DnDateUtilTest extends Specification {

    def "Test parsing and formatting dates"() {
        when: "Formatting and parsing current time"
        def now = new Date()
        def curDate = formatDate(now)
        def timeAgain = parseDate(curDate)

        then: "Should get the same result"
        now == timeAgain

        when: "Formatting and parsing day only dates"
        def curDay = formatDayPart(now)
        def dayAgain = parseDate(curDay)
        def curDayStart = toStartOfDay(now)

        then: "Days should line up"
        curDayStart == dayAgain
    }

}