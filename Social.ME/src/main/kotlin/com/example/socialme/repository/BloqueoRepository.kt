package com.example.socialme.repository

import com.example.socialme.model.Bloqueo
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface BloqueoRepository : MongoRepository<Bloqueo, String> {
    fun findByBloqueadorAndBloqueado(bloqueador: String, bloqueado: String): Optional<Bloqueo>
    fun findAllByBloqueador(bloqueador: String): List<Bloqueo>
    fun findAllByBloqueado(bloqueado: String): List<Bloqueo>
    fun existsByBloqueadorAndBloqueado(bloqueador: String, bloqueado: String): Boolean
}