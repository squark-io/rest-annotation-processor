package io.squark.jsrest4spring

data class PathParameter(override val name: String) : Parameter {
  override val required = true
}