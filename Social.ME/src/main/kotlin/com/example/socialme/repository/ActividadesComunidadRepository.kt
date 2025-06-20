package com.example.socialme.repository

import com.example.socialme.model.Actividad
import com.example.socialme.model.ActividadesComunidad
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ActividadesComunidadRepository: MongoRepository<ActividadesComunidad,String> {
    fun findByComunidad(comunidad:String): Optional<List<ActividadesComunidad>>
    fun findActividadesComunidadByIdActividad(actividadId: String): Optional <ActividadesComunidad>
    fun deleteByIdActividad(idActividad: String)
    fun deleteByComunidad(comunidad:String)
}
