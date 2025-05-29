package com.example.socialme.service

import com.example.socialme.config.PayPalConfig
import org.slf4j.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(PayPalService::class.java)

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
            logger.info("Obteniendo token de PayPal...")
            val response = restTemplate.postForEntity(url, entity, Map::class.java)
            val responseBody = response.body as? Map<String, Any>
            val token = responseBody?.get("access_token") as? String
            logger.info("✅ Token obtenido exitosamente")
            token
        } catch (e: Exception) {
            logger.error("❌ Error obteniendo token de PayPal", e)
            null
        }
    }

    // Crear orden de PayPal
    fun createOrder(amount: String): Map<String, Any>? {
        val token = getAccessToken() ?: return null
        val url = "${payPalConfig.baseUrl}/v2/checkout/orders"

        val headers = HttpHeaders()
        headers.set("Authorization", "Bearer $token")
        headers.contentType = MediaType.APPLICATION_JSON

        val orderData = mapOf(
            "intent" to "CAPTURE",
            "purchase_units" to listOf(
                mapOf(
                    "amount" to mapOf(
                        "currency_code" to "EUR",
                        "value" to amount
                    ),
                    "description" to "SocialMe Premium Subscription"
                )
            ),
            "application_context" to mapOf(
                "return_url" to "https://socialme.app/success",
                "cancel_url" to "https://socialme.app/cancel"
            )
        )

        val entity = HttpEntity(orderData, headers)

        return try {
            logger.info("Creando orden de PayPal por €$amount")
            val response = restTemplate.postForEntity(url, entity, Map::class.java)
            val responseBody = response.body as? Map<String, Any>
            logger.info("✅ Orden creada: ${responseBody?.get("id")}")
            responseBody
        } catch (e: Exception) {
            logger.error("❌ Error creando orden", e)
            null
        }
    }

    // Simular aprobación de pago (normalmente se hace en PayPal web)
    fun simulatePaymentApproval(orderId: String): Boolean {
        logger.info("🎭 Simulando aprobación de pago para orden: $orderId")

        // En un entorno real, este paso se hace en la web de PayPal
        // Para la simulación, simplemente devolvemos true
        logger.info("✅ Pago simulado como aprobado")
        return true
    }

    // Capturar pago
    fun capturePayment(orderId: String): Boolean {
        val token = getAccessToken() ?: return false
        val url = "${payPalConfig.baseUrl}/v2/checkout/orders/$orderId/capture"

        val headers = HttpHeaders()
        headers.set("Authorization", "Bearer $token")
        headers.contentType = MediaType.APPLICATION_JSON

        val entity = HttpEntity("{}", headers)

        return try {
            logger.info("Capturando pago para orden: $orderId")
            val response = restTemplate.postForEntity(url, entity, Map::class.java)
            val responseBody = response.body as? Map<String, Any>
            val status = responseBody?.get("status") as? String

            val success = status == "COMPLETED"
            if (success) {
                logger.info("✅ Pago capturado exitosamente")
            } else {
                logger.warn("⚠️ Pago no completado. Status: $status")
            }
            success
        } catch (e: Exception) {
            logger.error("❌ Error capturando pago", e)
            false
        }
    }

    // Verificar estado de la orden
    fun verifyPayment(orderId: String): Boolean {
        val token = getAccessToken() ?: return false
        val url = "${payPalConfig.baseUrl}/v2/checkout/orders/$orderId"

        val headers = HttpHeaders()
        headers.set("Authorization", "Bearer $token")
        headers.contentType = MediaType.APPLICATION_JSON

        val entity = HttpEntity<String>(headers)

        return try {
            logger.info("Verificando estado de orden: $orderId")
            val response = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java)
            val payment = response.body as? Map<String, Any>
            val status = payment?.get("status") as? String

            logger.info("Estado de la orden: $status")
            status == "COMPLETED" || status == "APPROVED"
        } catch (e: Exception) {
            logger.error("❌ Error verificando pago", e)
            false
        }
    }

    // Flujo completo de simulación
    fun simulateCompletePurchase(amount: String): PayPalPurchaseResult {
        try {
            logger.info("=== INICIANDO SIMULACIÓN DE COMPRA PAYPAL ===")

            // 1. Crear orden
            val orderResponse = createOrder(amount)
            if (orderResponse == null) {
                return PayPalPurchaseResult.Error("Error creando orden")
            }

            val orderId = orderResponse["id"] as? String
            if (orderId == null) {
                return PayPalPurchaseResult.Error("No se pudo obtener ID de orden")
            }

            // 2. Simular aprobación
            if (!simulatePaymentApproval(orderId)) {
                return PayPalPurchaseResult.Error("Error en aprobación simulada")
            }

            // 3. Capturar pago
            if (!capturePayment(orderId)) {
                return PayPalPurchaseResult.Error("Error capturando pago")
            }

            logger.info("✅ SIMULACIÓN COMPLETADA EXITOSAMENTE")
            return PayPalPurchaseResult.Success(orderId)

        } catch (e: Exception) {
            logger.error("❌ Error en simulación completa", e)
            return PayPalPurchaseResult.Error("Error: ${e.message}")
        }
    }
}

sealed class PayPalPurchaseResult {
    data class Success(val orderId: String) : PayPalPurchaseResult()
    data class Error(val message: String) : PayPalPurchaseResult()
}