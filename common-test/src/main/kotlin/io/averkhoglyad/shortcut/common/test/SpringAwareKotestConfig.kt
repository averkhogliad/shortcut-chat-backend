package io.averkhoglyad.shortcut.common.test

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.extensions.spring.SpringExtension

class SpringAwareKotestConfig : AbstractProjectConfig() {

    override val extensions: List<Extension> = listOf(SpringExtension())

}
