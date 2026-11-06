package io.averkhoglyad.shortcut.users.core.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration

private const val OTP_KEY_PREFIX = "users:otp:"
private const val RATE_LIMIT_KEY_PREFIX = "users:otp:ratelimit:"
private const val INITIAL_ATTEMPTS = 3
private const val CODE_LENGTH = 6
private const val CODE_UPPER_BOUND = 1_000_000
private const val RATE_LIMIT_VALUE = "1"
private const val CODE_TTL_SECONDS = 300
private const val RATE_LIMIT_TTL_SECONDS = 60

// Status values returned by the scripts; the values are part of the script contract.
private const val SCRIPT_RESULT_STORED = 1L
private const val SCRIPT_RESULT_EXPIRED = 0L
private const val SCRIPT_RESULT_SUCCESS = 1L
private const val SCRIPT_RESULT_EXHAUSTED = 2L
private const val SCRIPT_RESULT_INVALID = 3L

private val CODE_TTL: Duration = Duration.ofSeconds(CODE_TTL_SECONDS.toLong())
private val RATE_LIMIT_TTL: Duration = Duration.ofSeconds(RATE_LIMIT_TTL_SECONDS.toLong())

// Writes the code and its TTL in one atomic step: a stored code never outlives its deadline
// and a failed write leaves no half-stored state.
private val STORE_CODE_SCRIPT: RedisScript<Long> = RedisScript.of(
    """
    redis.call('HSET', KEYS[1], 'code', ARGV[1], 'attempts', ARGV[2])
    redis.call('EXPIRE', KEYS[1], ARGV[3])
    return 1
    """.trimIndent(),
    Long::class.javaObjectType,
)

// Compares and consumes the code in one atomic step: concurrent verifications cannot both
// read the same code before it is deleted, and each mismatch decrements the counter once.
// Returns 0 = expired/not found, 1 = success, 2 = exhausted, 3 = invalid.
private val CONSUME_CODE_SCRIPT: RedisScript<Long> = RedisScript.of(
    """
    local storedCode = redis.call('HGET', KEYS[1], 'code')
    if not storedCode then
        return 0
    end
    if storedCode == ARGV[1] then
        redis.call('DEL', KEYS[1])
        return 1
    end
    local remainingAttempts = redis.call('HINCRBY', KEYS[1], 'attempts', -1)
    if remainingAttempts <= 0 then
        redis.call('DEL', KEYS[1])
        return 2
    end
    return 3
    """.trimIndent(),
    Long::class.javaObjectType,
)

sealed interface OtpRequestResult {

    data class CodeSent(val code: String) : OtpRequestResult

    data object RateLimited : OtpRequestResult

}

enum class OtpVerificationResult {
    SUCCESS,
    INVALID,
    EXHAUSTED,
    EXPIRED,
}

interface OtpService {

    fun requestCode(email: String): OtpRequestResult

    fun verifyCode(email: String, code: String): OtpVerificationResult

}

@Service
class OtpServiceImpl(
    private val redis: StringRedisTemplate,
) : OtpService {

    private val random = SecureRandom()

    override fun requestCode(email: String): OtpRequestResult {
        val rateLimited = redis.opsForValue()
            .setIfAbsent(rateLimitKey(email), RATE_LIMIT_VALUE, RATE_LIMIT_TTL) != true

        if (rateLimited) {
            return OtpRequestResult.RateLimited
        }

        val code = generateCode()
        val stored = redis.execute(
            STORE_CODE_SCRIPT,
            listOf(otpKey(email)),
            code,
            INITIAL_ATTEMPTS.toString(),
            CODE_TTL_SECONDS.toString(),
        )
        check(stored == SCRIPT_RESULT_STORED) { "Unexpected OTP storage script result" }

        return OtpRequestResult.CodeSent(code)
    }

    override fun verifyCode(email: String, code: String): OtpVerificationResult =
        when (redis.execute(CONSUME_CODE_SCRIPT, listOf(otpKey(email)), code)) {
            SCRIPT_RESULT_SUCCESS -> OtpVerificationResult.SUCCESS
            SCRIPT_RESULT_EXHAUSTED -> OtpVerificationResult.EXHAUSTED
            SCRIPT_RESULT_INVALID -> OtpVerificationResult.INVALID
            SCRIPT_RESULT_EXPIRED -> OtpVerificationResult.EXPIRED
            else -> error("Unexpected OTP verification script result")
        }

    private fun generateCode(): String = random.nextInt(CODE_UPPER_BOUND)
        .toString()
        .padStart(CODE_LENGTH, '0')

    private fun otpKey(email: String): String = "$OTP_KEY_PREFIX$email"

    private fun rateLimitKey(email: String): String = "$RATE_LIMIT_KEY_PREFIX$email"

}
