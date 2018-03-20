package io.squark.restannotationprocessor

import javax.lang.model.type.TypeMirror

data class PathEndpoint(val methods: Collection<HTTPMethod>, val accepts: Collection<String>,
                        val consumes: Collection<String>,
                        val returnType: TypeMirror, val parameters: Collection<Parameter>)