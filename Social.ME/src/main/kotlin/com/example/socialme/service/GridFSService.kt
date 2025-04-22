package com.example.socialme.service

import com.mongodb.client.gridfs.model.GridFSFile
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.gridfs.GridFsOperations
import org.springframework.data.mongodb.gridfs.GridFsTemplate
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Base64

@Service
class GridFSService {

    @Autowired
    private lateinit var gridFsTemplate: GridFsTemplate

    @Autowired
    private lateinit var gridFsOperations: GridFsOperations

    /**
     * Stores a file in GridFS and returns its ID
     */
    fun storeFile(file: MultipartFile, metadata: Map<String, String> = emptyMap()): String {
        try {
            val inputStream = file.inputStream
            val metadataDocument = org.bson.Document()
            metadata.forEach { (key, value) -> metadataDocument.append(key, value) }
            metadataDocument.append("filename", file.originalFilename)
            metadataDocument.append("contentType", file.contentType)

            val id = gridFsTemplate.store(
                inputStream,
                file.originalFilename,
                file.contentType,
                metadataDocument
            )
            return id.toString()
        } catch (e: IOException) {
            throw RuntimeException("Failed to store file", e)
        }
    }

    /**
     * Stores a file from base64 string in GridFS and returns its ID
     */
    fun storeFileFromBase64(base64Content: String, filename: String, contentType: String, metadata: Map<String, String> = emptyMap()): String {
        try {
            // Remove base64 prefix if present (e.g., "data:image/jpeg;base64,")
            val base64Data = if (base64Content.contains(",")) {
                base64Content.substring(base64Content.indexOf(",") + 1)
            } else {
                base64Content
            }

            val decodedData = Base64.getDecoder().decode(base64Data)
            val inputStream = ByteArrayInputStream(decodedData)

            val metadataDocument = org.bson.Document()
            metadata.forEach { (key, value) -> metadataDocument.append(key, value) }
            metadataDocument.append("filename", filename)
            metadataDocument.append("contentType", contentType)

            val id = gridFsTemplate.store(
                inputStream,
                filename,
                contentType,
                metadataDocument
            )
            return id.toString()
        } catch (e: Exception) {
            throw RuntimeException("Failed to store file from base64", e)
        }
    }

    /**
     * Retrieves a file by its ID
     */
    fun getFile(id: String): GridFSFile? {
        return gridFsOperations.findOne(Query(Criteria.where("_id").`is`(ObjectId(id))))
    }

    /**
     * Retrieves a file's content as byte array
     */
    fun getFileContent(id: String): ByteArray {
        val file = getFile(id) ?: throw RuntimeException("File not found with ID: $id")
        val resource = gridFsOperations.getResource(file)
        return resource.inputStream.readAllBytes()
    }

    /**
     * Deletes a file by its ID
     */
    fun deleteFile(id: String?) {
        gridFsTemplate.delete(Query(Criteria.where("_id").`is`(ObjectId(id))))
    }
}