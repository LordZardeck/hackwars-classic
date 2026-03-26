package com.hackwars.rewrite.client.ui

import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.parser.SVGLoader
import java.awt.Image
import java.net.URL
import javax.swing.ImageIcon

private val imageCache = mutableMapOf<String, ImageIcon?>()
private val svgCache = mutableMapOf<String, SVGDocument?>()

fun hackWarsImageIcon(vararg candidates: String): ImageIcon? {
    val cacheKey = candidates.joinToString("|")
    return imageCache.getOrPut(cacheKey) {
        resolveHackWarsResource(*candidates)?.let(::ImageIcon)
    }
}

fun hackWarsScaledImage(
    width: Int,
    height: Int,
    vararg candidates: String,
): ImageIcon? {
    val icon = hackWarsImageIcon(*candidates) ?: return null
    val scaled = icon.image.getScaledInstance(width, height, Image.SCALE_SMOOTH)
    return ImageIcon(scaled)
}

fun hackWarsSvg(vararg candidates: String): SVGDocument? {
    val cacheKey = candidates.joinToString("|")
    return svgCache.getOrPut(cacheKey) {
        resolveHackWarsResource(*candidates)?.let { resourceUrl ->
            SVGLoader().load(resourceUrl)
        }
    }
}

private fun resolveHackWarsResource(vararg candidates: String): URL? {
    val classLoader = object {}.javaClass.classLoader
    return candidates
        .map { it.trimStart('/') }
        .firstNotNullOfOrNull(classLoader::getResource)
}
