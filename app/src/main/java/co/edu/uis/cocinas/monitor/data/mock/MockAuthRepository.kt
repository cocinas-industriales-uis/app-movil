package co.edu.uis.cocinas.monitor.data.mock

import co.edu.uis.cocinas.monitor.BuildConfig
import co.edu.uis.cocinas.monitor.data.SecureSessionStore
import co.edu.uis.cocinas.monitor.domain.AuthRepository
import co.edu.uis.cocinas.monitor.domain.Role
import co.edu.uis.cocinas.monitor.domain.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** SOLO DEBUG. Usuarios "admin" y "operador"; cualquier contraseña de 4+ caracteres. */
class MockAuthRepository(private val store: SecureSessionStore) : AuthRepository {
    init { require(BuildConfig.DEBUG) { "El login simulado solo existe en debug" } }

    private val _user = MutableStateFlow(store.user())
    override val user: StateFlow<User?> = _user.asStateFlow()

    override suspend fun login(username: String, password: String): Result<User> {
        delay(600)
        val u = when (username.lowercase()) {
            "admin" -> User("1", "Administrador Demo", "admin", Role.ADMIN)
            "operador" -> User("2", "Operador Demo", "operador", Role.OPERATOR)
            else -> null
        }
        if (u == null || password.length < 4) return Result.failure(IllegalArgumentException("credenciales"))
        store.saveSession("mock-token", u)
        _user.value = u
        return Result.success(u)
    }

    override suspend fun logout() { store.clear(); _user.value = null }
}
