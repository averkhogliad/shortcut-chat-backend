package io.averkhoglyad.shortcut.message.api.handler

import io.averkhoglyad.shortcut.common.util.slf4j
import io.averkhoglyad.shortcut.common.web.exception.EndpointException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import kotlin.reflect.full.findAnnotation

@RestControllerAdvice
class EndpointExceptionAdvice {

    private val logger by slf4j()

    @ExceptionHandler
    fun handleException(e: AccessDeniedException): ProblemDetail {
        logger.info("Access denied: {}", e.message)
        return ProblemDetail
            .forStatusAndDetail(HttpStatus.FORBIDDEN, "access.denied")
    }

    @ExceptionHandler
    fun handleException(e: HttpMessageNotReadableException): ProblemDetail {
        logger.info("Invalid body: {}", e.message)
        return ProblemDetail
            .forStatusAndDetail(HttpStatus.BAD_REQUEST, "invalid.body")
    }

    @ExceptionHandler
    fun handleException(e: IllegalArgumentException): ProblemDetail {
        logger.info("Illegal argument {}", e.message)
        return ProblemDetail
            .forStatus(HttpStatus.BAD_REQUEST)
            .also { it.title = "illegal.argument" }
            .also { it.detail = e.message }
    }

    @ExceptionHandler
    fun handleException(e: MethodArgumentTypeMismatchException): ProblemDetail {
        logger.info("Invalid argument: {}", e.message)
        return ProblemDetail
            .forStatusAndDetail(HttpStatus.BAD_REQUEST, "invalid.argument")
    }

    @ExceptionHandler(EndpointException::class)
    fun handleEndpointException(exception: EndpointException): ProblemDetail {
        return ProblemDetail.forStatus(exception.status)
            .also { it.title = exception.message }
            .also { it.detail = exception.details }
    }

    @ExceptionHandler(Throwable::class)
    fun handleAnyUncaughtException(e: Throwable): ProblemDetail {
        logger.error("Generic exception caught:", e)
        return this::class.findAnnotation<ResponseStatus>()
            ?.let { e.asProblemDetail(it) }
            ?: ProblemDetail.forStatusAndDetail(INTERNAL_SERVER_ERROR, e.message ?: "internal.server.error")
    }
}

private fun Throwable.asProblemDetail(responseStatus: ResponseStatus): ProblemDetail {
    val status: HttpStatus = responseStatus.code
        .takeUnless { it == INTERNAL_SERVER_ERROR }
        ?: responseStatus.value

    val message = (message ?: responseStatus.reason)
        .takeUnless { it.isBlank() }
        ?: "internal.server.error"

    return ProblemDetail.forStatusAndDetail(status, message)
}
