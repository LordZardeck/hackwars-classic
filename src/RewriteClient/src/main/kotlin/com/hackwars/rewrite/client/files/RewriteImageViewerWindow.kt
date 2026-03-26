package com.hackwars.rewrite.client.files

import com.hackwars.rewrite.protocol.ClientStoredFile
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.io.File
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

internal data class RewriteImageViewerState(
    val file: ClientStoredFile,
    val sourceDescription: String,
    val image: BufferedImage?,
    val statusMessage: String,
    val errorMessage: String? = null,
)

internal fun shouldOpenInImageViewer(file: ClientStoredFile): Boolean {
    val extension = file.name.substringAfterLast('.', "").lowercase()
    return extension in IMAGE_FILE_EXTENSIONS
}

internal fun buildImageViewerState(file: ClientStoredFile): RewriteImageViewerState {
    val sourceDescription = file.contents.trim().ifBlank { file.path.trim() }
    if (sourceDescription.isBlank()) {
        return RewriteImageViewerState(
            file = file,
            sourceDescription = "",
            image = null,
            statusMessage = "No image source available.",
            errorMessage = "No image source available.",
        )
    }

    val image = loadBufferedImage(sourceDescription)
    return if (image != null) {
        RewriteImageViewerState(
            file = file,
            sourceDescription = sourceDescription,
            image = image,
            statusMessage = "Loaded image.",
        )
    } else {
        RewriteImageViewerState(
            file = file,
            sourceDescription = sourceDescription,
            image = null,
            statusMessage = "Unable to load image preview.",
            errorMessage = "Unable to load image preview.",
        )
    }
}

internal fun showImageViewer(owner: JInternalFrame, file: ClientStoredFile) {
    RewriteImageViewerController(owner).show(file)
}

internal class RewriteImageViewerController(
    private val owner: JInternalFrame,
) {
    fun show(file: ClientStoredFile) {
        val desktopPane = SwingUtilities.getAncestorOfClass(JDesktopPane::class.java, owner) as? JDesktopPane
            ?: return
        val windowName = imageViewerWindowName(file)
        val existing = desktopPane.allFrames.firstOrNull { it.name == windowName && !it.isClosed } as? RewriteImageViewerWindow
        val state = buildImageViewerState(file)
        if (existing != null) {
            existing.render(state)
            existing.toFront()
            runCatching { existing.isSelected = true }
            return
        }

        val window = RewriteImageViewerWindow(file)
        window.render(state)
        desktopPane.add(window)
        window.setLocation(100, 100)
        window.isVisible = true
        window.toFront()
        runCatching { window.isSelected = true }
    }
}

internal class RewriteImageViewerWindow(
    file: ClientStoredFile,
) : JInternalFrame("Image Viewer", false, false, true, true) {
    private val viewerPanel = RewriteImageViewerPanel()

    init {
        name = imageViewerWindowName(file)
        defaultCloseOperation = DISPOSE_ON_CLOSE
        contentPane = viewerPanel
        render(buildImageViewerState(file))
    }

    fun render(state: RewriteImageViewerState) {
        title = "Image Viewer"
        viewerPanel.render(state)
        val width = state.image?.width ?: 450
        val height = state.image?.height ?: 450
        val frameWidth = width + 2
        val frameHeight = height + 54
        size = Dimension(frameWidth, frameHeight)
        preferredSize = size
        revalidate()
        repaint()
    }
}

internal class RewriteImageViewerPanel : JPanel(BorderLayout()) {
    private val nameValueLabel = JLabel("-").apply {
        name = "rewrite-image-viewer-name-value"
    }
    private val sourceValueLabel = JLabel("-").apply {
        name = "rewrite-image-viewer-source-value"
        foreground = Color(0x55, 0x55, 0x55)
    }
    private val statusLabel = JLabel(" ").apply {
        name = "rewrite-image-viewer-status"
    }
    private val errorLabel = JLabel(" ").apply {
        name = "rewrite-image-viewer-error"
        foreground = Color(0xAA, 0x22, 0x22)
    }
    private val imageCanvas = RewriteImageCanvas().apply {
        name = "rewrite-image-viewer-canvas"
    }

    init {
        name = "rewrite-image-viewer-panel"
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        val header = JPanel(BorderLayout(0, 4)).apply {
            isOpaque = false
            add(nameValueLabel, BorderLayout.NORTH)
            add(sourceValueLabel, BorderLayout.SOUTH)
        }
        val footer = JPanel(BorderLayout(0, 2)).apply {
            isOpaque = false
            add(statusLabel, BorderLayout.NORTH)
            add(errorLabel, BorderLayout.SOUTH)
        }
        add(header, BorderLayout.NORTH)
        add(JScrollPane(imageCanvas).apply {
            name = "rewrite-image-viewer-scroll"
            border = BorderFactory.createEmptyBorder()
        }, BorderLayout.CENTER)
        add(footer, BorderLayout.SOUTH)
    }

    fun render(state: RewriteImageViewerState) {
        nameValueLabel.text = state.file.name
        sourceValueLabel.text = state.sourceDescription.ifBlank { state.file.path }
        statusLabel.text = state.statusMessage
        errorLabel.text = state.errorMessage ?: " "
        imageCanvas.setImage(state.image)
        imageCanvas.revalidate()
    }
}

internal class RewriteImageCanvas : JPanel() {
    private var image: BufferedImage? = null

    fun setImage(image: BufferedImage?) {
        this.image = image
        revalidate()
        repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val currentImage = image ?: return
        graphics.drawImage(currentImage, 0, 0, this)
    }

    override fun getPreferredSize(): Dimension {
        val currentImage = image
        return if (currentImage == null) {
            Dimension(420, 280)
        } else {
            Dimension(currentImage.width, currentImage.height)
        }
    }
}

private fun loadBufferedImage(sourceDescription: String): BufferedImage? {
    val file = File(sourceDescription)
    if (file.isFile) {
        return runCatching { ImageIO.read(file) }.getOrNull()
    }

    runCatching { URL(sourceDescription) }.getOrNull()?.let { url ->
        return runCatching { ImageIO.read(url) }.getOrNull()
    }

    val resource = object {}.javaClass.classLoader.getResourceAsStream(sourceDescription.trimStart('/'))
    if (resource != null) {
        resource.use { input ->
            return runCatching { ImageIO.read(input) }.getOrNull()
        }
    }

    return null
}

private fun imageViewerWindowName(file: ClientStoredFile): String {
    return "rewrite-image-viewer-window-${sanitizeWindowKey(file.path)}"
}

private val IMAGE_FILE_EXTENSIONS = setOf(
    "bmp",
    "gif",
    "ico",
    "jpeg",
    "jpg",
    "png",
    "tif",
    "tiff",
    "webp",
)
