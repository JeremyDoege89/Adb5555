package dev.dreamz.adb5555

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseReplyTest {
    @Test fun codeOnly() {
        assertEquals(null to "123456", WirelessDebugging.parseReply("123456"))
        assertEquals(null to "123456", WirelessDebugging.parseReply(" 123 456 ".replace(" ", "")))
    }

    @Test fun portAndCode() {
        assertEquals(37123 to "123456", WirelessDebugging.parseReply("37123 123456"))
        assertEquals(37123 to "123456", WirelessDebugging.parseReply("37123:123456"))
        assertEquals(37123 to "123456", WirelessDebugging.parseReply("123456 37123"))
        assertEquals(5555 to "654321", WirelessDebugging.parseReply("192.168.1.5:5555 654321".substringAfter(':')))
    }

    @Test fun rejectsNonsense() {
        assertNull(WirelessDebugging.parseReply(""))
        assertNull(WirelessDebugging.parseReply("12345"))
        assertNull(WirelessDebugging.parseReply("hello"))
        assertNull(WirelessDebugging.parseReply("1 2 3"))
    }
}
