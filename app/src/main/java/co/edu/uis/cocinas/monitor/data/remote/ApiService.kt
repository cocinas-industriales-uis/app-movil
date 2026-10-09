package co.edu.uis.cocinas.monitor.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Endpoints propuestos (ver README → "Contrato con el backend"). */
interface ApiService {
    @POST("auth/login/") suspend fun login(@Body body: LoginRequest): LoginResponse
    @GET("lecturas/ultimas/") suspend fun latestReadings(): List<ReadingDto>
    @GET("alarmas/") suspend fun alarms(): List<AlarmDto>
    @POST("alarmas/{id}/reconocer/") suspend fun acknowledge(@Path("id") id: String, @Body body: AckRequest): AlarmDto
}
