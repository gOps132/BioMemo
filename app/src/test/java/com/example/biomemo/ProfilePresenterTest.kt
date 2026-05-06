package com.example.biomemo

import com.example.biomemo.data.remote.SupabaseAuthResult
import com.example.biomemo.screens.profile.ProfileAuthModel
import com.example.biomemo.screens.profile.ProfileContract
import com.example.biomemo.screens.profile.ProfilePresenter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePresenterTest {
    @Test
    fun successfulLogoutNavigatesToLogin() = runBlocking {
        val view = FakeProfileView()
        val presenter = ProfilePresenter(view, FakeProfileModel(SupabaseAuthResult.Success(null)))

        presenter.onLogoutClicked()

        assertTrue(view.loggedOut)
    }

    @Test
    fun failedLogoutShowsError() = runBlocking {
        val view = FakeProfileView()
        val presenter = ProfilePresenter(view, FakeProfileModel(SupabaseAuthResult.Failure("Sign out failed")))

        presenter.onLogoutClicked()

        assertEquals("Sign out failed", view.errorMessage)
        assertTrue(!view.loggedOut)
    }

    private class FakeProfileModel(
        private val result: SupabaseAuthResult
    ) : ProfileAuthModel {
        override suspend fun signOut(): SupabaseAuthResult = result
    }

    private class FakeProfileView : ProfileContract.View {
        var loggedOut = false
        var errorMessage: String? = null

        override fun logout() {
            loggedOut = true
        }

        override fun showError(message: String) {
            errorMessage = message
        }
    }
}
