package io.squark.jsrest4spring.sample

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication(scanBasePackages = ["io.squark.jsrest4spring"])
open class Main

fun main(args: Array<String>) {
  SpringApplication.run(Main::class.java, *args)
}