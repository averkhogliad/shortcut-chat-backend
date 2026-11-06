package io.averkhoglyad.shortcut.users

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class UsersAppTests(val ctx: ApplicationContext) : FreeSpec({

    "context loads" {
        ctx.getBean(UsersApp::class.java)
            .shouldNotBeNull()
    }
})
