package io.squark.jsrest4spring

enum class JSType {
  STRING, NUMBER, BOOLEAN, OBJECT, ARRAY;

  fun jsName() = name.toLowerCase()
}