package com.example.biomemo.screens.login

import com.example.biomemo.data.UserRepository

class LoginModel {
    fun authenticate(username: String, password: String): Boolean {
        // Calls the shared data layer
        return UserRepository.login(username, password)
    }
}