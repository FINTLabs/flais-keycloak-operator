package no.novari.operator.utils

import org.slf4j.MDC

fun <T> withLoggingContext(
    vararg mdcs: Pair<String, String>,
    block: () -> T,
): T = withLoggingContext(mdcs.toMap(), block)

fun <T> withLoggingContext(
    mdcs: Map<String, String>,
    block: () -> T,
): T {
    val closeable =
        mdcs.map {
            putMdcCloseable(it.key, it.value)
        }
    try {
        return block()
    } finally {
        closeable.asReversed().forEach { it() }
    }
}

private fun putMdcCloseable(
    key: String,
    value: String,
): () -> Unit {
    val previous = MDC.get(key)
    MDC.put(key, value)
    return {
        if (previous == null) MDC.remove(key) else MDC.put(key, previous)
    }
}
