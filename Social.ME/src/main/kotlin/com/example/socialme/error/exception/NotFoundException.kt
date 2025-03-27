package com.example.socialme.error.exception

class NotFoundException(message: String): Exception("Not Found Exception (404). $message"){
}