package io.averkhoglyad.shortcut.users.api.controller

import io.averkhoglyad.shortcut.common.web.exception.endpointError
import io.averkhoglyad.shortcut.users.api.data.AuthCodeRequest
import io.averkhoglyad.shortcut.users.core.service.OtpRequestResult
import io.averkhoglyad.shortcut.users.core.service.OtpRequestService
import org.springframework.http.HttpStatus.TOO_MANY_REQUESTS
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Requests an email OTP code. The response never depends on whether the email belongs to a
 * user: the endpoint has no user service at all, so it neither looks a user up nor creates one.
 */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val service: OtpRequestService,
) {

    @PostMapping("/code")
    fun requestCode(@RequestBody request: AuthCodeRequest): ResponseEntity<Unit> {
        require(request.email.isNotBlank()) { "email must not be blank" }

        return when (service.requestCode(request.email)) {
            is OtpRequestResult.CodeSent -> ResponseEntity.accepted().build()
            OtpRequestResult.RateLimited -> throw endpointError(
                status = TOO_MANY_REQUESTS,
                message = "too.many.requests",
                details = "otp.request.rate.limited",
            )
        }
    }
}
