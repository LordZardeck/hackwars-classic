package com.hackwars.gui

import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.parser.SVGLoader

fun svgResource(path: String): SVGDocument? {
    return runCatching {
        object {}.javaClass.classLoader.getResource(path)?.let { resourceUrl ->
            SVGLoader().load(resourceUrl)
        }
    }
        .onFailure {
            // TODO: Use logging system to record that we were unable to load the resource
        }
        .getOrNull()
}