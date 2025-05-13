package com.example.socialme.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "paypal")
class PayPalConfig {
    var clientId: String = ""
    var clientSecret: String = ""
    var environment: String = "sandbox"
    var baseUrl: String = "https://api-m.sandbox.paypal.com"

    fun isProduction(): Boolean = environment == "live"
    fun isSandbox(): Boolean = environment == "sandbox"
}