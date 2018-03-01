package io.squark.jsrest4spring

import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter

class TypeUtil(private val processingEnv: ProcessingEnvironment) {

  fun generateJsOutput(rawTypes: List<TypeMirror>): List<String> {
    val transformedTypes = mutableMapOf<String, TransformedType>()
    rawTypes.forEach { transformType(it, transformedTypes) }
    return transformedTypes.map { generateJsOutputForType(it.value) }
  }

  private fun generateJsOutputForType(type: TransformedType): String {
    val output = StringBuilder()
    output.appendln("/**")
    output.appendln(" * @class")
    type.fields.forEach { field ->
      output.appendln(" * @param {${extractJsTypeHint(field)}=} ${field.name}")
    }
    output.appendln(" */")
    output.append("function ${type.name}(")
    output.append(type.fields.joinToString { it.name })
    output.appendln(") {")
    type.fields.forEach { field ->
      output.appendln("  /** @type {?${extractJsTypeHint(field)}} */")
      output.appendln("  this.${field.name} = typeof ${field.name} !== 'undefined' ? ${field.name} : null;")
    }
    output.appendln("}")
    type.fields.forEach { field ->
      output.appendln()
      output.appendln("/**")
      output.appendln(" * Returns whether ${type.name}.${field.name} is set")
      output.appendln(" * @returns {boolean}")
      output.appendln(" */")
      output.appendln("${type.name}.prototype.has${field.name.capitalize()} = function () {")
      output.appendln("  return this.${field.name} !== null;")
      output.appendln("};")
    }
    return output.toString()
  }

  private fun extractJsTypeHint(field: TransformedField): String {
    return when (field.fieldType) {
      JSType.STRING, JSType.NUMBER, JSType.BOOLEAN -> {
        field.fieldType.jsName()
      }
      JSType.OBJECT -> {
        if (field.fieldObjectType != null) {
          field.fieldObjectType.name
        } else if (field.fieldMapType != null) {
          if (field.fieldMapType == JSType.OBJECT && field.fieldMapTypeObject != null) {
            "Object.<string, ${field.fieldMapTypeObject.name}>"
          } else if (field.fieldMapType == JSType.OBJECT) {
            throw AnnotationProcessingException("Unknown map type for field $field")
          } else {
            "Object.<string, ${field.fieldMapType.jsName()}>"
          }
        } else {
          throw AnnotationProcessingException("Unknown field object type for field $field")
        }
      }
      JSType.ARRAY -> {
        val arrayType = when (field.fieldArrayType!!) {
          JSType.STRING, JSType.NUMBER, JSType.BOOLEAN -> {
            field.fieldArrayType.jsName()
          }
          JSType.OBJECT -> field.fieldArrayOfObjectsType!!.name
          JSType.ARRAY -> TODO("Nested array types not yet handled. Raise issue if you need")
        }
        return "$arrayType[]"
      }
    }
  }

