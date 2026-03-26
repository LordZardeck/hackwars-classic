package com.hackwars.rewrite.client.shell

import java.awt.Image
import javax.swing.ImageIcon

private val shellIconCache = mutableMapOf<String, ImageIcon?>()

fun shellImageIcon(iconName: String): ImageIcon? {
    return shellIconCache.getOrPut(iconName) {
        object {}.javaClass.classLoader.getResource("images/shell/$iconName")?.let(::ImageIcon)
    }
}

fun shellScaledImage(iconName: String, width: Int, height: Int): ImageIcon? {
    val icon = shellImageIcon(iconName) ?: return null
    val scaled = icon.image.getScaledInstance(width, height, Image.SCALE_SMOOTH)
    return ImageIcon(scaled)
}
