package io.squark.jsrest4spring

data class PathNode(val path: String, val children: List<PathNode> = mutableListOf(),
                    val endpoint: PathEndpoint?)