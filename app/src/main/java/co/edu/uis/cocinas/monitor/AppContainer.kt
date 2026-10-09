package co.edu.uis.cocinas.monitor

import android.content.Context
import co.edu.uis.cocinas.monitor.data.SecureSessionStore
import co.edu.uis.cocinas.monitor.data.mock.MockAuthRepository
import co.edu.uis.cocinas.monitor.data.mock.MockMonitoringRepository
import co.edu.uis.cocinas.monitor.data.remote.ApiService
import co.edu.uis.cocinas.monitor.data.remote.RemoteAuthRepository
import co.edu.uis.cocinas.monitor.data.remote.RemoteMonitoringRepository
import co.edu.uis.cocinas.monitor.domain.AuthRepository
import co.edu.uis.cocinas.monitor.domain.MonitoringRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Inyección de dependencias manual (suficiente para este tamaño; evita sumar Hilt/Koin). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val sessionStore = SecureSessionStore(context)

    val authRepository: AuthRepository
    val monitoring: MonitoringRepository
    /** No nulo solo en modo simulado (debug): permite el panel de pruebas del dashboard. */
    val mock: MockMonitoringRepository?

    init {
        if (BuildConfig.USE_MOCK) {
            val auth = MockAuthRepository(sessionStore)
            val m = MockMonitoringRepository(appScope) { auth.user.value }
            authRepository = auth
            monitoring = m
            mock = m
        } else {
            val json = Json { ignoreUnknownKeys = true }
            val authHeader = Interceptor { chain ->
                val token = sessionStore.token()
                val req = if (token != null) chain.request().newBuilder().header("Authorization", "Bearer $token").build()
                else chain.request()
                chain.proceed(req)
            }
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                redactHeader("Authorization")
            }
            val http = OkHttpClient.Builder()
                .addInterceptor(authHeader)
                .addInterceptor(logging)
                .connectTimeout(10, TimeUnit.SECONDS)
                .build()
            val api = Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(http)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(ApiService::class.java)
            authRepository = RemoteAuthRepository(api, sessionStore)
            monitoring = RemoteMonitoringRepository(
                api = api,
                sseClient = http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build(),
                baseUrl = BuildConfig.API_BASE_URL,
                scope = appScope,
                json = json,
            )
            mock = null
        }
    }
}
