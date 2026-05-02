package com.example.biomemo.screens.profile

class ProfileModel {
    fun getFormattedUsername(username: String?): String {
        val name = if (username.isNullOrEmpty()) "Guest User" else username
        return "Field name: $name"
    }
}
