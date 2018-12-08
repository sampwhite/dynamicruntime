package org.dynamicruntime.content

import org.dynamicruntime.context.DnCxt
import spock.lang.Specification

class DnContentServiceTest extends Specification {
    def "Test loading resource"() {
        when: "Loading a known resource"
        def cs = new DnContentService()
        def cxt = DnCxt.mkSimpleCxt("contentTest")
        cs.onCreate(cxt)
        def content = cs.getContent(cxt, "md/DynamicType.md")

        then: "Should get content"
        content != null
    }
}
