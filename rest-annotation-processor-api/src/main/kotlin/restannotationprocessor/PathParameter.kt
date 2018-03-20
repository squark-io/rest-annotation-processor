package io.squark.restannotationprocessor

data class PathParameter(override val name: String) : Parameter {
  override val required = true
}