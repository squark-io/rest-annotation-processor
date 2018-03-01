package io.squark.jsrest4spring

import javax.lang.model.element.Element

data class RootNode(val element: Element, val annotation: Annotation?, val children: List<TreeNode>)