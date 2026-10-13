package uk.co.aaronburt.sharedroid.s3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class KeyTemplateTest {
    private val now = ZonedDateTime.of(2026, 3, 7, 9, 5, 1, 0, ZoneOffset.UTC)

    @Test
    fun dateTokensAndExtension() {
        assertEquals("2026/03/07/090501.png", KeyTemplate.render("{yyyy}/{MM}/{dd}/{HH}{mm}{ss}.{ext}", "Screenshot.PNG", now))
    }

    @Test
    fun defaultTemplateHasRandomName() {
        val key = KeyTemplate.render(KeyTemplate.DEFAULT, "a.jpg", now)
        assertTrue(key, Regex("2026/03/[A-Za-z0-9]{8}\\.jpg").matches(key))
    }

    @Test
    fun filenameIsSanitisedAndUnknownTokensKept() {
        assertEquals("my-holiday-pic_{nope}.bin", KeyTemplate.render("/{filename}_{nope}.{ext}", "my holiday pic", now))
    }

    @Test
    fun randomLength() {
        assertEquals(12, KeyTemplate.render("{random:12}", "x.txt", now).length)
    }
}
