package com.hackwars.rewrite.client.login

import com.github.weisj.jsvg.SVGDocument
import com.hackwars.rewrite.client.ui.hackWarsSvg

fun svgResource(path: String): SVGDocument? {
    val normalized = path.trimStart('/')
    val parityPath = if (normalized.startsWith("images/")) {
        normalized.replaceFirst("images/", "images/legacy/")
    } else {
        "images/legacy/$normalized"
    }
    return hackWarsSvg(parityPath, normalized)
}
