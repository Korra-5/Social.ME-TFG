package com.example.socialme.dto

import java.util.*

data class ActividadDTO (
    val nombre: String,
    val descripcion: String,
    val privada:Boolean,
    val creador:String,
    val fechaInicio: Date,
    val fechaFinalizacion: Date,
    val fotosCarrusel: List<String>,
    val lugar:String,
){
}