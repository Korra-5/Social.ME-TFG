package com.example.socialme.repository

import com.example.socialme.model.Actividad
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ActividadRepository: MongoRepository<Actividad, String> {
    fun findActividadBy_id(_id: String?): Optional<Actividad>
}