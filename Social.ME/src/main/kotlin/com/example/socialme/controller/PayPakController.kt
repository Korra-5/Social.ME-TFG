package com.example.socialme.controller

import com.example.socialme.dto.*
import com.example.socialme.model.PaymentRequestDTO
import com.example.socialme.model.PaymentResponseDTO
import com.example.socialme.model.PaymentVerificationDTO
import com.example.socialme.service.PayPalService
import com.example.socialme.service.UsuarioService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/paypal")
class PayPalController {

    @Autowired
    private lateinit var payPalService: PayPalService

    @Autowired
    private lateinit var usuarioService: UsuarioService

    @PostMapping("/create-payment")
    fun createPayment(
        httpRequest: HttpServletRequest,
        @Valid @RequestBody paymentRequest: PaymentRequestDTO
    ): ResponseEntity<PaymentResponseDTO> {
        return try {
            // Para desarrollo, usamos simulación
            val result = payPalService.simulateSuccessfulPayment(
                total = paymentRequest.amount,
                currency = paymentRequest.currency,
                description = paymentRequest.description
            )

            val response = PaymentResponseDTO(
                success = true,
                paymentId = result["paymentId"] as String,
                approvalUrl = "https://sandbox.paypal.com/cgi-bin/webscr?cmd=_express-checkout&token=SIMULATED",
                message = "Pago creado exitosamente (simulado)",
                amount = paymentRequest.amount,
                currency = paymentRequest.currency
            )

            ResponseEntity.ok(response)
        } catch (e: Exception) {
            val errorResponse = PaymentResponseDTO(
                success = false,
                message = "Error al crear el pago: ${e.message}"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
        }
    }

    @PostMapping("/verify-payment")
    fun verifyPayment(
        httpRequest: HttpServletRequest,
        @Valid @RequestBody verificationRequest: PaymentVerificationDTO
    ): ResponseEntity<PaymentResponseDTO> {
        return try {
            // Simulamos la verificación del pago
            if (verificationRequest.paymentId.startsWith("PAY-") &&
                verificationRequest.payerId.startsWith("PAYER-")) {

                // Actualizamos el usuario a premium
                usuarioService.actualizarPremium(verificationRequest.username)

                val response = PaymentResponseDTO(
                    success = true,
                    paymentId = verificationRequest.paymentId,
                    message = "Pago verificado exitosamente. ¡Ya eres Premium!"
                )
                ResponseEntity.ok(response)
            } else {
                val response = PaymentResponseDTO(
                    success = false,
                    message = "Payment ID o Payer ID inválido"
                )
                ResponseEntity.badRequest().body(response)
            }
        } catch (e: Exception) {
            val errorResponse = PaymentResponseDTO(
                success = false,
                message = "Error al verificar el pago: ${e.message}"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
        }
    }

    @PostMapping("/simulate-premium-purchase")
    fun simulatePremiumPurchase(
        httpRequest: HttpServletRequest,
        @RequestParam username: String
    ): ResponseEntity<PaymentResponseDTO> {
        return try {
            // Directamente actualizamos el usuario a premium (para testing rápido)
            usuarioService.actualizarPremium(username)

            val response = PaymentResponseDTO(
                success = true,
                paymentId = "SIMULATED-PAY-" + System.currentTimeMillis(),
                message = "Premium activado exitosamente (simulación directa)",
                amount = 1.99,
                currency = "EUR"
            )

            ResponseEntity.ok(response)
        } catch (e: Exception) {
            val errorResponse = PaymentResponseDTO(
                success = false,
                message = "Error al activar premium: ${e.message}"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
        }
    }

    @GetMapping("/health-check")
    fun healthCheck(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf(
            "status" to "OK",
            "service" to "PayPal Service",
            "mode" to "sandbox",
            "timestamp" to java.util.Date().toString()
        ))
    }
}