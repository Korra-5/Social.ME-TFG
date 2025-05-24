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
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class ComunidadService {

    @Autowired
    private lateinit var actividadesComunidadRepository: ActividadesComunidadRepository

    @Autowired
    private lateinit var participantesComunidadRepository: ParticipantesComunidadRepository

    @Autowired
    private lateinit var chatService: ChatService

    @Autowired
    private lateinit var comunidadRepository: ComunidadRepository

    @Autowired
    private lateinit var usuarioRepository: UsuarioRepository

    @Autowired
    private lateinit var gridFSService: GridFSService

    fun crearComunidad(comunidadCreateDTO: ComunidadCreateDTO): ComunidadDTO {
        if (comunidadRepository.findComunidadByUrl(comunidadCreateDTO.url).isPresent) {
            throw BadRequestException("Comunidad existente")
        }

        //Sustituye por guiones los espacios para que las url sean más accesibles (también las paso a minusculas)
        val formattedUrl = comunidadCreateDTO.url.trim().split(Regex("\\s+")).joinToString("-").toLowerCase()

        if (comunidadCreateDTO.nombre.length > 40) {
            throw BadRequestException("Este nombre es demasiado largo, pruebe con uno inferior a 40 caracteres")
        }
        if (comunidadCreateDTO.descripcion.length > 5000) {
            throw BadRequestException("Lo sentimos, la descripción no puede superar los 5000 caracteres")
        }

        //Verifica que los intereses no tengan espacios ni superen los 25 caracteres
        validateAndReplaceSpaces(listOf(formattedUrl))

        if (!usuarioRepository.existsByUsername(comunidadCreateDTO.creador)) {
            throw NotFoundException("Usuario no encontrado")
        }

        val comunidadesCreadas = comunidadRepository.countByCreador(comunidadCreateDTO.creador)
        if (comunidadesCreadas >= 3) {
            throw ForbiddenException("Has alcanzado el límite máximo de 3 comunidades creadas")
        }


        // Formatea la foto de perfil a Gridfs
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
                fotoCarruselIds = null,
                administradores = null,
                fechaCreacion = Date.from(Instant.now()),
                url = formattedUrl,
                comunidadGlobal = comunidadCreateDTO.comunidadGlobal,
                privada = comunidadCreateDTO.privada,
                coordenadas = comunidadCreateDTO.coordenadas,
                codigoUnion = if (comunidadCreateDTO.privada) {
                    generarCodigoUnico()
                }else{
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

        return ComunidadDTO(
            url = formattedUrl,
            nombre = comunidadCreateDTO.nombre,
            comunidadGlobal = comunidadCreateDTO.comunidadGlobal,
            creador = comunidadCreateDTO.creador,
            intereses = comunidadCreateDTO.intereses,
            fotoCarruselIds = null,
            fotoPerfilId = fotoPerfilId,
            descripcion = comunidadCreateDTO.descripcion,
            fechaCreacion = Date.from(Instant.now()),
            administradores = null,
            privada = comunidadCreateDTO.privada,
            coordenadas = comunidadCreateDTO.coordenadas,
            codigoUnion = comunidadCreateDTO.codigoUnion
        )
    }

    fun verComunidadPorUrl(url: String) : ComunidadDTO {
        val comunidad=comunidadRepository.findComunidadByUrl(url).orElseThrow {
            throw NotFoundException("Comunidad not found: $url")
        }
        return  ComunidadDTO(
            nombre = comunidad.nombre,
            descripcion = comunidad.descripcion,
            creador = comunidad.creador,
            intereses = comunidad.intereses,
            fotoPerfilId = comunidad.fotoPerfilId,
            fotoCarruselIds = comunidad.fotoCarruselIds,
            administradores = comunidad.administradores,
            fechaCreacion = comunidad.fechaCreacion,
            comunidadGlobal = comunidad.comunidadGlobal,
            privada = comunidad.privada,
            url =comunidad.url,
            coordenadas = comunidad.coordenadas,
            codigoUnion = comunidad.codigoUnion
        )
    }

    fun verComunidadesPublicasEnZona(
        username: String
    ): List<ComunidadDTO> {
        val distancia=usuarioRepository.findFirstByUsername(username).orElseThrow {
            throw NotFoundException("Usuario no existe")
        }.radarDistancia.toFloat()

        // Obtenemos todas las comunidades
        val todasLasComunidades = comunidadRepository.findAll()

        // Obtenemos el usuario para acceder a sus coordenadas e intereses
        val usuario = usuarioRepository.findFirstByUsername(username)
            .orElseThrow { throw NotFoundException("Usuario no existe") }

        val coordenadasUser = usuario.coordenadas
        val interesesUser = usuario.intereses

        // Obtenemos las comunidades a las que el usuario ya está unido
        val comunidadesDelUsuario = participantesComunidadRepository.findByUsername(username)
            .map { it.comunidad }
            .toSet()

        return todasLasComunidades
            .filter { !it.privada }
            .filter { comunidad ->
                // Filtrar aquellas a las que el usuario no esté unido ya
                !comunidadesDelUsuario.contains(comunidad.url)
            }
            .filter { comunidad ->
                // Verificar la distancia
                verificarDistancia(comunidad.coordenadas, coordenadasUser, distancia)
            }
            .map { comunidad ->
                ComunidadDTO(
                    url = comunidad.url,
                    nombre = comunidad.nombre,
                    descripcion = comunidad.descripcion,
                    intereses = comunidad.intereses,
                    fotoPerfilId = comunidad.fotoPerfilId,
                    fotoCarruselIds = comunidad.fotoCarruselIds,
                    creador = comunidad.creador,
                    administradores = comunidad.administradores,
                    fechaCreacion = comunidad.fechaCreacion,
                    comunidadGlobal = comunidad.comunidadGlobal,
                    privada = comunidad.privada,
                    coordenadas = comunidad.coordenadas,
                    codigoUnion = comunidad.codigoUnion
                )
            }
            .sortedWith(compareByDescending<ComunidadDTO> { comunidadDTO ->
                // Primera ordenación: por número de intereses coincidentes
                comunidadDTO.intereses.count { interes -> interesesUser.contains(interes) }
            }.thenByDescending {
                // Segunda ordenación: por fecha de creación (las más recientes primero)
                it.fechaCreacion
            })
    }

    // Verifica si una comunidad está dentro del radio de distancia especificado
    private fun verificarDistancia(coordenadasComunidad: Coordenadas?, coordenadasUser: Coordenadas?, distanciaKm: Float?): Boolean {
        // Si falta algún parámetro necesario para el cálculo de distancia, devuelve true para incluir la comunidad
        if (coordenadasComunidad == null || coordenadasUser == null || distanciaKm == null) {
            return true
        }

        // Cálculo de la distancia
        val distanciaCalculada = GeoUtils.calcularDistancia(coordenadasUser, coordenadasComunidad)

        // Verificamos si la distancia calculada es menor o igual que la distancia especificada
        return distanciaCalculada <= distanciaKm
    }


    fun unirseComunidad(participantesComunidadDTO: ParticipantesComunidadDTO): ParticipantesComunidadDTO {
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
        val comunidad = comunidadRepository.findComunidadByUrl(url).orElseThrow { BadRequestException("Esta comunidad no existe") }

        val comunidadDto = ComunidadDTO(
            url = comunidad.url,
            nombre = comunidad.nombre,
            comunidadGlobal = comunidad.comunidadGlobal,
            creador = comunidad.creador,
            intereses = comunidad.intereses,
            fotoCarruselIds = comunidad.fotoCarruselIds,
            fotoPerfilId = comunidad.fotoPerfilId,
            descripcion = comunidad.descripcion,
            fechaCreacion = comunidad.fechaCreacion,
            administradores = comunidad.administradores,
            privada = comunidad.privada,
            coordenadas = comunidad.coordenadas,
            codigoUnion = comunidad.codigoUnion
        )

        // Elimina imagenes de GridFS
        try {
            gridFSService.deleteFile(comunidad.fotoPerfilId)
            comunidad.fotoCarruselIds?.forEach { fileId ->
                gridFSService.deleteFile(fileId)
            }
        } catch (e: Exception) {
            // Log para mis pruebas
            println("Error deleting GridFS files: ${e.message}")
        }

        // Eliminar primero todos los participantes de la comunidad
        participantesComunidadRepository.deleteByComunidad(comunidad.url)

        // Luego eliminar las actividades asociadas a la comunidad
        actividadesComunidadRepository.deleteByComunidad(comunidad.url)

        // Finalmente eliminar la comunidad
        comunidadRepository.delete(comunidad)

        chatService.eliminarMensajesComunidad(url)

        return comunidadDto
    }

    fun getComunidadPorUsername(username: String): List<Comunidad> {
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
        // Buscar la comunidad existente usando currentURL
        val comunidadExistente = comunidadRepository.findComunidadByUrl(comunidadUpdateDTO.currentURL).orElseThrow {
            throw NotFoundException("Comunidad con URL ${comunidadUpdateDTO.currentURL} no encontrada")
        }

        // Si se está cambiando la URL, validar que la nueva no exista ya
        if (comunidadUpdateDTO.newUrl != comunidadUpdateDTO.currentURL) {
            comunidadRepository.findComunidadByUrl(comunidadUpdateDTO.newUrl).ifPresent {
                throw BadRequestException("Ya existe una comunidad con la URL ${comunidadUpdateDTO.newUrl}, prueba con otra URL")
            }
        }

        // Verificar que los administradores existan
        comunidadUpdateDTO.administradores?.forEach { admin ->
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
            comunidadUpdateDTO.fotoPerfilId
        } else {
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
            comunidadUpdateDTO.fotoCarruselIds ?: comunidadExistente.fotoCarruselIds
        }

        // Actualizar la información de la comunidad
        comunidadExistente.apply {
            url = comunidadUpdateDTO.newUrl
            nombre = comunidadUpdateDTO.nombre
            descripcion = comunidadUpdateDTO.descripcion
            intereses = comunidadUpdateDTO.intereses
            administradores = comunidadUpdateDTO.administradores
            fotoPerfilId = nuevaFotoPerfilId
            fotoCarruselIds = nuevasFotosCarruselIds
        }

        val comunidadActualizada = comunidadRepository.save(comunidadExistente)

        // Si se ha cambiado la URL, actualizar referencias en otras colecciones
        if (urlAntigua != comunidadActualizada.url) {
            // Actualizar referencias en ActividadesComunidad
            val actividades = actividadesComunidadRepository.findByComunidad(urlAntigua).orElseThrow {
                NotFoundException("Actividades no encontradas")
            }
            actividades.forEach { actividad ->
                actividad.comunidad = comunidadActualizada.url
                actividadesComunidadRepository.save(actividad)
            }

            // Actualizar referencias en ParticipantesComunidad
            val participantes = participantesComunidadRepository.findByComunidad(urlAntigua)
            participantes.forEach { participante ->
                participante.comunidad = comunidadActualizada.url
                participantesComunidadRepository.save(participante)
            }
        }

        // Retornar el DTO actualizado
        return ComunidadDTO(
            url = comunidadActualizada.url,
            nombre = comunidadActualizada.nombre,
            comunidadGlobal = comunidadActualizada.comunidadGlobal,
            creador = comunidadActualizada.creador,
            intereses = comunidadActualizada.intereses,
            fotoCarruselIds = comunidadActualizada.fotoCarruselIds,
            fotoPerfilId = comunidadActualizada.fotoPerfilId,
            descripcion = comunidadActualizada.descripcion,
            fechaCreacion = comunidadActualizada.fechaCreacion,
            administradores = comunidadActualizada.administradores,
            privada = comunidadActualizada.privada,
            coordenadas = comunidadActualizada.coordenadas,
            codigoUnion = if (comunidadExistente.privada) {
                comunidadExistente.codigoUnion
            }else{
                if(comunidadActualizada.privada){
                    generarCodigoUnico()
                }else{
                    null
                }
            }
        )
    }

    fun salirComunidad(participantesComunidadDTO: ParticipantesComunidadDTO): ParticipantesComunidadDTO {
        val union = participantesComunidadRepository.findByUsernameAndComunidad(username = participantesComunidadDTO.username, comunidad = participantesComunidadDTO.comunidad).orElseThrow {
            throw BadRequestException("No estás en esta comunidad")
        }

        val comunidad=comunidadRepository.findComunidadByUrl(participantesComunidadDTO.comunidad).orElseThrow {
            throw NotFoundException("No existe esta comunidad")
        }

        if (comunidad.creador==participantesComunidadDTO.username) {
            throw BadRequestException("El creador no puede abandonar la comunidad")
        }

        participantesComunidadRepository.delete(union)

        return ParticipantesComunidadDTO(
            comunidad = union.comunidad,
            username = union.username
        )
    }

    fun validateAndReplaceSpaces(inputList: List<String>): List<String> {
        return inputList.map {
            val trimmed = it.trim()
            if (trimmed.length > 25) throw BadRequestException("Los intereses no pueden exceder los 25 caracteres")
            if (trimmed.contains(" ")) throw BadRequestException("Con el fin de facilitar su uso, los intereses no pueden contener espacios")
            trimmed
        }
    }

    fun booleanUsuarioApuntadoComunidad(participantesComunidadDTO: ParticipantesComunidadDTO):Boolean{
        comunidadRepository.findComunidadByUrl(participantesComunidadDTO.comunidad)
            .orElseThrow { BadRequestException("Esta comunidad no existe") }

        // Verificar que el usuario existe
        if (usuarioRepository.findFirstByUsername(participantesComunidadDTO.username).isEmpty) {
            throw NotFoundException("Usuario no encontrado")
        }

        return participantesComunidadRepository.findByUsernameAndComunidad(participantesComunidadDTO.username,participantesComunidadDTO.comunidad).isPresent

    }

    fun contarUsuariosEnUnaComunidad(comunidad:String):Int{
        if (comunidadRepository.findComunidadByUrl(comunidad).isEmpty) {
            throw BadRequestException("Comunidad no existe")
        }
        val participaciones=participantesComunidadRepository.findByComunidad(comunidad)
        var usuarios:Int=0
        participaciones.forEach {
            usuarios++
        }
        return usuarios
    }

    fun verificarCreadorAdministradorComunidad(comunidadUrl: String, username: String):Boolean{
        val comunidad=comunidadRepository.findComunidadByUrl(comunidadUrl).orElseThrow {
            NotFoundException("Comunidad no existe")
        }
        usuarioRepository.findFirstByUsername(username).orElseThrow {
            NotFoundException("Usuario no encontrado")
        }

        return comunidad.creador == username || comunidad.administradores!!.contains(username)
    }

    fun verTodasComunidadesPublicas():List<ComunidadDTO>{
        val todasLasComunidades = comunidadRepository.findAll()

        return todasLasComunidades
            .filter { !it.privada }
            .map { comunidad ->
                ComunidadDTO(
                    url = comunidad.url,
                    nombre = comunidad.nombre,
                    descripcion = comunidad.descripcion,
                    intereses = comunidad.intereses,
                    fotoPerfilId = comunidad.fotoPerfilId,
                    fotoCarruselIds = comunidad.fotoCarruselIds,
                    creador = comunidad.creador,
                    administradores = comunidad.administradores,
                    fechaCreacion = comunidad.fechaCreacion,
                    comunidadGlobal = comunidad.comunidadGlobal,
                    privada = comunidad.privada,
                    coordenadas = comunidad.coordenadas,
                    codigoUnion = comunidad.codigoUnion
                )
            }
    }

    fun unirseComunidadPorCodigo(participantesComunidadDTO: ParticipantesComunidadDTO,codigo:String):ParticipantesComunidadDTO{
        val comunidad=comunidadRepository.findComunidadByUrl(participantesComunidadDTO.comunidad)
            .orElseThrow { BadRequestException("La comunidad no existe") }

        if (comunidad.codigoUnion==null){
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
        }else{
            throw BadRequestException("El codigo de union no es correcto")
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

    fun verComunidadesPorUsuarioCreador(username: String): List<ComunidadDTO> {
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
            ComunidadDTO(
                url = comunidad.url,
                nombre = comunidad.nombre,
                comunidadGlobal = comunidad.comunidadGlobal,
                creador = comunidad.creador,
                intereses = comunidad.intereses,
                fotoCarruselIds = comunidad.fotoCarruselIds,
                fotoPerfilId = comunidad.fotoPerfilId,
                descripcion = comunidad.descripcion,
                fechaCreacion = comunidad.fechaCreacion,
                administradores = comunidad.administradores,
                privada = comunidad.privada,
                coordenadas = comunidad.coordenadas,
                codigoUnion = comunidad.codigoUnion
            )
        }
    }

    fun eliminarUsuarioDeComunidad(
        participantesComunidadDTO: ParticipantesComunidadDTO,
        usuarioSolicitante: String
    ): ParticipantesComunidadDTO {
        val usuarioAEliminar = participantesComunidadDTO.username
        val comunidadUrl = participantesComunidadDTO.comunidad

        // Verificar que la comunidad existe
        val comunidad = comunidadRepository.findComunidadByUrl(comunidadUrl)
            .orElseThrow { NotFoundException("Comunidad no encontrada") }

        // Verificar que el usuario a eliminar existe
        usuarioRepository.findFirstByUsername(usuarioAEliminar)
            .orElseThrow { NotFoundException("Usuario a eliminar no encontrado") }

        // Verificar que el usuario solicitante existe
        usuarioRepository.findFirstByUsername(usuarioSolicitante)
            .orElseThrow { NotFoundException("Usuario solicitante no encontrado") }

        // Verificar si el usuario a eliminar pertenece a la comunidad
        val participacion = participantesComunidadRepository.findByUsernameAndComunidad(
            usuarioAEliminar, comunidadUrl
        ).orElseThrow { BadRequestException("El usuario a eliminar no pertenece a esta comunidad") }

        // Verificar si el usuario solicitante es creador o administrador
        val esCreador = comunidad.creador == usuarioSolicitante
        val esAdmin = comunidad.administradores?.contains(usuarioSolicitante) ?: false

        if (!esCreador && !esAdmin) {
            throw ForbiddenException("No tienes permisos para eliminar usuarios de esta comunidad")
        }

        // Verificar permisos según roles
        if (!esCreador && esAdmin) {
            val usuarioAEliminarEsCreador = comunidad.creador == usuarioAEliminar
            val usuarioAEliminarEsAdmin = comunidad.administradores?.contains(usuarioAEliminar) ?: false

            if (usuarioAEliminarEsCreador || usuarioAEliminarEsAdmin) {
                throw ForbiddenException("Los administradores no pueden eliminar al creador ni a otros administradores")
            }
        }

        // No permitir que el creador se elimine a sí mismo
        if (esCreador && usuarioAEliminar == usuarioSolicitante) {
            throw BadRequestException("El creador no puede abandonar la comunidad")
        }

        // Eliminar al usuario de la comunidad
        participantesComunidadRepository.delete(participacion)

        return participantesComunidadDTO
    }

    fun cambiarCreadorComunidad(comunidadUrl: String, creadorActual: String, nuevoCreador: String): ComunidadDTO {
        // Verificar que la comunidad existe
        val comunidad = comunidadRepository.findComunidadByUrl(comunidadUrl).orElseThrow {
            throw NotFoundException("Comunidad con URL $comunidadUrl no encontrada")
        }

        // Verificar que el usuario actual es el creador
        if (comunidad.creador != creadorActual) {
            throw ForbiddenException("Solo el creador actual puede transferir la propiedad de la comunidad")
        }

        // Verificar que el nuevo creador existe
        if (!usuarioRepository.existsByUsername(nuevoCreador)) {
            throw NotFoundException("El usuario $nuevoCreador no existe")
        }

        // Verificar que el nuevo creador es miembro de la comunidad
        if (!participantesComunidadRepository.findByUsernameAndComunidad(nuevoCreador, comunidadUrl).isPresent) {
            throw BadRequestException("El usuario $nuevoCreador debe ser miembro de la comunidad para convertirse en creador")
        }

        // Verificar que no es el mismo usuario
        if (creadorActual == nuevoCreador) {
            throw BadRequestException("No puedes transferir la propiedad a ti mismo")
        }

        // Actualizar el creador
        comunidad.creador = nuevoCreador

        // Si el nuevo creador estaba en la lista de administradores, eliminarlo de ahí
        val nuevosAdministradores = comunidad.administradores?.toMutableList() ?: mutableListOf()
        nuevosAdministradores.remove(nuevoCreador)

        // Añadir al creador anterior como administrador si no está ya
        if (!nuevosAdministradores.contains(creadorActual)) {
            nuevosAdministradores.add(creadorActual)
        }

        comunidad.administradores = nuevosAdministradores

        // Guardar los cambios
        val comunidadActualizada = comunidadRepository.save(comunidad)

        // Retornar el DTO actualizado
        return ComunidadDTO(
            url = comunidadActualizada.url,
            nombre = comunidadActualizada.nombre,
            comunidadGlobal = comunidadActualizada.comunidadGlobal,
            creador = comunidadActualizada.creador,
            intereses = comunidadActualizada.intereses,
            fotoCarruselIds = comunidadActualizada.fotoCarruselIds,
            fotoPerfilId = comunidadActualizada.fotoPerfilId,
            descripcion = comunidadActualizada.descripcion,
            fechaCreacion = comunidadActualizada.fechaCreacion,
            administradores = comunidadActualizada.administradores,
            privada = comunidadActualizada.privada,
            coordenadas = comunidadActualizada.coordenadas,
            codigoUnion = comunidadActualizada.codigoUnion
        )
    }

}