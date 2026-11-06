package io.averkhoglyad.shortcut.users.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE
import org.springframework.http.ProblemDetail
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import tools.jackson.databind.ObjectMapper

/**
 * Renders a denial raised by the security filter chain in the `ProblemDetail` format of the API
 * error contract. A chain-level denial never reaches `@RestControllerAdvice`, so the response body
 * is written here.
 */
class ProblemDetailAccessDeniedHandler(
    private val mapper: ObjectMapper,
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        response.status = FORBIDDEN.value()
        response.contentType = APPLICATION_PROBLEM_JSON_VALUE
        mapper.writeValue(
            response.outputStream,
            ProblemDetail.forStatusAndDetail(FORBIDDEN, "access.denied"),
        )
    }
}
