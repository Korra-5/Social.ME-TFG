package com.example.socialme.dto

import java.util.*

data class ActividadCreateDTO (
    val nombre: String,
    val descripcion: String,
    val comunidad:String,
    val creador:String,
    val fechaInicio: Date,
    val fechaFinalizacion: Date,
    val fotosCarrusel: List<String>,
    val privada:Boolean
){
}