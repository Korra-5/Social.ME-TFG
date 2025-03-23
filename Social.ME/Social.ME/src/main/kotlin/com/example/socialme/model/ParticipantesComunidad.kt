package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId
import org.springframework.data.mongodb.core.mapping.Document

@Document("ParticipantesComunidad")
data class ParticipantesComunidad(
    @BsonId
    val _id:String,
    val username:String,
    var comunidad:String
) {
}