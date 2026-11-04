package io.averkhoglyad.shortcut.common.persistence.reference

import org.springframework.data.jdbc.core.mapping.AggregateReference

object EmptyReference : AggregateReference<Nothing, Nothing> {

    override fun getId(): Nothing = throw UnsupportedOperationException("Empty reference has no id")

}

fun <E : Any, I : Any> emptyReference(): AggregateReference<out E, out I> {
    return EmptyReference
}

val AggregateReference<*, *>.isEmpty
    get() = this === EmptyReference
