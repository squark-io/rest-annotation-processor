package io.squark.restannotationprocessor

import javax.lang.model.element.ExecutableElement

data class TreeNode(val element: ExecutableElement, val annotation: Annotation)
