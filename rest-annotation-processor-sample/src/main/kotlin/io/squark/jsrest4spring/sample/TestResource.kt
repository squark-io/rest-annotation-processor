package io.squark.jsrest4spring.sample

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
class TestResource {
  @RequestMapping
  fun stuff(): SomeClass {
    val embedded = EmbeddedClass("hejhej")
    return SomeClass("lalala", false, 1L, arrayOf(), listOf(), embedded, listOf(), arrayOf(), listOf(), arrayOf(),
      listOf(), arrayOf(), mapOf(), mapOf(), mapOf(), mapOf())
  }
}

class SomeClass(val information: String, val isThisReal: Boolean, val aNumber: Long, val andAnArray: Array<String>,
                val andAListOfString: List<String>, val embedded: EmbeddedClass,
                val listOfObject: List<EmbeddedClass>, val arrayOfObject: Array<EmbeddedClass>,
                val listOfNumber: List<Long>, val arrayOfNumber: Array<Long>,
                val listOfBoolean: List<Boolean>, val arrayOfBoolean: Array<Boolean>,
                val mapOfStrings: Map<String, String>, val mapOfNumbers: Map<String, Long>,
                val mapOfObjects: Map<String, EmbeddedClass>, val mapOfBoolean: Map<String, Boolean>)

class EmbeddedClass(val stuffToSay: String)
