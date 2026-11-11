package org.churchpresenter.app.churchpresenter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MainDesktopNextImageTest {

    private val images = listOf("a.jpg", "b.jpg", "c.jpg")

    @Test
    fun `the next picture is the one after the selected picture`() {
        assertEquals(1, nextImageIndex(0, images.size))
        assertEquals(2, nextImageIndex(1, images.size))
    }

    @Test
    fun `the last picture has no next picture`() {
        assertNull(images.getOrNull(nextImageIndex(2, images.size)))
    }

    @Test
    fun `an index minus one does not preload the first picture as next`() {
        assertEquals(-1, nextImageIndex(-1, images.size))
        assertNull(
            images.getOrNull(nextImageIndex(-1, images.size)),
            "a bare index + 1 would make this picture 0, shown to the platform as what is coming up",
        )
    }

    @Test
    fun `an index past the end has no next picture`() {
        assertNull(images.getOrNull(nextImageIndex(99, images.size)))
    }

    @Test
    fun `an empty folder has no next picture`() {
        assertNull(emptyList<String>().getOrNull(nextImageIndex(0, 0)))
    }

    @Test
    fun `the resolved next picture is the following file, not the selected one`() {
        assertEquals("b.jpg", images.getOrNull(nextImageIndex(0, images.size)))
        assertEquals("c.jpg", images.getOrNull(nextImageIndex(1, images.size)))
    }
}