  private fun transformType(type: TypeMirror,
                            transformedTypes: MutableMap<String, TransformedType>): TransformedType {
    val element = processingEnv.typeUtils.asElement(type)
    val qualifiedName = (element as TypeElement).qualifiedName.toString()
    if (!transformedTypes.containsKey(qualifiedName)) {
      val name = element.simpleName.toString()
      val fields = ElementFilter.fieldsIn(element.enclosedElements)
      val transformedFields = mutableListOf<TransformedField>()
      fields.forEach { field ->
        val fieldName = field.simpleName.toString()
        val fieldType = getJSType(field.asType())
        val fieldObjectType = when (fieldType) {
          JSType.OBJECT -> {
            when {
              !(field.asType() as DeclaredType).isMap() -> transformType((field.asType()), transformedTypes)
              else -> null
            }
          }
          else -> null
        }
        val fieldMapType = when (fieldType == JSType.OBJECT && (field.asType() as DeclaredType).isMap()) {
          true -> getJSType((field.asType() as DeclaredType).typeArguments[1])
          false -> null
        }
        val fieldMapTypeObject = when (fieldMapType) {
          JSType.OBJECT -> transformType((field.asType() as DeclaredType).typeArguments[1], transformedTypes)
          else -> null
        }
        val fieldArrayType = when (fieldType) {
          JSType.ARRAY -> {
            when {
              field.asType().kind == TypeKind.DECLARED && (field.asType() as DeclaredType).isCollection() -> getJSType(
                (field.asType() as DeclaredType).typeArguments.first())
              field.asType().kind == TypeKind.ARRAY -> getJSType((field.asType() as ArrayType).componentType)
              else -> throw AnnotationProcessingException("Unknown Array type ${field.kind}")
            }
          }
          else -> null
        }
        val fieldArrayOfObjectsType = when (fieldArrayType) {
          JSType.OBJECT -> {
            if (field.asType().kind == TypeKind.DECLARED && (field.asType() as DeclaredType).isCollection()) {
              transformType((field.asType() as DeclaredType).typeArguments.first(), transformedTypes)
            } else if (field.asType().kind == TypeKind.ARRAY) {
              transformType((field.asType() as ArrayType).componentType, transformedTypes)
            } else {
              throw AnnotationProcessingException("Unknown Array type ${field.kind}")
            }
          }
          else -> null
        }
        //todo: add default value for non-object fields
        transformedFields.add(
          TransformedField(fieldName, fieldType, fieldObjectType, fieldMapType, fieldMapTypeObject, fieldArrayType,
            fieldArrayOfObjectsType))
      }
      transformedTypes[qualifiedName] = TransformedType(name, transformedFields.toList())
    }
    return transformedTypes[qualifiedName]!!
  }

  private data class TransformedField(val name: String, val fieldType: JSType, val fieldObjectType: TransformedType?,
                                      val fieldMapType: JSType?, val fieldMapTypeObject: TransformedType?,
                                      val fieldArrayType: JSType?, val fieldArrayOfObjectsType: TransformedType?)

  private data class TransformedType(val name: String, val fields: List<TransformedField>)

  private fun getJSType(type: TypeMirror): JSType {
    return when {
      type.isNumber() -> JSType.NUMBER
      type.isString() -> JSType.STRING
      type.isBoolean() -> JSType.BOOLEAN
      type.kind == TypeKind.DECLARED -> {
        if ((type as DeclaredType).isCollection()) {
          JSType.ARRAY
        } else {
          JSType.OBJECT
        }
      }
      type.kind == TypeKind.ARRAY -> JSType.ARRAY
      else -> throw AnnotationProcessingException(
        "Failed to handle field type ${type.kind} ($type)")
    }
  }

  private fun TypeMirror.isNumber(): Boolean = when (this.kind) {
    TypeKind.BYTE, TypeKind.SHORT, TypeKind.INT, TypeKind.LONG, TypeKind.CHAR, TypeKind.FLOAT, TypeKind.DOUBLE -> true
    TypeKind.DECLARED -> processingEnv.typeUtils.isAssignable(this,
      processingEnv.elementUtils.getTypeElement(Number::class.javaObjectType.name).asType())
    else -> false
  }

  private fun TypeMirror.isBoolean(): Boolean = when (this.kind) {
    TypeKind.BOOLEAN -> true
    TypeKind.DECLARED -> processingEnv.typeUtils.isAssignable(this,
      processingEnv.elementUtils.getTypeElement(Boolean::class.javaObjectType.name).asType())
    else -> false
  }

  private fun TypeMirror.isString(): Boolean =
    processingEnv.typeUtils.isSameType(this,
      processingEnv.elementUtils.getTypeElement(String::class.javaObjectType.name).asType())

  private fun DeclaredType.isCollection(): Boolean {
    val collectionType = processingEnv.elementUtils.getTypeElement(Collection::class.javaObjectType.name).asType()
    val erasedType = processingEnv.typeUtils.erasure(this)
    return processingEnv.typeUtils.isAssignable(erasedType, collectionType)
  }

  private fun DeclaredType.isMap(): Boolean {
    val mapType = processingEnv.elementUtils.getTypeElement(Map::class.javaObjectType.name).asType()
    val erasedType = processingEnv.typeUtils.erasure(this)
    return processingEnv.typeUtils.isAssignable(erasedType, mapType)
  }
}