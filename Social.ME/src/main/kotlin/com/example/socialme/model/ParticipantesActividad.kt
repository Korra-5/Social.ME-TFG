package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

@Document("ParticipantesActividad")
data class ParticipantesActividad (
    @BsonId
    val _id:String?,
    var username:String,
    val idActividad:String,
    var fechaUnion: Date,
    var nombreActividad:String
) {

}