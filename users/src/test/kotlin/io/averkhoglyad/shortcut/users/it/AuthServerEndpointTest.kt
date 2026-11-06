package io.averkhoglyad.shortcut.users.it

import io.averkhoglyad.shortcut.users.TestcontainersConfiguration
import io.averkhoglyad.shortcut.users.security.EMAIL_OTP_GRANT_TYPE
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

private const val ISSUER = "http://localhost:8080"

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class AuthServerEndpointTest(
    private val mvc: MockMvc,
    private val mapper: ObjectMapper,
) : FreeSpec({

    "GET /.well-known/openid-configuration" - {
        "returns discovery document with required endpoints and the email-otp grant" {
            // when
            val body = readJson(mvc, mapper, "/.well-known/openid-configuration")

            // then
            body["issuer"].stringValue() shouldBe ISSUER
            body["authorization_endpoint"].stringValue() shouldBe "$ISSUER/oauth2/authorize"
            body["token_endpoint"].stringValue() shouldBe "$ISSUER/oauth2/token"
            body["jwks_uri"].stringValue() shouldBe "$ISSUER/oauth2/jwks"
            body["userinfo_endpoint"].stringValue() shouldBe "$ISSUER/userinfo"
            body["grant_types_supported"].stringValues() shouldContain EMAIL_OTP_GRANT_TYPE.value
            body.has("introspection_endpoint") shouldBe false
            body.has("introspection_endpoint_auth_methods_supported") shouldBe false
            body.has("end_session_endpoint") shouldBe false
        }
    }

    "GET /.well-known/oauth-authorization-server" - {
        "does not advertise the token introspection endpoint" {
            // when
            val body = readJson(mvc, mapper, "/.well-known/oauth-authorization-server")

            // then
            body["issuer"].stringValue() shouldBe ISSUER
            body.has("introspection_endpoint") shouldBe false
            body.has("introspection_endpoint_auth_methods_supported") shouldBe false
        }
    }

    "POST /oauth2/introspect" - {
        "is not served" {
            // when/then
            mvc.perform(post("/oauth2/introspect").param("token", "any-token"))
                .andExpect(status().isNotFound())
        }
    }

    "POST /oauth2/token" - {
        "stays served and rejects a request without client authentication" {
            // when/then
            mvc.perform(post("/oauth2/token").param("grant_type", "authorization_code"))
                .andExpect(status().isBadRequest())
        }
    }

    "GET /oauth2/jwks" - {
        "returns the public RSA key" {
            // when
            val body = readJson(mvc, mapper, "/oauth2/jwks")

            // then
            val key = body["keys"][0]
            key["kty"].stringValue() shouldBe "RSA"
            key["kid"].stringValue().shouldNotBeBlank()
            key["n"].stringValue().shouldNotBeBlank()
            key["e"].stringValue().shouldNotBeBlank()
            key.has("d") shouldBe false
        }
    }
})

private fun readJson(mvc: MockMvc, mapper: ObjectMapper, path: String): JsonNode {
    val content = mvc.perform(get(path))
        .andExpect(status().isOk)
        .andReturn()
        .response
        .contentAsString
    return mapper.readTree(content)
}

private fun JsonNode.stringValues(): List<String> =
    (0 until size()).map { this[it].stringValue() }
