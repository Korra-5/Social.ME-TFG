package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId
import org.springframework.data.mongodb.core.mapping.Document

@Document("ParticipantesActividad")
data class ParticipantesActividad (
    @BsonId
    val _id:String,
    val username:String,
    val actividad:String
) {

}