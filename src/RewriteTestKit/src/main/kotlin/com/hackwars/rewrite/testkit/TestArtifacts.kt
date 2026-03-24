package com.hackwars.rewrite.testkit

data class RewriteFailureArtifact(
    val name: String,
    val path: String,
)

class ArtifactSink {
    private val artifacts = mutableListOf<RewriteFailureArtifact>()

    fun record(name: String, path: String) {
        artifacts += RewriteFailureArtifact(name = name, path = path)
    }

    fun snapshot(): List<RewriteFailureArtifact> = artifacts.toList()
}
