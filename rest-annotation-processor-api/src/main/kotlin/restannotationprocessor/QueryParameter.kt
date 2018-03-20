package io.squark.restannotationprocessor

data class QueryParameter(override val name: String, override val required: Boolean, val defaultValue: String?) : Parameter