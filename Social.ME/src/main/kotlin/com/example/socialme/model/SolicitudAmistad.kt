package com.example.socialme.model

import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

@Document("SolicitudAmistad")
class SolicitudAmistad(
    val _id: String,
    val remitente:String,
    val destinatario:String,
    val fechaEnviada: Date,
    val aceptada:Boolean
) {
}