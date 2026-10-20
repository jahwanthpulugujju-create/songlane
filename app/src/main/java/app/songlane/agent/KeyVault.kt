package app.songlane.agent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyVault(
    context: Context,
    name: String = "agent-vault",
    private val alias: String = "songlane.agent.aes.v1",
) {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey)
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                                alias,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build()
                    )
                }
                .generateKey()
    }

    fun save(value: String, provider: AiProvider = AiProvider.ANTHROPIC) =
        synchronized(lock) {
            require(value.length in 10..512 && value.all { it.code in 33..126 }) {
                "Check the API key format"
            }
            val cipher =
                Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            cipher.updateAAD(provider.name.toByteArray(Charsets.UTF_8))
            val payload = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            check(
                preferences
                    .edit()
                    .putString(
                        "${provider.name}.cipher",
                        Base64.encodeToString(payload, Base64.NO_WRAP),
                    )
                    .putString(
                        "${provider.name}.iv",
                        Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
                    )
                    .apply {
                        if (provider == AiProvider.ANTHROPIC) {
                            remove("cipher")
                            remove("iv")
                        }
                    }
                    .commit()
            )
        }

    fun read(provider: AiProvider = AiProvider.ANTHROPIC): String? =
        synchronized(lock) {
            val legacy =
                provider == AiProvider.ANTHROPIC && !preferences.contains("${provider.name}.cipher")
            val stored =
                preferences.getString(if (legacy) "cipher" else "${provider.name}.cipher", null)
                    ?: return null
            val iv =
                preferences.getString(if (legacy) "iv" else "${provider.name}.iv", null)
                    ?: error("Stored key IV missing")
            val cipher =
                Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(
                        Cipher.DECRYPT_MODE,
                        key(),
                        GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
                    )
                }
            if (!legacy) cipher.updateAAD(provider.name.toByteArray(Charsets.UTF_8))
            val result =
                String(cipher.doFinal(Base64.decode(stored, Base64.NO_WRAP)), Charsets.UTF_8)
            if (legacy) save(result, provider)
            result
        }

    fun exists(provider: AiProvider = AiProvider.ANTHROPIC) =
        preferences.contains("${provider.name}.cipher") ||
            (provider == AiProvider.ANTHROPIC && preferences.contains("cipher"))

    fun delete(provider: AiProvider = AiProvider.ANTHROPIC) =
        synchronized(lock) {
            check(
                preferences
                    .edit()
                    .remove("${provider.name}.cipher")
                    .remove("${provider.name}.iv")
                    .apply {
                        if (provider == AiProvider.ANTHROPIC) {
                            remove("cipher")
                            remove("iv")
                        }
                    }
                    .commit()
            )
            if (AiProvider.entries.none { exists(it) }) deleteEncryptionKey()
        }

    fun deleteAll() =
        synchronized(lock) {
            check(preferences.edit().clear().commit())
            deleteEncryptionKey()
        }

    private fun deleteEncryptionKey() {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            if (containsAlias(alias)) deleteEntry(alias)
        }
    }

    companion object {
        private val lock = Any()
    }
}
