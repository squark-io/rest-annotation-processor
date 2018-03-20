package io.squark.restannotationprocessor.sample

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication(scanBasePackages = ["io.squark.restannotationprocessor"])
open class Main

fun main(args: Array<String>) {
  SpringApplication.run(Main::class.java, *args)
}