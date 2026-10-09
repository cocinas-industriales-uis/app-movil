package co.edu.uis.cocinas.monitor.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import co.edu.uis.cocinas.monitor.domain.Role
import co.edu.uis.cocinas.monitor.domain.User
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda el token cifrado con una clave AES-GCM del Android Keystore (la clave nunca sale del hardware/TEE).
 * Nunca se guarda la contraseña. Los datos del usuario (nombre/rol) no son secretos.
 */
class SecureSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun saveSession(token: String, user: User) {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val blob = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(K_TOKEN, Base64.encodeToString(blob, Base64.NO_WRAP))
            .putString(K_ID, user.id).putString(K_NAME, user.name)
            .putString(K_USER, user.username).putString(K_ROLE, user.role.name)
            .apply()
    }

    fun token(): String? {
        val raw = prefs.getString(K_TOKEN, null) ?: return null
        return runCatching {
            val b = Base64.decode(raw, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
                .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b.copyOfRange(0, IV_LEN))) }
            String(cipher.doFinal(b, IV_LEN, b.size - IV_LEN), Charsets.UTF_8)
        }.getOrNull()
    }

    fun user(): User? {
        if (token() == null) return null
        val id = prefs.getString(K_ID, null) ?: return null
        return User(
            id = id,
            name = prefs.getString(K_NAME, "") ?: "",
            username = prefs.getString(K_USER, "") ?: "",
            role = runCatching { Role.valueOf(prefs.getString(K_ROLE, "OPERATOR")!!) }.getOrDefault(Role.OPERATOR),
        )
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val ALIAS = "cocinas_session_key"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
        const val K_TOKEN = "t"; const val K_ID = "id"; const val K_NAME = "n"; const val K_USER = "u"; const val K_ROLE = "r"
    }
}
