package com.hackwars.rewrite.client.files

import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RewriteImageViewerWindowTest {
    @Test
    fun imageViewerCandidateUsesLegacyImageExtensions() {
        assertTrue(
            shouldOpenInImageViewer(
                ClientStoredFile(
                    path = "/Public/preview.png",
                    name = "preview.png",
                    kind = ClientStoredFileKind.APPLICATION_BINARY,
                ),
            ),
        )
        assertFalse(
            shouldOpenInImageViewer(
                ClientStoredFile(
                    path = "/Public/readme.txt",
                    name = "readme.txt",
                    kind = ClientStoredFileKind.TEXT,
                ),
            ),
        )
    }

    @Test
    fun imageViewerStateLoadsBufferedImageFromFilePathContents() {
        val imageFile = File.createTempFile("img", ".png")
        imageFile.deleteOnExit()
        val bufferedImage = BufferedImage(18, 12, BufferedImage.TYPE_INT_ARGB).apply {
            val graphics = createGraphics()
            graphics.color = Color(0x22, 0x44, 0xAA)
            graphics.fillRect(0, 0, width, height)
            graphics.dispose()
        }
        ImageIO.write(bufferedImage, "png", imageFile)

        val state = buildImageViewerState(
            ClientStoredFile(
                path = "/Public/preview.png",
                name = "preview.png",
                kind = ClientStoredFileKind.APPLICATION_BINARY,
                contents = imageFile.absolutePath,
            ),
        )

        assertEquals(imageFile.absolutePath, state.sourceDescription)
        assertNotNull(state.image)
        assertEquals(18, state.image?.width)
        assertEquals(12, state.image?.height)
        assertEquals("Loaded image.", state.statusMessage)
        assertTrue(state.errorMessage == null)
    }
}
