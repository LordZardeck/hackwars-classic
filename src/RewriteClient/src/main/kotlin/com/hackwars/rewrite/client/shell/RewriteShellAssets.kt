package com.hackwars.rewrite.client.shell

import com.hackwars.rewrite.client.ui.hackWarsImageIcon
import com.hackwars.rewrite.client.ui.hackWarsScaledImage
import javax.swing.ImageIcon

private val shellIconCache = mutableMapOf<String, ImageIcon?>()

fun shellImageIcon(iconName: String): ImageIcon? {
    return shellIconCache.getOrPut(iconName) {
        hackWarsImageIcon(
            "images/legacy/$iconName",
            "images/shell/$iconName",
        )
    }
}

fun shellScaledImage(iconName: String, width: Int, height: Int): ImageIcon? {
    return hackWarsScaledImage(
        width = width,
        height = height,
        "images/legacy/$iconName",
        "images/shell/$iconName",
    )
}
