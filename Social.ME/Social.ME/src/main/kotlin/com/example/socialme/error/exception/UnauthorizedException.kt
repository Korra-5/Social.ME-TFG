package com.es.aplicacion.error.exception

class UnauthorizedException(message: String) : Exception("Not authorized exception (401). $message") {
}