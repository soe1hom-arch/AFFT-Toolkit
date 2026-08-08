package com.afft.app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.afft.app.ui.theme.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePersistenceTest {
    /** Round-trip warna via toArgb()/Color(int) — cara yang benar & stabil. */
    @Test
    fun colorRoundTripViaArgbIsStable() {
        val colors = listOf(
            Color(0.77f, 0.79f, 0.81f),
            Color(0.13f, 0.89f, 0.62f),
            Color(0f, 0f, 0f),
            Color(1f, 1f, 1f),
        )
        val unset = Int.MIN_VALUE
        for (c in colors) {
            val stored = c.toArgb()
            val loaded = stored.takeIf { it != unset }?.let { Color(it) }
            assertEquals("stored=$stored", c, loaded)
        }
    }

    @Test
    fun presetIdRoundTrip() {
        for (p in ThemePreset.entries) assertEquals(p, ThemePreset.fromId(p.id))
    }
}
