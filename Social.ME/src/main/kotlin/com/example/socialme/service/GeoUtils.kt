package com.example.socialme.service

import com.example.socialme.model.Coordenadas
import kotlin.math.*

/**
 * Clase de utilidad para operaciones geoespaciales
 */
class GeoUtils {
    companion object {
        // Radio de la Tierra en kilómetros
        private const val RADIO_TIERRA = 6371.0

        /**
         * Calcula la distancia en kilómetros entre dos coordenadas usando la fórmula de Haversine
         * @param coordenadas1 Las primeras coordenadas
         * @param coordenadas2 Las segundas coordenadas
         * @return La distancia en kilómetros entre las dos coordenadas
         */
        fun calcularDistancia(coordenadas1: Coordenadas, coordenadas2: Coordenadas): Double {
            val lat1 = coordenadas1.latitud.toDouble()
            val lon1 = coordenadas1.longitud.toDouble()
            val lat2 = coordenadas2.latitud.toDouble()
            val lon2 = coordenadas2.longitud.toDouble()

            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)

            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)

            val c = 2 * atan2(sqrt(a), sqrt(1 - a))

            return RADIO_TIERRA * c
        }

        /**
         * Encuentra los límites de latitud y longitud para una distancia dada desde un punto
         * Útil para consultas geoespaciales
         * @param coordenadas Las coordenadas centrales
         * @param distanciaKm La distancia en kilómetros
         * @return Un mapa con los límites de latitud y longitud para la consulta
         */
        fun obtenerLimitesCoordenadas(coordenadas: Coordenadas, distanciaKm: Double): Map<String, Double> {
            val lat = coordenadas.latitud.toDouble()
            val lon = coordenadas.longitud.toDouble()

            // Conversión aproximada de kilómetros a grados
            val latRadio = distanciaKm / RADIO_TIERRA * (180.0 / PI)
            val lonRadio = distanciaKm / (RADIO_TIERRA * cos(Math.toRadians(lat))) * (180.0 / PI)

            return mapOf(
                "minLat" to (lat - latRadio),
                "maxLat" to (lat + latRadio),
                "minLon" to (lon - lonRadio),
                "maxLon" to (lon + lonRadio)
            )
        }
    }
}