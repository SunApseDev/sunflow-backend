package kr.sunapse.sunflow

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SunflowApplication

fun main(args: Array<String>) {
    runApplication<SunflowApplication>(*args)
}
