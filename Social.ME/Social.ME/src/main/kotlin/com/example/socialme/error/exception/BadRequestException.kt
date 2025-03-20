package com.es.aplicacion.error.exception

class BadRequestException (message: String): Exception("Bad Request Exception (400). $message") {
}