package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId
import org.springframework.data.mongodb.core.mapping.Document
import java.util.Date

@Document("ParticipantesComunidad")
data class ParticipantesComunidad(
    @BsonId
    val _id:String?,
    var username:String,
    var comunidad:String,
    var fechaUnion:Date
) {
}