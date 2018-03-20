package io.squark.restannotationprocessor

data class PathNode(val name: String, val path: String, val children: MutableCollection<PathNode> = mutableListOf(),
                    var endpoint: PathEndpoint? = null)
