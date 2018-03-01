package io.squark.jsrest4spring

import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

data class AnnotationMatch(val element: Element,
                           val annotation: TypeElement? = null)