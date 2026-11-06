package io.averkhoglyad.shortcut.users.core.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.email
import io.kotest.property.arbitrary.next
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.script.RedisScript
import java.time.Duration

private const val OTP_KEY_PREFIX = "users:otp:"
private const val RATE_LIMIT_KEY_PREFIX = "users:otp:ratelimit:"
private val CODE_PATTERN = Regex("\\d{6}")

private fun stubScript(redis: StringRedisTemplate, vararg results: Long) {
    every { redis.execute(any<RedisScript<Long>>(), any(), *anyVararg()) } returnsMany results.toList()
}

private fun String.occurrencesOf(token: String): Int =
    Regex(Regex.escape(token)).findAll(this).count()

class OtpServiceTest : FreeSpec({

    val redis = mockk<StringRedisTemplate>()
    val values = mockk<ValueOperations<String, String>>()

    val service = OtpServiceImpl(redis)

    beforeEach {
        every { redis.opsForValue() } returns values
    }

    afterTest {
        clearAllMocks()
    }

    "requestCode" - {

        "returns rate limited result and does not store a code when the rate limit key exists" {
            // given
            val email = Arb.email().next()
            every { values.setIfAbsent(any(), any(), any<Duration>()) } returns false

            // when
            val result = service.requestCode(email)

            // then
            result shouldBe OtpRequestResult.RateLimited

            val key = slot<String>()
            val ttl = slot<Duration>()
            verify(exactly = 1) {
                values.setIfAbsent(capture(key), any(), capture(ttl))
            }
            key.captured shouldBe "$RATE_LIMIT_KEY_PREFIX$email"
            ttl.captured shouldBe Duration.ofSeconds(60)
            verify(exactly = 0) { redis.execute(any<RedisScript<Long>>(), any(), *anyVararg()) }
        }

        "stores a six digit code with three attempts and a 300 second ttl in one atomic script call" {
            // given
            val email = Arb.email().next()
            every { values.setIfAbsent(any(), any(), any<Duration>()) } returns true
            stubScript(redis, 1L)

            // when
            val sent = service.requestCode(email)
                .shouldBeInstanceOf<OtpRequestResult.CodeSent>()

            // then
            sent.code shouldMatch CODE_PATTERN

            val script = slot<RedisScript<Long>>()
            val keys = slot<List<String>>()
            verify(exactly = 1) {
                redis.execute(capture(script), capture(keys), *anyVararg())
            }
            keys.captured shouldBe listOf("$OTP_KEY_PREFIX$email")
            script.captured.scriptAsString shouldContain "'HSET'"
            script.captured.scriptAsString shouldContain "'EXPIRE'"

            verify(exactly = 1) {
                redis.execute(
                    any<RedisScript<Long>>(),
                    listOf("$OTP_KEY_PREFIX$email"),
                    sent.code,
                    "3",
                    "300",
                )
            }
            verify(exactly = 0) { redis.expire(any(), any<Duration>()) }

            val rateLimitKey = slot<String>()
            val rateLimitTtl = slot<Duration>()
            verify(exactly = 1) {
                values.setIfAbsent(capture(rateLimitKey), any(), capture(rateLimitTtl))
            }
            rateLimitKey.captured shouldBe "$RATE_LIMIT_KEY_PREFIX$email"
            rateLimitTtl.captured shouldBe Duration.ofSeconds(60)
        }

        "generates a six digit code for every request" {
            // given
            val email = Arb.email().next()
            every { values.setIfAbsent(any(), any(), any<Duration>()) } returns true
            stubScript(redis, 1L)

            // when
            val codes = (1..100).map {
                service.requestCode(email)
                    .shouldBeInstanceOf<OtpRequestResult.CodeSent>()
                    .code
            }

            // then
            codes.forEach { it shouldMatch CODE_PATTERN }
        }

        "propagates a redis failure from the rate limit claim instead of returning a result" {
            // given
            val email = Arb.email().next()
            every { values.setIfAbsent(any(), any(), any<Duration>()) } throws
                RedisConnectionFailureException("redis is down")

            // when
            shouldThrow<RedisConnectionFailureException> {
                service.requestCode(email)
            }

            // then
            verify(exactly = 0) { redis.execute(any<RedisScript<Long>>(), any(), *anyVararg()) }
        }

        "propagates a redis failure from the code storage script instead of returning a code" {
            // given
            val email = Arb.email().next()
            every { values.setIfAbsent(any(), any(), any<Duration>()) } returns true
            every { redis.execute(any<RedisScript<Long>>(), any(), *anyVararg()) } throws
                RedisConnectionFailureException("redis is down")

            // when
            shouldThrow<RedisConnectionFailureException> {
                service.requestCode(email)
            }

            // then
            verify(exactly = 1) { redis.execute(any<RedisScript<Long>>(), any(), *anyVararg()) }
        }

        "fails instead of returning a code when the storage script returns no status" {
            // given
            val email = Arb.email().next()
            every { values.setIfAbsent(any(), any(), any<Duration>()) } returns true
            every { redis.execute(any<RedisScript<Long>>(), any(), *anyVararg()) } returns null

            // when
            shouldThrow<IllegalStateException> {
                service.requestCode(email)
            }
        }
    }

    "verifyCode" - {

        "returns success when the script consumes the matching code" {
            // given
            val email = Arb.email().next()
            val code = "123456"
            stubScript(redis, 1L)

            // when
            val result = service.verifyCode(email, code)

            // then
            result shouldBe OtpVerificationResult.SUCCESS

            verify(exactly = 1) {
                redis.execute(any<RedisScript<Long>>(), listOf("$OTP_KEY_PREFIX$email"), code)
            }
        }

        "returns expired result when the script finds no stored code" {
            // given
            val email = Arb.email().next()
            stubScript(redis, 0L)

            // when
            val result = service.verifyCode(email, "123456")

            // then
            result shouldBe OtpVerificationResult.EXPIRED
        }

        "returns invalid result when the script decrements attempts without exhausting them" {
            // given
            val email = Arb.email().next()
            val code = "123456"
            stubScript(redis, 3L)

            // when
            val result = service.verifyCode(email, "654321")

            // then
            result shouldBe OtpVerificationResult.INVALID

            verify(exactly = 1) {
                redis.execute(any<RedisScript<Long>>(), listOf("$OTP_KEY_PREFIX$email"), "654321")
            }
        }

        "returns exhausted result when the script reaches zero attempts" {
            // given
            val email = Arb.email().next()
            stubScript(redis, 2L)

            // when
            val result = service.verifyCode(email, "654321")

            // then
            result shouldBe OtpVerificationResult.EXHAUSTED
        }

        "compares and consumes the code in a single atomic script call" {
            // given
            val email = Arb.email().next()
            val code = "123456"
            stubScript(redis, 1L)

            // when
            service.verifyCode(email, code) shouldBe OtpVerificationResult.SUCCESS

            // then
            val script = slot<RedisScript<Long>>()
            val keys = slot<List<String>>()
            verify(exactly = 1) {
                redis.execute(capture(script), capture(keys), *anyVararg())
            }
            keys.captured shouldBe listOf("$OTP_KEY_PREFIX$email")

            val scriptText = script.captured.scriptAsString
            scriptText shouldContain "'HGET'"
            scriptText shouldContain "ARGV[1]"
            scriptText shouldContain "'HINCRBY'"
            scriptText shouldContain "'DEL'"
            scriptText.occurrencesOf("'HINCRBY'") shouldBe 1
            scriptText.occurrencesOf("'DEL'") shouldBe 2

            verify(exactly = 0) { redis.delete(any<String>()) }
        }

        "does not allow a used code to be verified again" {
            // given
            val email = Arb.email().next()
            val code = "123456"
            stubScript(redis, 1L, 0L)

            // when
            val first = service.verifyCode(email, code)
            val second = service.verifyCode(email, code)

            // then
            first shouldBe OtpVerificationResult.SUCCESS
            second shouldBe OtpVerificationResult.EXPIRED

            verify(exactly = 2) {
                redis.execute(any<RedisScript<Long>>(), listOf("$OTP_KEY_PREFIX$email"), code)
            }
        }

        "does not allow an exhausted code to be verified again" {
            // given
            val email = Arb.email().next()
            val code = "123456"
            stubScript(redis, 2L, 0L)

            // when
            val first = service.verifyCode(email, code)
            val second = service.verifyCode(email, code)

            // then
            first shouldBe OtpVerificationResult.EXHAUSTED
            second shouldBe OtpVerificationResult.EXPIRED

            verify(exactly = 2) {
                redis.execute(any<RedisScript<Long>>(), listOf("$OTP_KEY_PREFIX$email"), code)
            }
        }

        "propagates a redis failure instead of returning a result" {
            // given
            val email = Arb.email().next()
            every { redis.execute(any<RedisScript<Long>>(), any(), *anyVararg()) } throws
                RedisConnectionFailureException("redis is down")

            // when
            shouldThrow<RedisConnectionFailureException> {
                service.verifyCode(email, "123456")
            }

            // then
            verify(exactly = 1) { redis.execute(any<RedisScript<Long>>(), any(), *anyVararg()) }
        }

        "fails instead of returning a result when the script returns an unknown status" {
            // given
            val email = Arb.email().next()
            stubScript(redis, 42L)

            // when
            shouldThrow<IllegalStateException> {
                service.verifyCode(email, "123456")
            }
        }
    }
})
