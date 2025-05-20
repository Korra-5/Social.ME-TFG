package com.example.socialme.service

import com.example.socialme.dto.LoginUsuarioDTO
import com.example.socialme.error.exception.UnauthorizedException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.stereotype.Service

@Service
class AuthenticationService {

    @Autowired
    @Lazy
    private lateinit var authenticationManager: AuthenticationManager

    @Autowired
    private lateinit var tokenService: TokenService

    fun authenticate(username: String, password: String): Authentication {
        try {
            return authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(username, password)
            )
        } catch (e: AuthenticationException) {
            throw UnauthorizedException("Credenciales incorrectas")
        }
    }

    fun login(usuario: LoginUsuarioDTO): String {
        val authentication = authenticate(usuario.username, usuario.password)
        return tokenService.generarToken(authentication)
    }
}