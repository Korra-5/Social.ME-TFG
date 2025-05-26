
package com.example.socialme.utils

import com.example.socialme.error.exception.BadRequestException

object ContentValidator {

    fun validarContenidoInapropiado(vararg textos: String) {
        try {
            // Leer el archivo de palabras malsonantes
            val palabrasMalsonantes = this::class.java.classLoader
                .getResourceAsStream("palabrasMalsonantes.txt")
                ?.bufferedReader()
                ?.readText()
                ?.split(",")
                ?.map { it.trim().lowercase() }
                ?: emptyList()

            // Validar cada texto proporcionado
            textos.forEach { texto ->
                if (texto.isNotBlank()) {
                    val textoLower = texto.lowercase()

                    // Buscar si contiene alguna palabra malsonante
                    palabrasMalsonantes.forEach { palabra ->
                        if (textoLower.contains(palabra)) {
                            throw BadRequestException("El contenido contiene palabras no permitidas")
                        }
                    }
                }
            }
        } catch (e: BadRequestException) {
            throw e
        } catch (e: Exception) {
            // Si hay error leyendo el archivo, continuar sin validación
            println("Advertencia: No se pudo cargar el filtro de contenido: ${e.message}")
        }
    }
}
