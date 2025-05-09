package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId
import org.springframework.data.mongodb.core.mapping.Document
import java.util.Date

@Document("Denuncias")
data class Denuncia(
    @BsonId
    val _id: String?,
    val motivo: String,
    val cuerpo: String,
    val nombreItemDenunciado:String,
    val tipoItemDenunciado:String,
    val usuarioDenunciante:String,
    val fechaCreacion: Date
) {

}