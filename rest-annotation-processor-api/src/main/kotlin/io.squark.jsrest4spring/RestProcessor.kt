package io.squark.jsrest4spring

import javax.lang.model.type.TypeMirror

interface RestProcessor {
  fun canHandle(qualifiedName: String): Boolean
  fun extractFullPaths(root: RootNode, child: TreeNode): Collection<String>
  fun extractMethods(root: RootNode, child: TreeNode): Collection<HTTPMethod>
  fun extractProduces(root: RootNode, child: TreeNode): Collection<String>
  fun extractConsumes(root: RootNode, child: TreeNode): Collection<String>
  fun extractReturnType(child: TreeNode): TypeMirror {
    return child.element.returnType
  }
  fun extractParameters(child: TreeNode): Collection<Parameter>
}