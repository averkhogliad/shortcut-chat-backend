package io.averkhoglyad.shortcut.users.it

import io.kotest.property.Arb
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.uuid

fun Arb.Companion.otpEmails(prefix: String = "otp-flow-"): Arb<String> =
    Arb.uuid().map { id -> "$prefix$id@example.com" }
