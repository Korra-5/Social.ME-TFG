package com.example.socialme.dto

import java.util.Date

data class DenunciaDTO(
    val _id: String? = null,
    val motivo: String,
    val cuerpo: String,
    val nombreItemDenunciado: String,
    val tipoItemDenunciado: String,
    val usuarioDenunciante: String,
    val fechaCreacion: Date,
    val solucionado: Boolean = false
)