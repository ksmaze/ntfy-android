package io.heckel.ntfy.util

import org.junit.Test
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone
import java.util.Locale

class UtilTest {
    private var defaultTimeZone: TimeZone? = null
    private var defaultLocale: Locale? = null

    @Before
    fun setUp() {
        defaultTimeZone = TimeZone.getDefault()
        defaultLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(defaultTimeZone)
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun testFormatDateOnlyShort() {
        // Set a known timezone and locale to ensure consistent output
        val timezone = TimeZone.getTimeZone("UTC")
        val locale = Locale.US

        TimeZone.setDefault(timezone)
        Locale.setDefault(locale)

        val timestamp = 1621234567L // 2021-05-17 06:56:07 UTC

        val formatted = formatDateOnlyShort(timestamp)

        val expectedFormat = DateFormat.getDateInstance(DateFormat.SHORT, locale)
        expectedFormat.timeZone = timezone
        val expected = expectedFormat.format(Date(timestamp * 1000))

        assertEquals(expected, formatted)
    }

    @Test
    fun testFormatTimeOnlyShort() {
        val timezone = TimeZone.getTimeZone("UTC")
        val locale = Locale.US

        TimeZone.setDefault(timezone)
        Locale.setDefault(locale)

        val timestamp = 1621234567L // 2021-05-17 06:56:07 UTC

        val formatted = formatTimeOnlyShort(timestamp)

        val expectedFormat = DateFormat.getTimeInstance(DateFormat.SHORT, locale)
        expectedFormat.timeZone = timezone
        val expected = expectedFormat.format(Date(timestamp * 1000))

        assertEquals(expected, formatted)
    }
}
