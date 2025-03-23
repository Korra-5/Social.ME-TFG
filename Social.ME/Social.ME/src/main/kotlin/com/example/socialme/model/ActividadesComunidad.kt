package com.example.socialme.model

import org.springframework.data.mongodb.core.mapping.Document

@Document("ActividadesComunidad")
data class ActividadesComunidad(
    val comunidad:String,
    val idActividad:String
)