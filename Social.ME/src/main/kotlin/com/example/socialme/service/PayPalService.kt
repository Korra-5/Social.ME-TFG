package com.example.socialme.service

import com.paypal.api.payments.*
import com.paypal.base.rest.APIContext
import com.paypal.base.rest.PayPalRESTException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*

@Service
class PayPalService {

    @Value("\${paypal.client-id}")
    private lateinit var clientId: String

    @Value("\${paypal.client-secret}")
    private lateinit var clientSecret: String

    @Value("\${paypal.environment}")
    private lateinit var mode: String

    private fun getAPIContext(): APIContext {
        return APIContext(clientId, clientSecret, mode)
    }

    // Método para simular un pago exitoso (para desarrollo/testing)
    fun simulateSuccessfulPayment(
        total: Double,
        currency: String,
        description: String
    ): Map<String, Any> {
        // Simulamos una respuesta exitosa de PayPal
        val simulatedPaymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 17)
        val simulatedPayerId = "PAYER-" + UUID.randomUUID().toString().substring(0, 13)

        return mapOf(
            "success" to true,
            "paymentId" to simulatedPaymentId,
            "payerId" to simulatedPayerId,
            "amount" to total,
            "currency" to currency,
            "description" to description,
            "status" to "approved",
            "createTime" to Date().toString(),
            "message" to "Pago simulado exitosamente - Modo sandbox"
        )
    }

    @Throws(PayPalRESTException::class)
    fun createPayment(
        total: Double,
        currency: String,
        method: String,
        intent: String,
        description: String,
        cancelUrl: String,
        successUrl: String
    ): Payment {
        val amount = Amount()
        amount.currency = currency
        amount.total = String.format(Locale.forLanguageTag("en"), "%.2f", total)

        val transaction = Transaction()
        transaction.description = description
        transaction.amount = amount

        val transactions = listOf(transaction)

        val payer = Payer()
        payer.paymentMethod = method

        val payment = Payment()
        payment.intent = intent
        payment.payer = payer
        payment.transactions = transactions

        val redirectUrls = RedirectUrls()
        redirectUrls.cancelUrl = cancelUrl
        redirectUrls.returnUrl = successUrl
        payment.redirectUrls = redirectUrls

        return payment.create(getAPIContext())
    }

    @Throws(PayPalRESTException::class)
    fun executePayment(paymentId: String, payerId: String): Payment {
        val payment = Payment()
        payment.id = paymentId

        val paymentExecution = PaymentExecution()
        paymentExecution.payerId = payerId

        return payment.execute(getAPIContext(), paymentExecution)
    }
}