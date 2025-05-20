// SolicitudesAmistadRepository.kt
package com.example.socialme.repository

import com.example.socialme.model.SolicitudAmistad
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface SolicitudesAmistadRepository: MongoRepository<SolicitudAmistad, String> {
    fun findByDestinatarioAndAceptada(destinatario: String, aceptada: Boolean): List<SolicitudAmistad>
    fun findByRemitenteAndDestinatarioAndAceptada(remitente: String, destinatario: String, aceptada: Boolean): SolicitudAmistad?
    fun findByRemitenteAndAceptada(remitente: String, aceptada: Boolean): List<SolicitudAmistad>
    fun findByRemitenteAndDestinatario(remitente: String, destinatario: String): List<SolicitudAmistad>
}