package com.example.biomemo.data

object UserRepository {
    // dumb in-memory db
    private val users = mutableMapOf<String, String>()

    fun register(username: String, password: String): Boolean {
        if (users.containsKey(username)) return false
        users[username] = password
        return true
    }

    fun login(username: String, password: String): Boolean {
        // This checks if the key exists AND if the value (password) matches
        return users.containsKey(username) && users[username] == password
    }
}