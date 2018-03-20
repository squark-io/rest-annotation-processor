package io.squark.restannotationprocessor

import com.google.auto.service.AutoService
import java.io.File
import java.util.ServiceLoader
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

@AutoService(Processor::class)
class AnnotationProcessor : AbstractProcessor() {

  private val restProcessors: List<RestProcessor> =
    ServiceLoader.load(RestProcessor::class.java, AnnotationProcessor::class.java.classLoader).toList()

  override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
    val processors = mutableMapOf<RestProcessor, MutableList<AnnotationMatch>>()
    annotations.forEach { annotation: TypeElement ->
      val restProcessor = getProcessor(annotation)
      restProcessor?.let {
        val elements = roundEnv.getElementsAnnotatedWith(annotation)
        if (elements.size > 0) {
          processors.getOrPut(restProcessor, { mutableListOf() })
            .addAll(elements.map { AnnotationMatch(it, annotation) })
        }
      }
    }
    processors.forEach { processor, elements -> handleElements(processor, elements) }
    return false //Don't claim classes as other annotation processors might be interested
  }

  private fun getProcessor(annotation: TypeElement): RestProcessor? {
    val validProcessors = restProcessors.filter { it.canHandle(annotation.qualifiedName.toString()) }
    return when (validProcessors.size) {
      1 -> validProcessors.single()
      0 -> null
      else -> throw AnnotationProcessingException(
        "Found more than one valid processor for ${annotation.qualifiedName}, [${validProcessors.joinToString()}]")
    }
  }

  override fun getSupportedAnnotationTypes(): MutableSet<String> {
    return mutableSetOf(
      "org.springframework.web.bind.annotation.RequestMapping") //TODO: Get from processor implementations
  }

  fun handleElements(processor: RestProcessor, elements: List<AnnotationMatch>) {
    val tree = buildTreeOfMatches(elements)
    val typesFound = mutableSetOf<TypeMirror>()
    val pathTree = mutableSetOf<PathNode>()
    tree.forEach { root ->
      root.children.forEach { child ->
        val fullPaths = processor.extractFullPaths(root, child)
        val methods = processor.extractMethods(root, child)
        val accepts = processor.extractProduces(root, child)
        val consumes = processor.extractConsumes(root, child)
        val returnType = processor.extractReturnType(child)
        val parameters = processor.extractParameters(child)

        typesFound.add(returnType)
        typesFound.addAll(parameters.filterIsInstance<BodyParameter>().map { it.type })

        fullPaths.forEach { fullPath ->
          val splitPath = fullPath.split("/").map { "$it/" }
          var current = pathTree.singleOrNull { it.path == splitPath[0] }
          if (current == null) {
            current = PathNode(root.element.simpleName.toString(), splitPath[0])
            pathTree.add(current)
          }
          var previousPath = current.path
          for (i in 0 until splitPath.size) {
            val currentPath = (previousPath + splitPath[i]).replace("//", "/")
            if (currentPath != previousPath) {
              var childNode = current!!.children.singleOrNull { it.path == currentPath }
              if (childNode == null) {
                childNode = PathNode(child.element.simpleName.toString(), currentPath)
                current.children.add(childNode)
              }
              current = childNode
            }
            previousPath = currentPath
            if (i == splitPath.lastIndex) {
              if (current!!.endpoint != null) {
                throw AnnotationProcessingException(
                  "Multiple endpoints on the same resource is not supported. $currentPath")
              }
              val endpoint = PathEndpoint(methods, accepts, consumes, returnType, parameters)
              current.endpoint = endpoint
            }
          }
        }
      }
    }
    //Generate types
    val typeUtil = TypeUtil(processingEnv)
    val typesJoined = typeUtil.generateJsOutput(typesFound).joinToString(separator = "\n")
    writeToFile("classes.js", typesJoined)

    //Generate services
    val pathsAsStrings = mutableListOf<String>()
    pathTree.forEach {
      pathsAsStrings.add(pathNodeToJs(null, it, "    "))
    }
    val servicesBuilder = StringBuilder()
    servicesBuilder.appendln("/**")
    servicesBuilder.appendln(" *")
    servicesBuilder.appendln(" * @class")
    servicesBuilder.appendln(" * @param {string} [baseURL]")
    servicesBuilder.appendln(" * @constructor")
    pathTree.forEach { servicesBuilder.appendln(" * @property {${it.name}} ${it.name}") }
    servicesBuilder.appendln(" */")
    servicesBuilder.appendln("var RestServices = function (baseURL) {")
    servicesBuilder.appendln("  var config = {")
    servicesBuilder.appendln("    /** @type {string} */")
    servicesBuilder.appendln("    baseURL: baseURL || ''")
    servicesBuilder.appendln("  };")
    servicesBuilder.appendln("  return {")
    val servicesJoined = pathsAsStrings.joinToString(separator = ",\n")
    servicesBuilder.append(servicesJoined)
    servicesBuilder.appendln("  }")
    servicesBuilder.appendln("};")
    writeToFile("services.js", servicesBuilder.toString())
  }

  private fun pathNodeToJs(parent: PathNode?, pathNode: PathNode, prefix: String): String {
    val output = StringBuilder()
    //pathNode.children.forEach { output.appendln("$prefix/** @property ${it.name} */") }
    val singleMethod = pathNode.endpoint?.methods?.size == 1
    val parameters = if (pathNode.endpoint != null) pathNode.endpoint!!.parameters.filter { (it is PathParameter) || (it is QueryParameter) } else emptyList()
    if (singleMethod) {
      //todo: handle headers
      //todo: handle path parameters by building a new path with replace on {pathparamwhatever}
      //todo: handle query parameters by adding ?a=b&c=d to url
      //todo: allow Number type parameters. Currently only string
      //todo: handle default values for parameters
      output.appendln("$prefix/**")
      if (parameters.isNotEmpty()) {
        output.appendln("$prefix * @param parameters Parameters to pass to the method")
      }
      parameters.forEach { parameter ->
        if (parameter.required) {
          output.appendln("$prefix * @param {string} parameters.${parameter.name}")
        } else {
          output.appendln("$prefix * @param {string} [parameters.${parameter.name}]")
        }
      }
      output.appendln("$prefix * @returns {Promise<Response>}")
      output.appendln("$prefix */")
      val parametersParameter = if (parameters.isNotEmpty()) "parameters" else ""
      output.appendln("$prefix${pathNode.name}: function ($parametersParameter) {")
    } else {
      output.appendln("$prefix/**")
      output.appendln("$prefix * @class")
      output.appendln("$prefix * @inner")
      if (parent != null) {
        output.appendln("$prefix * @memberOf ${parent.name}")
      } else {
        output.appendln("$prefix * @memberOf RestServices")
      }
      output.appendln("$prefix */")
      output.appendln("$prefix${pathNode.name}: {")
    }
    if (pathNode.endpoint != null) {
      val queryParams = parameters.filterIsInstance<QueryParameter>()
      if (queryParams.isNotEmpty()) {
        output.appendln("$prefix  var queryParams = {")
        queryParams.forEach {
          val defaultValue = if (it.defaultValue != null) " || '${it.defaultValue}'" else ""
          output.appendln("$prefix    ${it.name}: parameters.${it.name}$defaultValue")
        }
        output.appendln("$prefix  };")
        output.appendln("$prefix  var queryString = Object.keys(queryParams).filter(function (key) {")
        output.appendln("$prefix    return queryParams[key] !== undefined && queryParams[key] !== null;")
        output.appendln("$prefix  }).map(function (key) {")
        output.appendln("$prefix    return key + '=' + encodeURIComponent(queryParams[key]);")
        output.appendln("$prefix  }).join('&');")
      }
      val queryString = if (queryParams.isNotEmpty()) "?' + queryString" else "'"
      val nodePath = "/${pathNode.path}".replace("//", "/")
      if (singleMethod) {
        output.appendln("$prefix  return fetch(config.baseURL + '$nodePath$queryString, {")
        output.appendln("$prefix    method: '${pathNode.endpoint!!.methods.first().name.toLowerCase()}'")
        val bodyParameter = pathNode.endpoint!!.parameters.filterIsInstance<BodyParameter>().singleOrNull()
        bodyParameter?.let {
          output.appendln(",")
          output.appendln("$prefix    body: JSON.stringify(${bodyParameter.name})")
        }
        output.appendln("$prefix  })")
      } else {
        var comma = ""
        pathNode.endpoint!!.methods.forEach { method ->
          val endpointName = method.name.toLowerCase() + pathNode.name.capitalize()
          output.append("$comma$prefix  $endpointName: function (")
          output.append(pathNode.endpoint!!.parameters.joinToString { it.name })
          output.appendln(") {")
          //todo: handle headers
          //todo: handle path parameters by building a new path with replace on {pathparamwhatever}
          //todo: handle query parameters by adding ?a=b&c=d to url
          //todo: allow Number type parameters. Currently only string
          //todo: handle default values for parameters
          output.appendln("$prefix    return fetch(config.baseURL + '$nodePath', {")
          output.appendln("$prefix      method: '${method.name.toLowerCase()}'")
          val bodyParameter = pathNode.endpoint!!.parameters.filterIsInstance<BodyParameter>().singleOrNull()
          bodyParameter?.let {
            output.appendln(",")
            output.appendln("$prefix      body: JSON.stringify(${bodyParameter.name})")
          }
          output.appendln("$prefix    })")
          output.appendln("$prefix  }")
          comma = ",\n"
        }
      }
    }
    pathNode.children.forEach { child ->
      output.append(pathNodeToJs(pathNode, child, "$prefix  "))
    }
    output.appendln("$prefix}")
    return output.toString()
  }

  private fun writeToFile(fileName: String, body: String) {
    val targetFile = File("src-gen/main/js/$fileName")
    targetFile.parentFile.mkdirs()
    if (targetFile.exists()) {
      targetFile.delete()
    }
    targetFile.createNewFile()
    val output = StringBuilder()
    output.appendln("(function () {")
    output.appendln("  \"use strict\";")
    output.appendln()
    body.lines().forEach { line ->
      if (line.trim().isNotEmpty()) {
        output.appendln("  $line")
      } else {
        output.appendln()
      }
    }
    output.appendln("})();")
    targetFile.writeText("${body.trim()}\n")
  }


  private fun buildTreeOfMatches(elements: List<AnnotationMatch>): MutableList<RootNode> {
    val mutableElements = elements.toMutableList()
    val roots = mutableElements.filter { it.element.kind == ElementKind.CLASS }
      .associateBy({ it }, { mutableListOf<AnnotationMatch>() }).toMutableMap()

    val executables =
      mutableElements.filter { it.element.kind == ElementKind.METHOD }.toMutableList()
    executables.removeIf { executable: AnnotationMatch ->
      val root = roots.entries.singleOrNull { it.key.element == executable.element.enclosingElement }
      when {
        root != null -> {
          root.value.add(executable)
          return@removeIf true
        }
        else -> return@removeIf false
      }
    }
    executables.forEach {
      val rootElement = it.element.enclosingElement
      val rootMatch = AnnotationMatch(rootElement)
      val children = roots.getOrPut(rootMatch, { mutableListOf() })
      children.add(it)
    }
    val tree = mutableListOf<RootNode>()
    roots.forEach { root, children ->
      val annotation = if (root.annotation != null) {
        @Suppress("UNCHECKED_CAST")
        root.element.getAnnotation(
          Class.forName(root.annotation!!.qualifiedName.toString()) as Class<Annotation>)
      } else {
        null
      }
      tree.add(RootNode(root.element, annotation, children.map { toTreeNode(it) }))
    }

    return tree
  }

  private fun toTreeNode(match: AnnotationMatch): TreeNode {
    @Suppress("UNCHECKED_CAST")
    val annotation = match.element.getAnnotation(
      Class.forName(match.annotation!!.qualifiedName.toString()) as Class<Annotation>)
    return TreeNode(match.element as ExecutableElement, annotation)
  }
}
