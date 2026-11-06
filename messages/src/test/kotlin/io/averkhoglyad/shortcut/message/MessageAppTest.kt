package io.averkhoglyad.shortcut.message

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestcontainersConfiguration::class, TestSecurityConfiguration::class)
class MessageAppTest(val ctx: ApplicationContext) : FreeSpec({

    "context loads" {
        ctx.getBean(MessageApp::class.java)
            .shouldNotBeNull()
    }
})