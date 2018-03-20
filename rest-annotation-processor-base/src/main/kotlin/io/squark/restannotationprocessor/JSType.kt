package io.squark.restannotationprocessor

enum class JSType {
  STRING, NUMBER, BOOLEAN, OBJECT, ARRAY;

  fun jsName() = name.toLowerCase()
}