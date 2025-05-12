package com.example.socialme.repository

import com.example.socialme.dto.ComunidadDTO
import com.example.socialme.model.Comunidad
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ComunidadRepository: MongoRepository<Comunidad, String> {
    fun findComunidadByUrl(url:String): Optional<Comunidad>
    fun findComunidadByCodigoUnion(codigo:String): Optional<Comunidad>
    fun countByCreador(creador: String): Int
    fun findByCreador(username: String): List<Comunidad>
}