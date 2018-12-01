package com.pokosho.test

import com.pokosho.bot.twitter.TwitterUtils
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
        assertTrue(TwitterUtils.containsJPN("酒飲まずにはいられない"))
        assertTrue(TwitterUtils.containsKR("한글"))
        assertTrue(TwitterUtils.containsSurrogatePair("low surrogate \uD800"))
        assertTrue(TwitterUtils.containsSurrogatePair("high surrogate \uDFFF"))
    }

    @Test
    fun testNotContains() {
        assertFalse(TwitterUtils.containsSurrogatePair("賢い、可愛い、わけち"))
        assertFalse(TwitterUtils.containsJPN("URYYY!!!"))
        assertFalse(TwitterUtils.containsKR("このテストは腐ってる。使えないよ。"))
    }
}
