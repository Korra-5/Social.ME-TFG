package com.example.socialme.dto

import java.util.Date

data class DenunciaDTO(
    val motivo: String,
    val cuerpo: String,
    val nombreItemDenunciado: String,
    val tipoItemDenunciado: String,
    val fechaCreacion: Date,
    val solucionado: Boolean = false
)