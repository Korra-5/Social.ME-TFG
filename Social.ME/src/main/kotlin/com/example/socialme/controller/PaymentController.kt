package com.example.socialme.controller

import com.example.socialme.service.UsuarioService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = ["*"])
class PaymentController(
    @Autowired private val usuarioService: UsuarioService
) {
    private val logger = LoggerFactory.getLogger(PaymentController::class.java)

    @PostMapping("/simulate-purchase")
    fun simulatePurchase(
        @RequestHeader("Authorization") token: String,
        @RequestBody request: SimulatePurchaseRequest
    ): ResponseEntity<Map<String, Any>> {

        logger.info("=== SIMULANDO COMPRA PREMIUM ===")
        logger.info("Username: ${request.username}")
        logger.info("Amount: €${request.amount}")

        return try {
            // Simular un delay como si fuera PayPal
            Thread.sleep(2000)

            // Generar un ID de orden simulado
            val mockOrderId = "MOCK_ORDER_${System.currentTimeMillis()}"
            logger.info("✅ Orden simulada creada: $mockOrderId")

            // Actualizar usuario a premium
            val updated = usuarioService.updatePremiumStatus(request.username, true)

            if (updated) {
                logger.info("✅ Usuario actualizado a premium: ${request.username}")
                ResponseEntity.ok(mapOf(
                    "success" to true,
                    "message" to "¡Premium activado exitosamente! (Simulación)",
                    "orderId" to mockOrderId,
                    "simulation" to true,
                    "amount" to request.amount
                ))
            } else {
                logger.error("❌ Error actualizando usuario a premium")
                ResponseEntity.status(500).body(mapOf(
                    "success" to false,
                    "message" to "Error actualizando usuario en la base de datos"
                ))
            }

        } catch (e: Exception) {
            logger.error("❌ Error en simulación", e)
            ResponseEntity.status(500).body(mapOf(
                "success" to false,
                "message" to "Error del servidor: ${e.message}"
            ))
        }
    }

    @GetMapping("/test")
    fun testEndpoint(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "Endpoint funcionando correctamente",
            "timestamp" to System.currentTimeMillis()
        ))
    }
}

data class SimulatePurchaseRequest(
    val username: String,
    val amount: String
)