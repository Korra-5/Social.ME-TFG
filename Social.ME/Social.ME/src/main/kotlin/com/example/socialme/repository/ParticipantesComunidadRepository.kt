package com.example.socialme.repository

import com.example.socialme.model.ParticipantesActividad
import com.example.socialme.model.ParticipantesComunidad
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ParticipantesComunidadRepository: MongoRepository<ParticipantesComunidad, String> {
    fun findByUsername(username: String): List<ParticipantesComunidad>
    fun findBy_id(_id: String): Optional<ParticipantesComunidad>
    fun findParticipantesByComunidad(comunidad: String): List<ParticipantesComunidad>
}