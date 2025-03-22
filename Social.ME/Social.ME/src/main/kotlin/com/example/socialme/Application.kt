package com.example.socialme

import com.example.socialme.security.RSAKeysProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(RSAKeysProperties::class)
class Application

fun main(args: Array<String>) {
	runApplication<Application>(*args)
}
