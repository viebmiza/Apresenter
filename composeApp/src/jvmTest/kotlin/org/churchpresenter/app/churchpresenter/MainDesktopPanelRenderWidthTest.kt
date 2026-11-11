package org.churchpresenter.app.churchpresenter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainDesktopPanelRenderWidthTest {

    @Test
    fun `an open uncapped panel renders at its requested width`() {
        assertEquals(340, panelRenderWidthPx(requestedPx = 340f, capPx = 600f, visibleFraction = 1f))
    }

    @Test
    fun `a collapsed panel renders at nothing`() {
        assertEquals(0, panelRenderWidthPx(requestedPx = 340f, capPx = 600f, visibleFraction = 0f))
    }

    @Test
    fun `a panel wider than the cap renders at the cap`() {
        assertEquals(220, panelRenderWidthPx(requestedPx = 500f, capPx = 220f, visibleFraction = 1f))
    }

    @Test
    fun `the cap applies before the collapse animation scales it`() {
        // Scaling first would animate from 500 and overshoot the cap on the way open.
        assertEquals(110, panelRenderWidthPx(requestedPx = 500f, capPx = 220f, visibleFraction = 0.5f))
    }

    @Test
    fun `a half-open panel renders at half its width`() {
        assertEquals(170, panelRenderWidthPx(requestedPx = 340f, capPx = 600f, visibleFraction = 0.5f))
    }

    @Test
    fun `a spring undershooting below zero still renders a legal width`() {
        assertEquals(
            0, panelRenderWidthPx(requestedPx = 340f, capPx = 600f, visibleFraction = -0.05f),
            "a negative width is not a legal measurement constraint",
        )
    }

    @Test
    fun `a zero cap collapses the panel entirely`() {
        assertEquals(0, panelRenderWidthPx(requestedPx = 340f, capPx = 0f, visibleFraction = 1f))
    }

    @Test
    fun `the width grows as the panel opens`() {
        val widths = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
            .map { panelRenderWidthPx(requestedPx = 400f, capPx = 600f, visibleFraction = it) }

        assertEquals(widths.sorted(), widths, "the open animation must not go backwards: $widths")
        assertEquals(0, widths.first())
        assertEquals(400, widths.last())
    }

    @Test
    fun `a fractional width rounds rather than truncating`() {
        assertTrue(panelRenderWidthPx(requestedPx = 100.6f, capPx = 600f, visibleFraction = 1f) == 101)
    }
}
