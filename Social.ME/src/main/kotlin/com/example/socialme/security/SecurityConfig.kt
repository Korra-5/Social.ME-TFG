package com.example.socialme.security

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Autowired
    private lateinit var rsaKeys: RSAKeysProperties

    @Bean
    fun securityFilterChain(http: HttpSecurity) : SecurityFilterChain {

        return http
            .csrf { csrf -> csrf.disable() } // Cross-Site Forgery
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("/Usuario/login").permitAll()
                auth.requestMatchers("/Usuario/register").permitAll()
                auth.requestMatchers("/Comunidad/crearComunidad").authenticated()
                auth.requestMatchers("/Comunidad/salirComunidad").authenticated()
                auth.requestMatchers("/Comunidad/unirseComunidad").authenticated()
                auth.requestMatchers("/Comunidad/modificarComunidad").authenticated()
                auth.requestMatchers("/Comunidad/eliminarComunidad/{url}").authenticated()
                auth.requestMatchers("/Comunidad/verComunidadPorUrl/{url}").authenticated()
                auth.requestMatchers("/Comunidad/verTodasComunidadesPublicas").authenticated()
                auth.requestMatchers("/Comunidad/verComunidadesPublicasEnZona/{distanciaKm}/{username}").authenticated()
                auth.requestMatchers("/Comunidad/verComunidadPorUsuario/{username}").authenticated()
                auth.requestMatchers("/Actividad/verActividadesPublicasEnZona/{distanciaKm}/{username}").authenticated()
                auth.requestMatchers("/Actividad/verTodasActividadesPublicas").authenticated()
                auth.requestMatchers("/Actividad/verActividadNoParticipaUsuario/{username}").authenticated()
                auth.requestMatchers("/Actividad/modificarActividad").authenticated()
                auth.requestMatchers("/Actividad/eliminarActividad/{id}").authenticated()
                auth.requestMatchers("/Actividad/salirActividad").authenticated()
                auth.requestMatchers("/Actividad/unirseActividad").authenticated()
                auth.requestMatchers("/Actividad/verActividadPorId/{id}").authenticated()
                auth.requestMatchers("/Actividad/crearActividad").authenticated()
                auth.requestMatchers("/Usuario/modificarUsuario").authenticated()
                auth.requestMatchers("/Usuario/eliminarUsuario/{username}").authenticated()
                auth.requestMatchers("/files/download/{id}").authenticated()
                auth.requestMatchers("/Actividad/verActividadPorUsername/{username}").authenticated()
                auth.requestMatchers("/Actividad/booleanUsuarioApuntadoActividad").authenticated()
                auth.requestMatchers("/Comunidad/booleanUsuarioApuntadoComunidad").authenticated()
                auth.requestMatchers("/Usuario/verUsuarioPorUsername/{username}").authenticated()
                auth.requestMatchers("/Actividad/verActividadesPorComunidad/{comunidad}").authenticated()
                auth.requestMatchers("/Actividad/contarUsuariosEnUnaActividad/{actividadId}").authenticated()
                auth.requestMatchers("/Comunidad/contarUsuariosEnUnaComunidad/{comunidad}").authenticated()
                auth.requestMatchers("/Usuario/verUsuariosPorComunidad/{comunidad}").authenticated()
                auth.requestMatchers("/Usuario/verUsuariosPorActividad/{actividadId}").authenticated()
                auth.requestMatchers("/Comunidad/verificarCreadorAdministradorComunidad/{username}/{comunidadUrl}").authenticated()
                auth.requestMatchers("/Actividad/verificarCreadorAdministradorActividad/{username}/{idActividad}").authenticated()
                auth.requestMatchers("/Usuario/verTodosLosUsuarios/{username}").authenticated()
                auth.requestMatchers("/Comunidad/unirseComunidadPorCodigo/{Codigo}").authenticated()
                auth.requestMatchers("/Usuario/reenviarCodigo/{email}").permitAll()
                auth.requestMatchers("/Usuario/verificarCodigo").permitAll()
                auth.requestMatchers("/Denuncia/verDenunciasPuestas/{username}").authenticated()
                auth.requestMatchers("/Denuncia/crearDenuncia").authenticated()
                auth.requestMatchers("/Comunidad/verComunidadesPorUsuarioCreador/{username}").authenticated()
                auth.requestMatchers("/Usuario/verificarPremium").authenticated()
                auth.requestMatchers("/Usuario/actualizarPremium/{username}").authenticated()
                auth.requestMatchers("/Usuario/verSolicitudesAmistad/{username}").authenticated()
                auth.requestMatchers("/Usuario/verAmigos/{username}").authenticated()
                auth.requestMatchers("/Usuario/enviarSolicitudAmistad").authenticated()
                auth.requestMatchers("/Usuario/aceptarSolicitud/{id}").authenticated()
                auth.requestMatchers("/Usuario/rechazarSolicitud/{id}").authenticated()
                auth.requestMatchers("/Usuario/verificarSolicitudPendiente/{remitente}/{destinatario}").authenticated()
                auth.requestMatchers("/Usuario/bloquearUsuario").authenticated()
                auth.requestMatchers("/Usuario/desbloquearUsuario").authenticated()
                auth.requestMatchers("/Usuario/verUsuariosBloqueados/{username}").authenticated()
                auth.requestMatchers("/Usuario/existeBloqueo/{usuario1}/{usuario2}").authenticated()
                auth.requestMatchers("/Denuncia/verTodasLasDenuncias").authenticated()
                auth.requestMatchers("/Denuncia/verDenunciasNoCompletadas").authenticated()
                auth.requestMatchers("/Denuncia/completarDenuncia/{denunciaId}/{completado}").authenticated()
                auth.requestMatchers("/Usuario/verTodasLasDenuncias").authenticated()
            } // Los recursos protegidos y publicos
            .oauth2ResourceServer { oauth2 -> oauth2.jwt(Customizer.withDefaults()) }
            .sessionManagement { session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .httpBasic(Customizer.withDefaults())
            .build()

    }

    @Bean
    fun passwordEncoder() : PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    /**
     * Método que inicializa un objeto de tipo AuthenticationManager
     */
    @Bean
    fun authenticationManager(authenticationConfiguration: AuthenticationConfiguration) : AuthenticationManager {
        return authenticationConfiguration.authenticationManager
    }


    /*
    MÉTODO PARA CODIFICAR UN JWT
     */
    @Bean
    fun jwtEncoder(): JwtEncoder {
        val jwk: JWK = RSAKey.Builder(rsaKeys.publicKey).privateKey(rsaKeys.privateKey).build()
        val jwks: JWKSource<SecurityContext> = ImmutableJWKSet(JWKSet(jwk))
        return NimbusJwtEncoder(jwks)
    }

    /*
    MÉTODO PARA DECODIFICAR UN JWT
     */
    @Bean
    fun jwtDecoder(): JwtDecoder {
        return NimbusJwtDecoder.withPublicKey(rsaKeys.publicKey).build()
    }
}