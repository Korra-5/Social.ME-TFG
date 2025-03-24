package com.example.socialme.dto

import java.util.*

class ActividadUpdateDTO(
    val _id: String,
    val nombre: String,
    val descripcion: String,
    val fotosCarrusel:List<String>,
    val fechaInicio: Date,
    val fechaFinalizacion: Date,
) {

}