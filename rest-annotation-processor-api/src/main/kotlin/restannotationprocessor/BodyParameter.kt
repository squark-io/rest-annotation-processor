package io.squark.restannotationprocessor

import javax.lang.model.type.TypeMirror

data class BodyParameter(override val name: String, val type: TypeMirror) : Parameter {
  override val required = true
}