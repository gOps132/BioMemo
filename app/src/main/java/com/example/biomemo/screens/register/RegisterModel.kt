package com.example.biomemo.screens.register

import com.example.biomemo.data.UserRepository

class RegisterModel {
    fun registerUser(username: String, password: String): Boolean {
        return UserRepository.register(username, password)
    }
}