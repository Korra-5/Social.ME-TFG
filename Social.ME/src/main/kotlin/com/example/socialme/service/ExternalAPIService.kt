package com.example.socialme.service

import com.es.aplicacion.domain.DatosMunicipios
import com.es.aplicacion.domain.DatosProvincias
import com.example.socialme.model.Direccion
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class ExternalAPIService (private val webClient: WebClient.Builder) {

    @Value("\${GEOAPI_KEY}")
    private lateinit var apiKey: String

    fun obtenerDatosDesdeApi(): DatosProvincias? {
        return webClient.build()
            .get()
            .uri("https://apiv1.geoapi.es/provincias?type=JSON&key=$apiKey")
            .retrieve()
            .bodyToMono(DatosProvincias::class.java)
            .block()
    }

    fun obtenerMunicipiosDesdeApi(cpro: String): DatosMunicipios? {
        return webClient.build()
            .get()
            .uri("https://apiv1.geoapi.es/municipios?CPRO=${cpro}&type=JSON&key=$apiKey")
            .retrieve()
            .bodyToMono(DatosMunicipios::class.java)
            .block() // ⚠️ Esto bloquea el hilo, usar `subscribe()` en código reactivo
    }

    fun verificarProvinciaExiste(nombreProvincia: String): Boolean {
        val provincias = obtenerDatosDesdeApi()
        return provincias?.data?.any { it.PRO.equals(nombreProvincia, ignoreCase = true) } ?: false
    }

    fun verificarMunicipioExiste(nombreMunicipio: String, nombreProvincia: String): Boolean {
        val provincias = obtenerDatosDesdeApi()
        val provincia = provincias?.data?.find { it.PRO.equals(nombreProvincia, ignoreCase = true) }

        if (provincia != null) {
            val municipios = obtenerMunicipiosDesdeApi(provincia.CPRO)
            return municipios?.data?.any { it.DMUN50.equals(nombreMunicipio, ignoreCase = true) } ?: false
        }
        return false
    }

}