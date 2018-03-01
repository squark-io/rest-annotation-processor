package io.squark.jsrest4spring

data class PathEndpoint(val method: String, val accepts: List<String>, val consumes: List<String>,
                        val returnType: Class<*>, val parameters: List<Parameter>)