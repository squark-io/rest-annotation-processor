package io.squark.jsrest4spring

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
    val pathTree = mutableMapOf<String, Any>()
    tree.forEach { root ->
      root.children.forEach { child ->
        val fullPaths = processor.extractFullPaths(root, child)
        val methods = processor.extractMethods(root, child)
        val accepts = processor.extractProduces(root, child)
        val consumes = processor.extractConsumes(root, child)
        val rawTypes = mutableListOf<TypeMirror>()
        val returnType = processor.extractReturnType(child)
        val parameters = processor.extractParameters(child)

        //todo add PathNode/PathEndpoint for everything. PathNode should probably have list of endpoints.
        //todo: extract type handling to separate method
        rawTypes.add(returnType)
        rawTypes.addAll(parameters.filterIsInstance<BodyParameter>().map { it.type })

        val output = StringBuilder()
        output.appendln("(function () {")
        output.appendln("  \"use strict\";")
        output.appendln()
        val typeUtil = TypeUtil(processingEnv)
        val typesJoined = typeUtil.generateJsOutput(rawTypes).joinToString(separator = "\n")
        typesJoined.lines().forEach { line ->
          if (line.trim().isNotEmpty()) {
            output.appendln("  $line")
          } else {
            output.appendln()
          }
        }
        output.appendln("})();")
        writeToFile("classes.js", output.toString())
      }
    }
  }

  private fun writeToFile(fileName: String, body: String) {
    val targetFile = File("src-gen/main/js/$fileName")
    targetFile.parentFile.mkdirs()
    if (targetFile.exists()) {
      targetFile.delete()
    }
    targetFile.createNewFile()
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
