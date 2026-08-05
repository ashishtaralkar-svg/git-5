package com.hdfc.docupload.data.repository

import com.hdfc.docupload.data.local.prefs.SessionManager
import com.hdfc.docupload.domain.model.Resource
import com.hdfc.docupload.domain.repository.AuthRepository
import com.hdfc.docupload.util.Constants
import kotlinx.coroutines.delay
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Demo implementation: validates the hard-coded credentials client-side and
 * mints a local session token. Swap the body of [login] for an [AuthApi] call
 * when the backend is available — the rest of the app is agnostic.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val sessionManager: SessionManager
) : AuthRepository {

    override suspend fun login(username: String, password: String): Resource<Unit> {
        delay(600) // simulate network latency for a realistic UX
        return if (username == Constants.DEMO_USERNAME && password == Constants.DEMO_PASSWORD) {
            sessionManager.authToken = UUID.randomUUID().toString()
            sessionManager.username = username
            Resource.Success(Unit)
        } else {
            Resource.Error("Invalid Username or Password.")
        }
    }

    override fun logout() = sessionManager.clear()

    override fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()
}
