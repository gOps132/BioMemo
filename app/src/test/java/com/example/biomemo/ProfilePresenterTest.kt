package com.example.biomemo

import com.example.biomemo.data.remote.SupabaseAuthResult
import com.example.biomemo.data.ExplorerProfile
import com.example.biomemo.data.remote.ProfileResult
import com.example.biomemo.screens.profile.ProfileAuthModel
import com.example.biomemo.screens.profile.ProfileContract
import com.example.biomemo.screens.profile.ProfilePresenter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePresenterTest {
    @Test
    fun successfulProfileLoadRendersProfile() = runBlocking {
        val profile = ExplorerProfile(
            id = "user-1",
            username = "fernkeeper",
            email = "alex.rivera@biomemo.app",
            avatarUrl = "https://example.test/avatar.png"
        )
        val view = FakeProfileView()
        val presenter = ProfilePresenter(
            view,
            FakeProfileModel(
                signOutResult = SupabaseAuthResult.Success(null),
                profileResult = ProfileResult.Success(profile)
            )
        )

        presenter.onProfileOpened()

        assertEquals(profile, view.profile)
    }

    @Test
    fun failedProfileLoadShowsError() = runBlocking {
        val view = FakeProfileView()
        val presenter = ProfilePresenter(
            view,
            FakeProfileModel(
                signOutResult = SupabaseAuthResult.Success(null),
                profileResult = ProfileResult.Failure("Profile unavailable")
            )
        )

        presenter.onProfileOpened()

        assertEquals("Profile unavailable", view.errorMessage)
    }

    @Test
    fun successfulLogoutNavigatesToLogin() = runBlocking {
        val view = FakeProfileView()
        val presenter = ProfilePresenter(
            view,
            FakeProfileModel(signOutResult = SupabaseAuthResult.Success(null))
        )

        presenter.onLogoutClicked()

        assertTrue(view.loggedOut)
    }

    @Test
    fun failedLogoutShowsError() = runBlocking {
        val view = FakeProfileView()
        val presenter = ProfilePresenter(
            view,
            FakeProfileModel(signOutResult = SupabaseAuthResult.Failure("Sign out failed"))
        )

        presenter.onLogoutClicked()

        assertEquals("Sign out failed", view.errorMessage)
        assertTrue(!view.loggedOut)
    }

    private class FakeProfileModel(
        private val signOutResult: SupabaseAuthResult,
        private val profileResult: ProfileResult = ProfileResult.Failure("Profile not loaded")
    ) : ProfileAuthModel {
        override suspend fun loadProfile(): ProfileResult = profileResult

        override suspend fun signOut(): SupabaseAuthResult = signOutResult
    }

    private class FakeProfileView : ProfileContract.View {
        var loggedOut = false
        var errorMessage: String? = null
        var profile: ExplorerProfile? = null

        override fun showProfile(profile: ExplorerProfile) {
            this.profile = profile
        }

        override fun logout() {
            loggedOut = true
        }

        override fun showError(message: String) {
            errorMessage = message
        }
    }
}
