package com.example.socialme.repository

import com.example.socialme.model.ParticipantesActividad
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ParticipantesActividadRepository : MongoRepository<ParticipantesActividad, String> {
    fun findBy_id(actividadId: String): Optional<ParticipantesActividad>
    fun findByUsername(username: String): List<ParticipantesActividad>
    fun findByUsernameAndActividadId(username: String, actividadId: String): Optional<ParticipantesActividad>
}