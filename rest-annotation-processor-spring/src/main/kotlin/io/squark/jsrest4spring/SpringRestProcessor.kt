package io.squark.jsrest4spring

import com.google.auto.service.AutoService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ValueConstants

@AutoService(RestProcessor::class)
class SpringRestProcessor : RestProcessor {
  override fun extractFullPaths(root: RootNode, child: TreeNode): Collection<String> {
    val paths = mutableListOf<String>()
    if (root.annotation != null) {
      val rootRequestMapping = root.annotation as RequestMapping
      getPaths(rootRequestMapping).forEach { rootPath ->
        val childRequestMapping = child.annotation as RequestMapping
        getPaths(childRequestMapping).forEach { childPath ->
          paths.add("$rootPath$childPath".replace("//", "/"))
        }
      }
    } else {
      val childRequestMapping = child.annotation as RequestMapping
      getPaths(childRequestMapping).forEach { childPath ->
        paths.add(childPath)
      }
    }
    return paths.toList()
  }

  private fun getPaths(requestMapping: RequestMapping): Collection<String> {
    val paths = if (requestMapping.value.isEmpty()) {
      requestMapping.path.toList()
    } else {
      requestMapping.value.toList()
    }
    return if (paths.isEmpty()) {
      listOf("/")
    } else paths
  }

  override fun extractMethods(root: RootNode, child: TreeNode): Collection<HTTPMethod> {
    val methods = mutableListOf<HTTPMethod>()
    if (root.annotation != null) {
      val rootRequestMapping = root.annotation as RequestMapping
      methods.addAll(rootRequestMapping.method.map { HTTPMethod.valueOf(it.name) })
    }
    val childRequestMapping = child.annotation as RequestMapping
    methods.addAll(childRequestMapping.method.map { HTTPMethod.valueOf(it.name) })
    return methods.toList()
  }

  override fun extractProduces(root: RootNode, child: TreeNode): Collection<String> {
    val produces = mutableListOf<String>()
    if (root.annotation != null) {
      val rootRequestMapping = root.annotation as RequestMapping
      produces.addAll(rootRequestMapping.produces)
    }
    val childRequestMapping = child.annotation as RequestMapping
    produces.addAll(childRequestMapping.produces)
    return produces.toList()
  }

  override fun extractConsumes(root: RootNode, child: TreeNode): Collection<String> {
    val consumes = mutableListOf<String>()
    if (root.annotation != null) {
      val rootRequestMapping = root.annotation as RequestMapping
      consumes.addAll(rootRequestMapping.consumes)
    }
    val childRequestMapping = child.annotation as RequestMapping
    consumes.addAll(childRequestMapping.produces)
    return consumes.toList()
  }

  override fun extractParameters(child: TreeNode): Collection<Parameter> {
    val parameters = mutableListOf<Parameter>()
    child.element.parameters.forEach { parameter ->
      val pathVariable = parameter.getAnnotation(PathVariable::class.java)
      if (pathVariable != null) {
        val name = when {
          pathVariable.name.isNotEmpty() -> pathVariable.name
          pathVariable.value.isNotEmpty() -> pathVariable.value
          else -> parameter.simpleName.toString()
        }
        parameters.add(PathParameter(name))
      }
      val requestParam = parameter.getAnnotation(RequestParam::class.java)
      if (requestParam != null) {
        val name = when {
          requestParam.name.isNotEmpty() -> requestParam.name
          requestParam.value.isNotEmpty() -> requestParam.value
          else -> parameter.simpleName.toString()
        }
        val defaultValue = if (requestParam.defaultValue == ValueConstants.DEFAULT_NONE) null else requestParam.defaultValue
        parameters.add(QueryParameter(name, requestParam.required, defaultValue))
      }
      val requestBody = parameter.getAnnotation(RequestBody::class.java)
      if (requestBody != null) {
        parameters.add(BodyParameter(parameter.simpleName.toString(), parameter.asType()))
      }
    }
    return parameters.toList()
  }

  private val validAnnotations = listOf(RestController::class.java.name, RequestMapping::class.java.name,
    GetMapping::class.java, PostMapping::class.java, PutMapping::class.java, DeleteMapping::class.java,
    PatchMapping::class.java)

  override fun canHandle(qualifiedName: String): Boolean {
    return qualifiedName in validAnnotations
  }

}
