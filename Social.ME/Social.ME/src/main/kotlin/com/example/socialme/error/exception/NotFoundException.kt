package com.es.aplicacion.error.exception

class NotFoundException(message: String): Exception("Not Found Exception (404). $message"){
}