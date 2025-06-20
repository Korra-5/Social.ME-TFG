package com.example.socialme.repository

import com.example.socialme.model.Usuario
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UsuarioRepository : MongoRepository<Usuario, String> {

    fun findFirstByUsername(username: String) : Optional<Usuario>
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean

}