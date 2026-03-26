package com.hackwars.rewrite.client.login

import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.parser.SVGLoader

fun svgResource(path: String): SVGDocument? {
    return runCatching {
        object {}.javaClass.classLoader.getResource(path)?.let { resourceUrl ->
            SVGLoader().load(resourceUrl)
        }
    }.getOrNull()
}
