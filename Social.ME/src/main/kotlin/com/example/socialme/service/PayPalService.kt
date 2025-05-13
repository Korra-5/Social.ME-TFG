package com.example.socialme.service

import com.example.socialme.config.PayPalConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.*

@Service
class PayPalService(
    @Autowired private val payPalConfig: PayPalConfig
) {
    private val restTemplate = RestTemplate()

    fun getAccessToken(): String? {
        val url = "${payPalConfig.baseUrl}/v1/oauth2/token"

        val headers = HttpHeaders()
        val auth = "${payPalConfig.clientId}:${payPalConfig.clientSecret}"
        val encodedAuth = Base64.getEncoder().encodeToString(auth.toByteArray())
        headers.set("Authorization", "Basic $encodedAuth")
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

        val body = "grant_type=client_credentials"
        val entity = HttpEntity(body, headers)

        return try {
            val response = restTemplate.postForEntity(url, entity, Map::class.java)
            val responseBody = response.body as? Map<String, Any>
            responseBody?.get("access_token") as? String
        } catch (e: Exception) {
            null
        }
    }

    fun verifyPayment(paymentId: String): Boolean {
        val token = getAccessToken() ?: return false

        val url = "${payPalConfig.baseUrl}/v1/payments/payment/$paymentId"

        val headers = HttpHeaders()
        headers.set("Authorization", "Bearer $token")
        headers.contentType = MediaType.APPLICATION_JSON

        val entity = HttpEntity<String>(headers)

        return try {
            val response = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java)
            val payment = response.body as? Map<String, Any>
            payment?.get("state") == "approved"
        } catch (e: Exception) {
            false
        }
    }
}