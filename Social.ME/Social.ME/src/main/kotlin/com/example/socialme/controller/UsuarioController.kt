package com.example.socialme.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/Usuario")
class UsuarioController {

    @Autowired
    private lateinit var tokenService:TokenService
}