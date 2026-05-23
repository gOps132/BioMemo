package com.example.biomemo

import com.example.biomemo.features.auth.domain.ExplorerProfile
import com.example.biomemo.features.auth.domain.ProfileResult
import com.example.biomemo.screens.username.UsernameSetupContract
import com.example.biomemo.screens.username.UsernameSetupModel
import com.example.biomemo.screens.username.UsernameSetupPresenter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UsernameSetupPresenterTest {
    @Test
    fun blankUsernameShowsErrorWithoutSaving() = runBlocking {
        val model = FakeUsernameSetupModel(ProfileResult.Failure("Should not save"))
        val view = FakeUsernameSetupView()
        val presenter = UsernameSetupPresenter(view, model)

        presenter.onSaveClicked("   ")

        assertEquals("Choose a username", view.lastError)
        assertEquals(null, model.savedUsername)
    }

    @Test
    fun successfulSaveNavigatesToDashboard() = runBlocking {
        val model = FakeUsernameSetupModel(
            ProfileResult.Success(
                ExplorerProfile(
                    id = "auth-user-1",
                    username = "trailkeeper",
                    email = "trail@biomemo.app",
                    avatarUrl = null
                )
            )
        )
        val view = FakeUsernameSetupView()
        val presenter = UsernameSetupPresenter(view, model)

        presenter.onSaveClicked(" trailkeeper ")

        assertEquals("trailkeeper", model.savedUsername)
        assertEquals("trailkeeper", view.dashboardUsername)
        assertEquals(listOf(true, false), view.savingStates)
    }

    @Test
    fun failedSaveShowsError() = runBlocking {
        val model = FakeUsernameSetupModel(ProfileResult.Failure("Username already taken"))
        val view = FakeUsernameSetupView()
        val presenter = UsernameSetupPresenter(view, model)

        presenter.onSaveClicked("trailkeeper")

        assertEquals("Username already taken", view.lastError)
        assertEquals(null, view.dashboardUsername)
        assertEquals(listOf(true, false), view.savingStates)
    }
}

private class FakeUsernameSetupModel(
    private val result: ProfileResult
) : UsernameSetupModel {
    var savedUsername: String? = null

    override suspend fun saveUsername(username: String): ProfileResult {
        savedUsername = username
        return result
    }
}

private class FakeUsernameSetupView : UsernameSetupContract.View {
    val savingStates = mutableListOf<Boolean>()
    var lastError: String? = null
    var dashboardUsername: String? = null

    override fun showSaving(isSaving: Boolean) {
        savingStates.add(isSaving)
    }

    override fun showError(message: String) {
        lastError = message
    }

    override fun navigateToDashboard(username: String) {
        dashboardUsername = username
    }
}
