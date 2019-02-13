package org.dynamicruntime.util

import spock.lang.Specification

class EncodeUtilTest extends Specification {
    // This also implicitly does simple validation of uuEncode and uuDecode.
    def "Verify encryption algorithm can do encryption"() {
        String inText = "abc"
        String key = EncodeUtil.mkEncryptionKey()

        when: "Encoding and decoding text"
        String encrypted = EncodeUtil.encrypt(key, inText)
        String plainText = EncodeUtil.decrypt(key, encrypted)

        then: "Round trip should produce original value"
        inText == plainText
    }

    def "Verify hashing algorithm"() {
        String inText = "abc"
        when: "Producing a hash"
        String hash = EncodeUtil.hashPassword(inText)

        then: "Should be able to check hash against original value"
        EncodeUtil.checkPassword(inText, hash)
    }

    def "Verify generating readable code"() {
        List<Byte> bytes = [0x3A, 0xE2]
        when: "Producing a string"

        then:
        EncodeUtil.convertToReadableChars(bytes as byte[], 2) == 'KZ8H'
    }
}
