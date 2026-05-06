package com.example.biomemo

import com.example.biomemo.data.remote.AuthUser
import com.example.biomemo.data.remote.ProfileResult
import com.example.biomemo.data.remote.SupabaseProfileGateway
import com.example.biomemo.data.remote.SupabaseProfileRepository
import com.example.biomemo.data.remote.SupabaseProfileRow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseProfileRepositoryTest {
    @Test
    fun loadCurrentProfileUsesCurrentAuthUserForProfileLookup() = runBlocking {
        val gateway = FakeProfileGateway(
            currentUser = AuthUser("auth-user-1", "trail@biomemo.app"),
            rowsById = mapOf(
                "auth-user-1" to SupabaseProfileRow(
                    id = "auth-user-1",
                    username = "trailscout",
                    avatarUrl = "https://example.test/maya.png"
                ),
                "other-user" to SupabaseProfileRow(
                    id = "other-user",
                    username = "wrongexplorer",
                    avatarUrl = null
                )
            )
        )
        val repository = SupabaseProfileRepository(gateway)

        val result = repository.loadCurrentProfile()

        assertEquals(listOf("auth-user-1"), gateway.profileLookups)
        assertTrue(result is ProfileResult.Success)
        val profile = (result as ProfileResult.Success).profile
        assertEquals("auth-user-1", profile.id)
        assertEquals("trail@biomemo.app", profile.email)
        assertEquals("trailscout", profile.username)
    }

    @Test
    fun loadCurrentProfileFailsWhenNoAuthUserExists() = runBlocking {
        val repository = SupabaseProfileRepository(FakeProfileGateway(currentUser = null))

        val result = repository.loadCurrentProfile()

        assertTrue(result is ProfileResult.Failure)
        assertEquals("Please sign in again to view your profile", (result as ProfileResult.Failure).message)
    }

    @Test
    fun loadCurrentProfileFailsWhenProfileRowIsMissing() = runBlocking {
        val repository = SupabaseProfileRepository(
            FakeProfileGateway(currentUser = AuthUser("auth-user-1", "trail@biomemo.app"))
        )

        val result = repository.loadCurrentProfile()

        assertTrue(result is ProfileResult.Failure)
        assertEquals("Profile not found", (result as ProfileResult.Failure).message)
    }
}

private class FakeProfileGateway(
    private val currentUser: AuthUser?,
    private val rowsById: Map<String, SupabaseProfileRow> = emptyMap(),
    private val failure: Throwable? = null
) : SupabaseProfileGateway {
    val profileLookups = mutableListOf<String>()

    override fun currentUser(): AuthUser? = currentUser

    override suspend fun fetchProfile(userId: String): SupabaseProfileRow? {
        failure?.let { throw it }
        profileLookups.add(userId)
        return rowsById[userId]
    }
}
