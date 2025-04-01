package com.example.socialme.controller

import com.example.socialme.service.GridFSService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.IOException

@RestController
@RequestMapping("/files")
class FileController {

    @Autowired
    private lateinit var gridFSService: GridFSService


    @GetMapping("/download/{id}")
    fun downloadFile(@PathVariable id: String): ResponseEntity<ByteArrayResource> {
        try {
            // Obtener el archivo desde GridFS
            val gridFSFile = gridFSService.getFile(id) ?: return ResponseEntity.notFound().build()

            // Obtener el contenido y metadatos del archivo
            val fileContent = gridFSService.getFileContent(id)
            val resource = ByteArrayResource(fileContent)

            // Configurar los headers de la respuesta
            val headers = HttpHeaders()
            headers.contentType = MediaType.parseMediaType(
                gridFSFile.metadata?.getString("contentType") ?: MediaType.APPLICATION_OCTET_STREAM_VALUE
            )
            headers.contentLength = fileContent.size.toLong()
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"${gridFSFile.filename}\"")

            return ResponseEntity.ok()
                .headers(headers)
                .body(resource)
        } catch (e: IOException) {
            e.printStackTrace()
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        } catch (e: Exception) {
            e.printStackTrace()
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }
}