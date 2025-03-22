package com.example.socialme.model

import org.bson.codecs.pojo.annotations.BsonId

data class ParticipantesActividad (
    @BsonId
    val _id:String,
    val username:String,
    val actividad:String
) {

}