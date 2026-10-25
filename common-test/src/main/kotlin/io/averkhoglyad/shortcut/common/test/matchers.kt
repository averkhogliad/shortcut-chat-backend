package io.averkhoglyad.shortcut.common.test

import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.equals.beEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.should
import java.time.Instant

fun betweenInclusive(fromInstant: Instant, toInstant: Instant): Matcher<Instant> =
    object : Matcher<Instant> {
        override fun test(value: Instant): MatcherResult {
            return MatcherResult(
                value in fromInstant..toInstant,
                { "$value should be after or equal to $fromInstant and before or equal to $toInstant" },
                { "$value should be after or equal to $fromInstant and before or equal to $toInstant" }
            )
        }
    }

infix fun <A> A.shouldBeEqual(expected: A?): A? {
    if (expected == null) this.shouldBeNull()
    else this should beEqual(expected)
    return this
}
