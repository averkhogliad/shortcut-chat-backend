package io.averkhoglyad.shortcut.users.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class JwkTest : FreeSpec({

    lateinit var dir: Path

    beforeSpec {
        dir = createTempDirectory("jwk-test")
    }

    afterSpec {
        dir.toFile().deleteRecursively()
    }

    "saveRsaKey" - {
        "writes a key that loadRsaKey reads back unchanged" {
            // given
            val key = generateRsaKey()
            val file = dir.resolve("saved.json")

            // when
            saveRsaKey(key, file)
            val loaded = loadRsaKey(file)

            // then
            loaded.keyID shouldBe key.keyID
            loaded.modulus shouldBe key.modulus
            loaded.privateExponent shouldBe key.privateExponent
        }
    }

    "loadOrCreateRsaKey" - {
        "creates the key file on first load and reuses the same key afterwards" {
            // given
            val file = dir.resolve("nested/jwk.json")

            // when
            val created = loadOrCreateRsaKey(file)
            val loaded = loadOrCreateRsaKey(file)

            // then
            Files.exists(file) shouldBe true
            loaded.keyID shouldBe created.keyID
            loaded.modulus shouldBe created.modulus
        }

        "fails with a clear error when the file is corrupt and does not regenerate it" {
            // given
            val file = dir.resolve("corrupt.json")
            Files.writeString(file, "{not-a-jwk", StandardCharsets.UTF_8)

            // when
            val error = shouldThrow<IllegalStateException> {
                loadOrCreateRsaKey(file)
            }

            // then
            error.message.shouldContain(file.toString())
            Files.readString(file, StandardCharsets.UTF_8) shouldBe "{not-a-jwk"
        }

        "fails with a clear error when the file holds a public key only and does not regenerate it" {
            // given
            val file = dir.resolve("public-only.json")
            saveRsaKey(generateRsaKey().toPublicJWK(), file)
            val written = Files.readString(file, StandardCharsets.UTF_8)

            // when
            val error = shouldThrow<IllegalStateException> {
                loadOrCreateRsaKey(file)
            }

            // then
            error.message.shouldContain(file.toString())
            error.message.shouldContain("private key")
            Files.readString(file, StandardCharsets.UTF_8) shouldBe written
        }
    }
})
