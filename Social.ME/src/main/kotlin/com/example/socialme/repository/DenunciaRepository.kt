package com.example.socialme.repository

import com.example.socialme.model.Denuncia
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface DenunciaRepository: MongoRepository<Denuncia, String> {
    fun findByUsuarioDenunciante(usuarioDenunciante: String): Optional<Denuncia>
}