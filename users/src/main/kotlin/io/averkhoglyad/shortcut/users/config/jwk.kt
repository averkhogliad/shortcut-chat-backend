package io.averkhoglyad.shortcut.users.config

import com.nimbusds.jose.jwk.RSAKey
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*

private const val RSA_KEY_SIZE = 2048

fun generateRsaKey(): RSAKey {
    val keyPair = KeyPairGenerator.getInstance("RSA")
        .apply { initialize(RSA_KEY_SIZE) }
        .generateKeyPair()
    return RSAKey.Builder(keyPair.public as RSAPublicKey)
        .privateKey(keyPair.private as RSAPrivateKey)
        .keyID(UUID.randomUUID().toString())
        .build()
}

fun saveRsaKey(key: RSAKey, file: Path) {
    file.toAbsolutePath().parent?.let { Files.createDirectories(it) }
    Files.writeString(file, key.toJSONString(), StandardCharsets.UTF_8)
}

fun loadRsaKey(file: Path): RSAKey =
    RSAKey.parse(Files.readString(file, StandardCharsets.UTF_8))

fun loadOrCreateRsaKey(file: Path): RSAKey {
    if (!Files.exists(file)) {
        return generateRsaKey()
            .also { saveRsaKey(it, file) }
    }
    val key = try {
        loadRsaKey(file)
    } catch (ex: Exception) {
        throw IllegalStateException("Failed to load JWK from '$file': file is unreadable or corrupt", ex)
    }
    check(key.isPrivate) {
        "JWK file '$file' does not contain a private key; refusing to start with a key that cannot sign tokens"
    }
    return key
}
