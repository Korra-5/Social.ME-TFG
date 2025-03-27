package com.example.socialme.error.exception

class ForbiddenException (message: String): Exception("Forbidden (403). $message") {
}