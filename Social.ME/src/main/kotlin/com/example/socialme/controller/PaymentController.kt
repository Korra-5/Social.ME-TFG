package com.example.socialme.controller

import com.example.socialme.service.UsuarioService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = ["*"], allowedHeaders = ["*"])
class PaymentController(
    @Autowired private val usuarioService: UsuarioService
) {
    private val logger = LoggerFactory.getLogger(PaymentController::class.java)

    @PostMapping("/simulate-purchase")
    fun simulatePurchase(
        @RequestHeader("Authorization") token: String,
        @RequestBody request: SimulatePurchaseRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Map<String, Any>> {

        logger.info("=== NUEVA SOLICITUD DE SIMULACIÓN ===")
        logger.info("IP Cliente: ${httpRequest.remoteAddr}")
        logger.info("User-Agent: ${httpRequest.getHeader("User-Agent")}")
        logger.info("Username: ${request.username}")
        logger.info("Amount: €${request.amount}")
        logger.info("Token: ${token.take(20)}...")

        return try {
            // Validar token
            if (token.isBlank() || !token.startsWith("Bearer ")) {
                logger.error("❌ Token inválido")
                return ResponseEntity.status(401).body(mapOf(
                    "success" to false,
                    "message" to "Token de autorización inválido"
                ))
            }

            // Validar datos de entrada
            if (request.username.isBlank()) {
                logger.error("❌ Username vacío")
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "Username no puede estar vacío"
                ))
            }

            if (request.amount.isBlank()) {
                logger.error("❌ Amount vacío")
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "Amount no puede estar vacío"
                ))
            }

            // Simular procesamiento PayPal
            logger.info("💳 Simulando procesamiento PayPal...")
            Thread.sleep(2500) // Simular latencia real

            // Generar ID de orden simulado
            val mockOrderId = "PAYPAL_SIM_${System.currentTimeMillis()}_${(1000..9999).random()}"
            logger.info("📝 Orden simulada creada: $mockOrderId")

            // Simular éxito/fallo (95% éxito)
            val simulateSuccess = (1..100).random() <= 95

            if (!simulateSuccess) {
                logger.warn("⚠️ Simulando fallo de PayPal")
                return ResponseEntity.status(402).body(mapOf(
                    "success" to false,
                    "message" to "Simulación de fallo en PayPal - Inténtalo de nuevo"
                ))
            }

            // Actualizar usuario a premium
            logger.info("👤 Actualizando usuario a premium...")
            val updated = usuarioService.updatePremiumStatus(request.username, true)

            if (updated) {
                logger.info("✅ SIMULACIÓN COMPLETADA EXITOSAMENTE")
                logger.info("✅ Usuario ${request.username} es ahora Premium")
                logger.info("✅ Order ID: $mockOrderId")

                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "message" to "¡Premium activado exitosamente!\n\nDetalles:\n• ID: $mockOrderId\n• Usuario: ${request.username}\n• Monto: €${request.amount}",
                    "orderId" to mockOrderId,
                    "simulation" to true,
                    "amount" to request.amount,
                    "currency" to "EUR",
                    "paymentMethod" to "paypal_simulation",
                    "timestamp" to System.currentTimeMillis()
                ))
            } else {
                logger.error("❌ Error actualizando usuario en BD")
                ResponseEntity.status(500).body(mapOf(
                    "success" to false,
                    "message" to "Pago procesado pero error actualizando usuario. Contacta soporte."
                ))
            }

        } catch (interruptedException: InterruptedException) {
            logger.error("❌ Simulación interrumpida", interruptedException)
            Thread.currentThread().interrupt()
            ResponseEntity.status(500).body(mapOf(
                "success" to false,
                "message" to "Simulación interrumpida"
            ))
        } catch (exception: Exception) {
            logger.error("❌ Error inesperado en simulación", exception)
            ResponseEntity.status(500).body(mapOf(
                "success" to false,
                "message" to "Error del servidor: ${exception.message}"
            ))
        }
    }

    @GetMapping("/test")
    fun testEndpoint(httpRequest: HttpServletRequest): ResponseEntity<Map<String, Any>> {
        logger.info("🔍 Test endpoint llamado desde: ${httpRequest.remoteAddr}")

        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "Servidor PayPal funcionando correctamente",
            "timestamp" to System.currentTimeMillis(),
            "version" to "1.0",
            "clientIp" to httpRequest.remoteAddr,
            "serverTime" to java.time.LocalDateTime.now().toString()
        ))
    }

    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "status" to "UP",
            "service" to "PayPal Simulation Service",
            "timestamp" to System.currentTimeMillis()
        ))
    }

    @GetMapping("/debug")
    fun debugInfo(httpRequest: HttpServletRequest): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "server" to "Running",
            "clientIp" to httpRequest.remoteAddr,
            "headers" to mapOf(
                "user-agent" to httpRequest.getHeader("User-Agent"),
                "accept" to httpRequest.getHeader("Accept"),
                "content-type" to httpRequest.getHeader("Content-Type")
            ),
            "endpoints" to listOf(
                "/api/payment/test",
                "/api/payment/health",
                "/api/payment/simulate-purchase",
                "/api/payment/debug"
            )
        ))
    }
}

data class SimulatePurchaseRequest(
    val username: String,
    val amount: String
)