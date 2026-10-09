package co.edu.uis.cocinas.monitor.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Sonido + vibración de emergencia, en bucle hasta que se llame stop().
 * - Audio por USAGE_ALARM: sigue el volumen del canal de alarma (no el de medios) y lo sube al máximo
 *   mientras dura la emergencia; al terminar restaura el volumen anterior. Android no permite saltarse
 *   el modo "No molestar" total ni forzar más volumen que el máximo del canal.
 */
class AlarmPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var savedVolume: Int? = null
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)

    val isRinging: Boolean get() = player != null

    fun start() {
        if (player != null) return
        Log.w(TAG, "ALARMA CRÍTICA: iniciando sonido y vibración")
        runCatching {
            savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { Log.e(TAG, "No se pudo reproducir la alarma: ${it.javaClass.simpleName}") }.getOrNull()
        vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, 0))   // 0 = repetir desde el inicio
    }

    fun stop() {
        if (player == null && savedVolume == null) { vibrator.cancel(); return }
        Log.i(TAG, "Deteniendo sonido y vibración")
        player?.let { p -> runCatching { p.stop() }; p.release() }
        player = null
        vibrator.cancel()
        savedVolume?.let { v -> runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, v, 0) } }
        savedVolume = null
    }

    private companion object {
        const val TAG = "AlarmPlayer"
        val PATTERN = longArrayOf(0, 800, 300, 800, 300, 1500, 600)
    }
}
