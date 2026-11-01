package io.averkhoglyad.shortcut.common.web.handler

import io.averkhoglyad.shortcut.common.data.EntityResult
import io.averkhoglyad.shortcut.common.web.exception.EndpointException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.springframework.http.HttpStatus

class EntityResultHandlerTest : FreeSpec({

    "unwrap" - {
        "returns entity for successful result" {
            // given
            val entity = Any()

            // when
            val result = EntityResult.Success(entity).unwrap()

            // then
            result shouldBeSameInstanceAs entity
        }

        "throws not found error for NotFound result" {
            // when
            val error = shouldThrow<EndpointException> {
                EntityResult.NotFound.unwrap()
            }

            // then
            error.status shouldBe HttpStatus.NOT_FOUND
        }

        "throws bad request error for Invalid result" {
            // when
            val error = shouldThrow<EndpointException> {
                EntityResult.Invalid<String>("invalid").unwrap()
            }

            // then
            error.status shouldBe HttpStatus.BAD_REQUEST
        }

        "throws conflict error for Conflict result" {
            // when
            val error = shouldThrow<EndpointException> {
                EntityResult.Conflict<String>("conflict").unwrap()
            }

            // then
            error.status shouldBe HttpStatus.CONFLICT
        }

        "throws internal error for Failed result" {
            // when
            val error = shouldThrow<EndpointException> {
                EntityResult.Failed<String>(Exception("failed")).unwrap()
            }

            // then
            error.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
        }
    }
})
