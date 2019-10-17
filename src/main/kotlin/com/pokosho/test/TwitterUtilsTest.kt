package com.pokosho.test

import com.pokosho.bot.twitter.TwitterUtils
import com.pokosho.util.StringUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TwitterUtilsTest {

    @Test
    fun testRemove() {
        assertEquals(
            "賢い、可愛い、わけち",
            TwitterUtils.removeHashTags("賢い、可愛い、わけち #waketi")
        )
        assertEquals(
            "賢い、可愛い、わけち",
            TwitterUtils.removeMention("@waketi 賢い、可愛い、わけち")
        )
        assertEquals("賢い、可愛い、わけち", TwitterUtils.removeRTString("RT 賢い、可愛い、わけち"))
        assertEquals(
            "賢い、可愛い、わけち",
            TwitterUtils
                .removeUrl("賢い、可愛い、わけち http://pokosho.com/b/waketi")
        )
    }

    @Test
    fun testContains() {
        assertTrue(StringUtils.containsJPN("酒飲まずにはいられない"))
        assertTrue(StringUtils.containsKR("한글"))
        assertTrue(StringUtils.containsSurrogatePair("low surrogate \uD800"))
        assertTrue(StringUtils.containsSurrogatePair("high surrogate \uDFFF"))
    }

    @Test
    fun testNotContains() {
        assertFalse(StringUtils.containsSurrogatePair("賢い、可愛い、わけち"))
        assertFalse(StringUtils.containsJPN("URYYY!!!"))
        assertFalse(StringUtils.containsKR("このテストは腐ってる。使えないよ。"))
    }
}
