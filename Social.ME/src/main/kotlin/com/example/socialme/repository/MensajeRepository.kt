package com.example.socialme.repository

import com.example.socialme.model.Mensaje
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.List

@Repository
interface MensajeRepository : MongoRepository<Mensaje, String> {
    fun findByComunidadUrlOrderByFechaEnvioAsc(comunidadUrl: String): List<Mensaje>
    fun countByComunidadUrl(comunidadUrl: String): Int
    fun deleteByComunidadUrl(comunidadUrl: String)
}