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
            // USAR PAYPAL REAL
            val payment = payPalService.createPayment(
                total = paymentRequest.amount,
                currency = paymentRequest.currency,
                method = "paypal",
                intent = "sale",
                description = paymentRequest.description,
                cancelUrl = "https://social-me-tfg.onrender.com/api/paypal/cancel",
                successUrl = "https://social-me-tfg.onrender.com/api/paypal/success"
            )

            // Buscar la URL de aprobación
            val approvalUrl = payment.links?.find {
                it.rel.equals("approval_url", ignoreCase = true)
            }?.href

            val response = PaymentResponseDTO(
                success = true,
                paymentId = payment.id,
                approvalUrl = approvalUrl,
                message = "Pago creado exitosamente. Redirige al usuario a PayPal.",
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

    @PostMapping("/execute-payment")
    fun executePayment(
        httpRequest: HttpServletRequest,
        @Valid @RequestBody verificationRequest: PaymentVerificationDTO
    ): ResponseEntity<PaymentResponseDTO> {
        return try {
            // EJECUTAR PAGO REAL EN PAYPAL
            val executedPayment = payPalService.executePayment(
                paymentId = verificationRequest.paymentId,
                payerId = verificationRequest.payerId
            )

            if (executedPayment.state == "approved") {
                // Actualizar usuario a premium
                usuarioService.actualizarPremium(verificationRequest.username)

                val response = PaymentResponseDTO(
                    success = true,
                    paymentId = executedPayment.id,
                    message = "¡Pago completado exitosamente! Ya eres Premium."
                )
                ResponseEntity.ok(response)
            } else {
                val response = PaymentResponseDTO(
                    success = false,
                    message = "El pago no fue aprobado por PayPal"
                )
                ResponseEntity.badRequest().body(response)
            }
        } catch (e: Exception) {
            val errorResponse = PaymentResponseDTO(
                success = false,
                message = "Error al ejecutar el pago: ${e.message}"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
        }
    }

    // Mantener también la simulación para testing
    @PostMapping("/simulate-premium-purchase")
    fun simulatePremiumPurchase(
        httpRequest: HttpServletRequest,
        @RequestParam username: String
    ): ResponseEntity<PaymentResponseDTO> {
        return try {
            usuarioService.actualizarPremium(username)

            val response = PaymentResponseDTO(
                success = true,
                paymentId = "SIMULATED-PAY-" + System.currentTimeMillis(),
                message = "Premium activado exitosamente (simulación)",
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

    // Endpoints para manejar retorno de PayPal
    @GetMapping("/success")
    fun paymentSuccess(
        @RequestParam("paymentId") paymentId: String,
        @RequestParam("PayerID") payerId: String
    ): ResponseEntity<String> {
        return ResponseEntity.ok("""
            <html>
                <body>
                    <h2>Pago exitoso</h2>
                    <p>Payment ID: $paymentId</p>
                    <p>Payer ID: $payerId</p>
                    <p>Puedes cerrar esta ventana.</p>
                </body>
            </html>
        """.trimIndent())
    }

    @GetMapping("/cancel")
    fun paymentCancel(): ResponseEntity<String> {
        return ResponseEntity.ok("""
            <html>
                <body>
                    <h2>Pago cancelado</h2>
                    <p>El pago fue cancelado por el usuario.</p>
                </body>
            </html>
        """.trimIndent())
    }
}