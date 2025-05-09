package com.example.socialme.dto

data class DenunciaCreateDTO(
     val motivo: String,
     val cuerpo: String,
     val nombreItemDenunciado:String,
     val tipoItemDenunciado:String,
    val usuarioDenunciante:String,
)
{
}