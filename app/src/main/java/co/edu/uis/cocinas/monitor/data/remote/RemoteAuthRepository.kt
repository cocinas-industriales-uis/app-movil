package co.edu.uis.cocinas.monitor.data.remote

import android.util.Log
import co.edu.uis.cocinas.monitor.data.SecureSessionStore
import co.edu.uis.cocinas.monitor.domain.AuthRepository
import co.edu.uis.cocinas.monitor.domain.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RemoteAuthRepository(
    private val api: ApiService,
    private val store: SecureSessionStore,
) : AuthRepository {
    private val _user = MutableStateFlow(store.user())
    override val user: StateFlow<User?> = _user.asStateFlow()

    override suspend fun login(username: String, password: String): Result<User> = try {
        val r = api.login(LoginRequest(username, password))
        val u = r.user.toDomain()
        store.saveSession(r.access, u)
        _user.value = u
        Log.i(TAG, "Sesión iniciada (rol=${u.role})")
        Result.success(u)
    } catch (e: Exception) {
        Log.w(TAG, "Fallo de autenticación: ${e.javaClass.simpleName}")   // nunca se registran credenciales
        Result.failure(e)
    }

    override suspend fun logout() {
        store.clear()
        _user.value = null
        Log.i(TAG, "Sesión cerrada")
    }

    private companion object { const val TAG = "Auth" }
}
