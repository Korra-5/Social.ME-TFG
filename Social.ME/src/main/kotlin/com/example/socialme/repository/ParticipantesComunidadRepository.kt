package com.example.socialme.repository

import com.example.socialme.model.ParticipantesActividad
import com.example.socialme.model.ParticipantesComunidad
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ParticipantesComunidadRepository: MongoRepository<ParticipantesComunidad, String> {
    fun findByUsername(username: String): List<ParticipantesComunidad>
    fun findParticipantesByComunidad(comunidad: String): List<ParticipantesComunidad>
    fun findByComunidad(comunidad: String): List<ParticipantesComunidad>
    fun findByUsernameAndComunidad(username: String, comunidad: String): Optional<ParticipantesComunidad>
    fun findComunidadByUsername(username: String): Optional<List <ParticipantesComunidad>>
    fun deleteByComunidad(comunidad: String)
}