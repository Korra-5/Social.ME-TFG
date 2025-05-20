package com.example.socialme.service

import com.example.socialme.dto.*
import com.example.socialme.error.exception.BadRequestException
import com.example.socialme.error.exception.ForbiddenException
import com.example.socialme.error.exception.NotFoundException
import com.example.socialme.model.Comunidad
import com.example.socialme.model.Coordenadas
import com.example.socialme.model.ParticipantesComunidad
import com.example.socialme.repository.ActividadesComunidadRepository
import com.example.socialme.repository.ComunidadRepository
import com.example.socialme.repository.ParticipantesComunidadRepository
import com.example.socialme.repository.UsuarioRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class ComunidadService {

    @Autowired
    private lateinit var actividadService: ActividadService

    @Autowired
    private lateinit var actividadesComunidadRepository: ActividadesComunidadRepository

    @Autowired
    private lateinit var participantesComunidadRepository: ParticipantesComunidadRepository

    @Autowired
    private lateinit var comunidadRepository: ComunidadRepository

    @Autowired
    private lateinit var usuarioRepository: UsuarioRepository

    @Autowired
    private lateinit var gridFSService: GridFSService

    // Función auxiliar para convertir de forma segura Comunidad a ComunidadDTO
    private fun crearComunidadDTO(comunidad: Comunidad): ComunidadDTO {
        return ComunidadDTO(
            url = comunidad.url,
            nombre = comunidad.nombre,
            descripcion = comunidad.descripcion,
            intereses = comunidad.intereses ?: emptyList(),
            fotoPerfilId = comunidad.fotoPerfilId ?: "",
            fotoCarruselIds = comunidad.fotoCarruselIds ?: emptyList(),
            creador = comunidad.creador,
            administradores = comunidad.administradores ?: emptyList(),
            fechaCreacion = comunidad.fechaCreacion,
            comunidadGlobal = comunidad.comunidadGlobal,
            privada = comunidad.privada,
            coordenadas = comunidad.coordenadas,
            codigoUnion = comunidad.codigoUnion
        )
    }

    fun crearComunidad(comunidadCreateDTO: ComunidadCreateDTO): ComunidadDTO {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        // Los admins no pueden crear comunidades
        if (userActual.roles == "ADMIN") {
            throw ForbiddenException("Los administradores no pueden crear comunidades")
        }

        if (auth.name != comunidadCreateDTO.creador) {
            throw ForbiddenException("No tienes permisos para crear esta comunidad")
        }

        if (comunidadRepository.findComunidadByUrl(comunidadCreateDTO.url).isPresent) {
            throw BadRequestException("Comunidad existente")
        }

        // Sustituye por guiones los espacios para que las url sean más accesibles
        val formattedUrl = comunidadCreateDTO.url.trim().split(Regex("\\s+")).joinToString("-")

        if (comunidadCreateDTO.nombre.length > 40) {
            throw BadRequestException("Este nombre es demasiado largo, pruebe con uno inferior a 40 caracteres")
        }
        if (comunidadCreateDTO.descripcion.length > 5000) {
            throw BadRequestException("Lo sentimos, la descripción no puede superar los 5000 caracteres")
        }

        // Verifica que los intereses no tengan espacios ni superen los 25 caracteres
        validateAndReplaceSpaces(listOf(formattedUrl))

        if (!usuarioRepository.existsByUsername(comunidadCreateDTO.creador)) {
            throw NotFoundException("Usuario no encontrado")
        }

        val comunidadesCreadas = comunidadRepository.countByCreador(comunidadCreateDTO.creador)
        if (comunidadesCreadas >= 3) {
            throw ForbiddenException("Has alcanzado el límite máximo de 3 comunidades creadas")
        }

        // Handle profile photo upload to GridFS
        val fotoPerfilId = if (comunidadCreateDTO.fotoPerfilBase64 != null) {
            gridFSService.storeFileFromBase64(
                comunidadCreateDTO.fotoPerfilBase64,
                "community_profile_${formattedUrl}_${Date().time}",
                "image/jpeg",
                mapOf("type" to "profilePhoto", "community" to formattedUrl)
            )
        } else comunidadCreateDTO.fotoPerfilId ?: throw BadRequestException("Se requiere una foto de perfil")

        val comunidad: Comunidad =
            Comunidad(
                _id = null,
                nombre = comunidadCreateDTO.nombre,
                descripcion = comunidadCreateDTO.descripcion,
                creador = comunidadCreateDTO.creador,
                intereses = comunidadCreateDTO.intereses,
                fotoPerfilId = fotoPerfilId,
                fotoCarruselIds = emptyList(),  // Inicializado como lista vacía en lugar de null
                administradores = emptyList(),  // Inicializado como lista vacía en lugar de null
                fechaCreacion = Date.from(Instant.now()),
                url = formattedUrl,
                comunidadGlobal = comunidadCreateDTO.comunidadGlobal,
                privada = comunidadCreateDTO.privada,
                coordenadas = comunidadCreateDTO.coordenadas,
                codigoUnion = if (comunidadCreateDTO.privada) {
                    generarCodigoUnico()
                } else {
                    null
                }
            )

        val participantesComunidad = ParticipantesComunidad(
            comunidad = comunidad.url,
            username = comunidad.creador,
            fechaUnion = Date.from(Instant.now()),
            _id = null
        )

        comunidadRepository.insert(comunidad)
        participantesComunidadRepository.insert(participantesComunidad)

        return crearComunidadDTO(comunidad)
    }

    fun verComunidadPorUrl(url: String): ComunidadDTO {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        val comunidad = comunidadRepository.findComunidadByUrl(url).orElseThrow {
            throw NotFoundException("Comunidad not found: $url")
        }

        // Los admins pueden ver cualquier comunidad, incluso las privadas
        // Usuarios normales solo pueden ver comunidades privadas si son miembros
        if (comunidad.privada && userActual.roles != "ADMIN") {
            val esMiembro = participantesComunidadRepository.findByUsernameAndComunidad(
                username = auth.name,
                comunidad = comunidad.url
            ).isPresent

            if (!esMiembro) {
                throw ForbiddenException("No tienes permisos para ver esta comunidad privada")
            }
        }

        return crearComunidadDTO(comunidad)
    }

    fun verComunidadesPublicasEnZona(distancia: Float? = null, username: String): List<ComunidadDTO> {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        // Los admins pueden ver todas las comunidades, incluso las privadas
        if (userActual.roles == "ADMIN") {
            return comunidadRepository.findAll().map { comunidad ->
                crearComunidadDTO(comunidad)
            }
        }

        if (auth.name != username) {
            throw ForbiddenException("No tienes permisos para ver comunidades en la zona de este usuario")
        }

        // Obtenemos todas las comunidades
        val todasLasComunidades = comunidadRepository.findAll()

        // Obtenemos el usuario para acceder a sus coordenadas e intereses
        val usuario = usuarioRepository.findFirstByUsername(username)
            .orElseThrow { throw NotFoundException("Usuario no existe") }

        val coordenadasUser = usuario.coordenadas
        val interesesUser = usuario.intereses ?: emptyList()  // Manejo seguro de nulos

        // Obtenemos las comunidades a las que el usuario ya está unido
        val comunidadesDelUsuario = participantesComunidadRepository.findByUsername(username)
            .map { it.comunidad }
            .toSet()

        return todasLasComunidades
            .filter { !it.privada } // Solo comunidades públicas
            .filter { comunidad ->
                // Filtrar aquellas a las que el usuario no esté unido ya
                !comunidadesDelUsuario.contains(comunidad.url)
            }
            .filter { comunidad ->
                // Verificar la distancia
                actividadService.verificarDistancia(comunidad.coordenadas, coordenadasUser, distancia)
            }
            // Ya no filtramos por intereses para mostrar todas
            .map { comunidad ->
                crearComunidadDTO(comunidad)
            }
            .sortedWith(compareByDescending<ComunidadDTO> { comunidadDTO ->
                // Primera ordenación: por número de intereses coincidentes
                val interesesComunidad = comunidadDTO.intereses ?: emptyList()  // Manejo seguro de nulos
                interesesComunidad.count { interes -> interesesUser.contains(interes) }
            }.thenByDescending {
                // Segunda ordenación: por fecha de creación (las más recientes primero)
                it.fechaCreacion
            })
    }

    fun unirseComunidad(participantesComunidadDTO: ParticipantesComunidadDTO): ParticipantesComunidadDTO {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        // Los admins no pueden unirse a comunidades
        if (userActual.roles == "ADMIN") {
            throw ForbiddenException("Los administradores no pueden unirse a comunidades")
        }

        if (auth.name != participantesComunidadDTO.username) {
            throw ForbiddenException("No tienes permisos para unir a este usuario a la comunidad")
        }

        comunidadRepository.findComunidadByUrl(participantesComunidadDTO.comunidad)
            .orElseThrow { BadRequestException("La comunidad no existe") }

        usuarioRepository.findFirstByUsername(participantesComunidadDTO.username)
            .orElseThrow { NotFoundException("Usuario no encontrado") }

        if (participantesComunidadRepository.findByUsernameAndComunidad(
                participantesComunidadDTO.username,
                participantesComunidadDTO.comunidad
            ).isPresent
        ) {
            throw BadRequestException("El usuario ya está unido a esta comunidad")
        }

        val union = ParticipantesComunidad(
            _id = null,
            comunidad = participantesComunidadDTO.comunidad,
            username = participantesComunidadDTO.username,
            fechaUnion = Date.from(Instant.now())
        )

        participantesComunidadRepository.insert(union)

        return participantesComunidadDTO
    }

    fun eliminarComunidad(url: String): ComunidadDTO {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        val comunidad = comunidadRepository.findComunidadByUrl(url).orElseThrow {
            BadRequestException("Esta comunidad no existe")
        }

        // Los admins pueden eliminar cualquier comunidad
        if (userActual.roles != "ADMIN" && auth.name != comunidad.creador) {
            throw ForbiddenException("No tienes permisos para eliminar esta comunidad")
        }

        val comunidadDto = crearComunidadDTO(comunidad)

        // Delete images from GridFS
        try {
            if (!comunidad.fotoPerfilId.isNullOrBlank()) {
                gridFSService.deleteFile(comunidad.fotoPerfilId)
            }
            comunidad.fotoCarruselIds?.forEach { fileId ->
                if (!fileId.isNullOrBlank()) {
                    gridFSService.deleteFile(fileId)
                }
            }
        } catch (e: Exception) {
            // Log error but continue with deletion
            println("Error deleting GridFS files: ${e.message}")
        }

        // Eliminar primero todos los participantes de la comunidad
        participantesComunidadRepository.deleteByComunidad(comunidad.url)

        // Luego eliminar las actividades asociadas a la comunidad
        actividadesComunidadRepository.deleteByComunidad(comunidad.url)

        // Finalmente eliminar la comunidad
        comunidadRepository.delete(comunidad)

        return comunidadDto
    }

    fun getComunidadPorUsername(username: String): List<Comunidad> {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        // Los admins pueden ver las comunidades de cualquier usuario
        // y también tienen acceso a todas las comunidades
        if (userActual.roles == "ADMIN") {
            if (username == "ADMIN") {
                // Si se solicita específicamente las comunidades de ADMIN, devolver todas
                return comunidadRepository.findAll()
            } else {
                // De lo contrario, buscar las comunidades del usuario específico
                val participaciones = participantesComunidadRepository.findByUsername(username)
                if (participaciones.isEmpty()) {
                    throw BadRequestException("No existe el usuario o no pertenece a ninguna comunidad")
                }
                return participaciones.mapNotNull { participante ->
                    val url = participante.comunidad
                    comunidadRepository.findComunidadByUrl(url)
                        .orElseThrow { BadRequestException("La comunidad con URL '$url' no existe") }
                }
            }
        }

        if (auth.name != username) {
            throw ForbiddenException("No tienes permisos para ver las comunidades de este usuario")
        }

        val participaciones = participantesComunidadRepository.findByUsername(username)

        if (participaciones.isEmpty()) {
            throw BadRequestException("No existe el usuario o no pertenece a ninguna comunidad")
        }

        return participaciones.mapNotNull { participante ->
            val url = participante.comunidad
            comunidadRepository.findComunidadByUrl(url)
                .orElseThrow { BadRequestException("La comunidad con URL '$url' no existe") }
        }
    }

    fun modificarComunidad(comunidadUpdateDTO: ComunidadUpdateDTO): ComunidadDTO {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        // Buscar la comunidad existente usando currentURL
        val comunidadExistente = comunidadRepository.findComunidadByUrl(comunidadUpdateDTO.currentURL).orElseThrow {
            throw NotFoundException("Comunidad con URL ${comunidadUpdateDTO.currentURL} no encontrada")
        }

        // Los admins pueden modificar cualquier comunidad
        if (userActual.roles != "ADMIN") {
            // Verificar si el usuario autenticado es creador o administrador de la comunidad
            val administradores = comunidadExistente.administradores ?: emptyList()
            if (auth.name != comunidadExistente.creador && !administradores.contains(auth.name)) {
                throw ForbiddenException("No tienes permisos para modificar esta comunidad")
            }
        }

        // Si se está cambiando la URL, validar que la nueva no exista ya
        if (comunidadUpdateDTO.newUrl != comunidadUpdateDTO.currentURL) {
            comunidadRepository.findComunidadByUrl(comunidadUpdateDTO.newUrl).ifPresent {
                throw BadRequestException("Ya existe una comunidad con la URL ${comunidadUpdateDTO.newUrl}, prueba con otra URL")
            }
        }

        // Verificar que los administradores existan
        var administradores = comunidadUpdateDTO.administradores ?: emptyList()
        administradores.forEach { admin ->
            if (!usuarioRepository.existsByUsername(admin)) {
                throw NotFoundException("Administrador con username '$admin' no encontrado")
            }
        }

        // Guardar la antigua URL para actualizar referencias
        val urlAntigua = comunidadExistente.url

        // Procesar la foto de perfil si se proporciona en Base64
        val nuevaFotoPerfilId = if (!comunidadUpdateDTO.fotoPerfilBase64.isNullOrBlank()) {
            // Intentar eliminar la foto de perfil antigua, si existe
            try {
                if (!comunidadExistente.fotoPerfilId.isNullOrBlank()) {
                    gridFSService.deleteFile(comunidadExistente.fotoPerfilId)
                }
            } catch (e: Exception) {
                println("Error al eliminar la foto de perfil antigua: ${e.message}")
            }

            // Guardar la nueva foto de perfil
            val urlParaFoto = comunidadUpdateDTO.newUrl
            gridFSService.storeFileFromBase64(
                comunidadUpdateDTO.fotoPerfilBase64,
                "community_profile_${urlParaFoto}_${Date().time}",
                "image/jpeg",
                mapOf(
                    "type" to "profilePhoto",
                    "community" to urlParaFoto
                )
            ) ?: ""
        } else if (comunidadUpdateDTO.fotoPerfilId != null) {
            // Si se proporciona explícitamente un ID de foto
            comunidadUpdateDTO.fotoPerfilId
        } else {
            // Mantener la foto de perfil existente
            comunidadExistente.fotoPerfilId
        }

        // Procesar las fotos del carrusel si se proporcionan en Base64
        val nuevasFotosCarruselIds = if (comunidadUpdateDTO.fotoCarruselBase64 != null && comunidadUpdateDTO.fotoCarruselBase64.isNotEmpty()) {
            // Intentar eliminar las fotos de carrusel antiguas, si existen
            try {
                comunidadExistente.fotoCarruselIds?.forEach { fotoId ->
                    if (!fotoId.isNullOrBlank()) {
                        gridFSService.deleteFile(fotoId)
                    }
                }
            } catch (e: Exception) {
                println("Error al eliminar fotos de carrusel antiguas: ${e.message}")
            }

            // Guardar las nuevas fotos de carrusel
            val urlParaFotos = comunidadUpdateDTO.newUrl
            comunidadUpdateDTO.fotoCarruselBase64.mapIndexed { index, base64 ->
                gridFSService.storeFileFromBase64(
                    base64,
                    "community_carousel_${urlParaFotos}_${index}_${Date().time}",
                    "image/jpeg",
                    mapOf(
                        "type" to "carouselPhoto",
                        "community" to urlParaFotos,
                        "position" to index.toString()
                    )
                ) ?: ""
            }
        } else {
            // Mantener las fotos de carrusel existentes
            comunidadUpdateDTO.fotoCarruselIds ?: comunidadExistente.fotoCarruselIds ?: emptyList()
        }

        // Actualizar la información de la comunidad
        comunidadExistente.apply {
            url = comunidadUpdateDTO.newUrl
            nombre = comunidadUpdateDTO.nombre
            descripcion = comunidadUpdateDTO.descripcion
            intereses = comunidadUpdateDTO.intereses
            administradores = administradores
            fotoPerfilId = nuevaFotoPerfilId
            fotoCarruselIds = nuevasFotosCarruselIds
        }

        val comunidadActualizada = comunidadRepository.save(comunidadExistente)

        // Si se ha cambiado la URL, actualizar referencias en otras colecciones
        if (urlAntigua != comunidadActualizada.url) {
            try {
                // Actualizar referencias en ActividadesComunidad
                val actividades = actividadesComunidadRepository.findByComunidad(urlAntigua).orElse(emptyList())
                actividades.forEach { actividad ->
                    actividad.comunidad = comunidadActualizada.url
                    actividadesComunidadRepository.save(actividad)
                }
            } catch (e: Exception) {
                println("Error al actualizar referencias de actividades: ${e.message}")
            }

            try {
                // Actualizar referencias en ParticipantesComunidad
                val participantes = participantesComunidadRepository.findByComunidad(urlAntigua)
                participantes.forEach { participante ->
                    participante.comunidad = comunidadActualizada.url
                    participantesComunidadRepository.save(participante)
                }
            } catch (e: Exception) {
                println("Error al actualizar referencias de participantes: ${e.message}")
            }
        }

        // Retornar el DTO actualizado
        return crearComunidadDTO(comunidadActualizada)
    }

    fun salirComunidad(participantesComunidadDTO: ParticipantesComunidadDTO): ParticipantesComunidadDTO {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        // Los admins no deberían estar en comunidades, pero si están pueden salir
        if (userActual.roles != "ADMIN" && auth.name != participantesComunidadDTO.username) {
            throw ForbiddenException("No tienes permisos para sacar a este usuario de la comunidad")
        }

        val union = participantesComunidadRepository.findByUsernameAndComunidad(
            username = participantesComunidadDTO.username,
            comunidad = participantesComunidadDTO.comunidad
        ).orElseThrow {
            throw BadRequestException("No estás en esta comunidad")
        }

        val comunidad = comunidadRepository.findComunidadByUrl(participantesComunidadDTO.comunidad).orElseThrow {
            throw NotFoundException("No existe esta comunidad")
        }

        if (comunidad.creador == participantesComunidadDTO.username) {
            throw BadRequestException("El creador no puede abandonar la comunidad")
        }

        participantesComunidadRepository.delete(union)

        return ParticipantesComunidadDTO(
            comunidad = union.comunidad,
            username = union.username
        )
    }

    fun booleanUsuarioApuntadoComunidad(participantesComunidadDTO: ParticipantesComunidadDTO): Boolean {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        // Los admins pueden verificar si cualquier usuario está apuntado a cualquier comunidad
        if (userActual.roles != "ADMIN" && auth.name != participantesComunidadDTO.username) {
            throw ForbiddenException("No tienes permisos para verificar si este usuario está apuntado a la comunidad")
        }

        comunidadRepository.findComunidadByUrl(participantesComunidadDTO.comunidad)
            .orElseThrow { BadRequestException("Esta comunidad no existe") }

        // Verificar que el usuario existe
        if (usuarioRepository.findFirstByUsername(participantesComunidadDTO.username).isEmpty) {
            throw NotFoundException("Usuario no encontrado")
        }

        return participantesComunidadRepository.findByUsernameAndComunidad(
            participantesComunidadDTO.username,
            participantesComunidadDTO.comunidad
        ).isPresent
    }

    fun contarUsuariosEnUnaComunidad(comunidad: String): Int {
        val auth = SecurityContextHolder.getContext().authentication
        // No es necesario verificar permisos específicos para contar usuarios en una comunidad
        // Los admins tienen acceso a toda la información.

        if (comunidadRepository.findComunidadByUrl(comunidad).isEmpty) {
            throw BadRequestException("Comunidad no existe")
        }
        val participaciones = participantesComunidadRepository.findByComunidad(comunidad)
        var usuarios: Int = 0
        participaciones.forEach {
            usuarios++
        }
        return usuarios
    }

    fun verificarCreadorAdministradorComunidad(comunidadUrl: String, username: String): Boolean {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        // Los admins pueden verificar esta información para cualquier usuario
        // y también se considera que los admins tienen todos los permisos
        if (userActual.roles == "ADMIN") {
            return true
        }

        if (auth.name != username) {
            throw ForbiddenException("No tienes permisos para verificar esta información")
        }

        val comunidad = comunidadRepository.findComunidadByUrl(comunidadUrl).orElseThrow {
            NotFoundException("Comunidad no existe")
        }

        usuarioRepository.findFirstByUsername(username).orElseThrow {
            NotFoundException("Usuario no encontrado")
        }

        val administradores = comunidad.administradores ?: emptyList()
        return comunidad.creador == username || administradores.contains(username)
    }

    fun verTodasComunidadesPublicas(): List<ComunidadDTO> {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        // Los admins pueden ver todas las comunidades, incluso las privadas
        if (userActual.roles == "ADMIN") {
            return comunidadRepository.findAll().map { comunidad ->
                crearComunidadDTO(comunidad)
            }
        }

        val todasLasComunidades = comunidadRepository.findAll()

        return todasLasComunidades
            .filter { !it.privada }
            .map { comunidad ->
                crearComunidadDTO(comunidad)
            }
    }

    fun unirseComunidadPorCodigo(participantesComunidadDTO: ParticipantesComunidadDTO, codigo: String): ParticipantesComunidadDTO {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        // Los admins no pueden unirse a comunidades
        if (userActual.roles == "ADMIN") {
            throw ForbiddenException("Los administradores no pueden unirse a comunidades")
        }

        if (auth.name != participantesComunidadDTO.username) {
            throw ForbiddenException("No tienes permisos para unir a este usuario a la comunidad")
        }

        val comunidad = comunidadRepository.findComunidadByUrl(participantesComunidadDTO.comunidad)
            .orElseThrow { BadRequestException("La comunidad no existe") }

        if (comunidad.codigoUnion == null) {
            throw BadRequestException("La comunidad ${comunidad.url} es publica")
        }

        usuarioRepository.findFirstByUsername(participantesComunidadDTO.username)
            .orElseThrow { NotFoundException("Usuario no encontrado") }

        if (participantesComunidadRepository.findByUsernameAndComunidad(
                participantesComunidadDTO.username,
                participantesComunidadDTO.comunidad
            ).isPresent
        ) {
            throw BadRequestException("El usuario ya está unido a esta comunidad")
        }

        if (codigo == comunidad.codigoUnion) {
            val union = ParticipantesComunidad(
                _id = null,
                comunidad = participantesComunidadDTO.comunidad,
                username = participantesComunidadDTO.username,
                fechaUnion = Date.from(Instant.now())
            )

            participantesComunidadRepository.insert(union)

            return participantesComunidadDTO
        } else {
            throw BadRequestException("El codigo de union no es correcto")
        }
    }

    fun verComunidadesPorUsuarioCreador(username: String): List<ComunidadDTO> {
        val auth = SecurityContextHolder.getContext().authentication
        val userActual = usuarioRepository.findFirstByUsername(auth.name).orElseThrow {
            throw NotFoundException("Usuario autenticado no encontrado")
        }

        // Los admins pueden ver las comunidades creadas por cualquier usuario
        if (userActual.roles != "ADMIN" && auth.name != username) {
            throw ForbiddenException("No tienes permisos para ver las comunidades creadas por este usuario")
        }

        // Verificar que el usuario existe
        if (!usuarioRepository.existsByUsername(username)) {
            throw NotFoundException("Usuario no encontrado")
        }

        // Buscar todas las comunidades donde el usuario es creador
        val comunidades = comunidadRepository.findByCreador(username)

        // Si no es creador de ninguna comunidad, devolver lista vacía
        if (comunidades.isEmpty()) {
            return emptyList()
        }

        return comunidades.map { comunidad ->
            crearComunidadDTO(comunidad)
        }
    }

    fun validateAndReplaceSpaces(inputList: List<String>): List<String> {
        return inputList.map {
            val trimmed = it.trim()
            if (trimmed.length > 25) throw BadRequestException("Los intereses no pueden exceder los 25 caracteres")
            if (trimmed.contains(" ")) throw BadRequestException("Con el fin de facilitar su uso, los intereses no pueden contener espacios")
            trimmed
        }
    }

    private fun generarCodigoUnico(): String {
        val caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val longitud = 10
        val random = Random()

        var codigoGenerado: String
        var codigoExistente: Boolean

        do {
            // Generar un nuevo código
            codigoGenerado = (1..longitud)
                .map { caracteres[random.nextInt(caracteres.length)] }
                .joinToString("")

            // Verificar si el código ya existe en alguna comunidad
            codigoExistente = comunidadRepository.findComunidadByCodigoUnion(codigoGenerado).isPresent

        } while (codigoExistente) // Repetir si el código ya existe

        return codigoGenerado
    }
}