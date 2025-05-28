package com.example.socialme.controller

import com.example.socialme.model.PaymentVerificationRequest
import com.example.socialme.service.PayPalService
import com.example.socialme.service.UsuarioService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = ["*"])
class PaymentController(
    @Autowired private val payPalService: PayPalService,
    @Autowired private val usuarioService: UsuarioService
) {

    private val logger = LoggerFactory.getLogger(PaymentController::class.java)

    @PostMapping("/verify-premium")
    fun verifyAndUpgradePremium(
        @RequestHeader("Authorization") token: String,
        @RequestBody request: PaymentVerificationRequest
    ): ResponseEntity<Map<String, Any>> {

        logger.info("=== Verificando pago premium ===")
        logger.info("Username: ${request.username}")
        logger.info("Payment ID: ${request.paymentId}")

        return try {
            // Verificar el pago con PayPal
            logger.info("Verificando pago con PayPal...")
            val isPaymentValid = payPalService.verifyPayment(request.paymentId)

            if (isPaymentValid) {
                logger.info("✅ Pago verificado exitosamente")

                // Actualizar usuario a premium
                val updated = usuarioService.updatePremiumStatus(request.username, true)

                if (updated) {
                    logger.info("✅ Usuario actualizado a premium: ${request.username}")
                    ResponseEntity.ok(mapOf(
                        "success" to true,
                        "message" to "Premium activado exitosamente",
                        "paymentId" to request.paymentId
                    ))
                } else {
                    logger.error("❌ Error actualizando usuario a premium")
                    ResponseEntity.status(500).body(mapOf(
                        "success" to false,
                        "message" to "Error actualizando usuario"
                    ))
                }
            } else {
                logger.warn("❌ Pago no válido: ${request.paymentId}")
                ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "Pago no válido o no completado"
                ))
            }

        } catch (e: Exception) {
            logger.error("❌ Error verificando pago", e)
            ResponseEntity.status(500).body(mapOf(
                "success" to false,
                "message" to "Error del servidor: ${e.message}"
            ))
        }
    }

    @GetMapping("/test-paypal")
    fun testPayPalConnection(): ResponseEntity<Map<String, Any>> {
        return try {
            val token = payPalService.getAccessToken()

            if (token != null) {
                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "message" to "Conexión con PayPal exitosa"
                ))
            } else {
                ResponseEntity.status(500).body(mapOf(
                    "success" to false,
                    "message" to "No se pudo obtener token de PayPal"
                ))
            }
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf(
                "success" to false,
                "message" to "Error: ${e.message}"
            ))
        }
    }
}