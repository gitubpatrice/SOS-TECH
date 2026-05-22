package com.filestech.sos.core.crypto

/**
 * Best-effort scrubbing of sensitive byte buffers. Note: JIT and immutable copies (FFI, JNI buffers)
 * can defeat this. Use only for memory you allocated yourself.
 */
fun ByteArray.wipe() {
    try {
        java.util.Arrays.fill(this, 0)
    } catch (_: UnsupportedOperationException) {
        // Unmodifiable buffer (rare on JVM byte[], common on Java ByteBuffer-backed arrays).
    }
}

fun CharArray.wipe() {
    try {
        java.util.Arrays.fill(this, ' ')
    } catch (_: UnsupportedOperationException) {
        // ignore
    }
}

fun CharArray.toUtf8Bytes(): ByteArray {
    val cb = java.nio.CharBuffer.wrap(this)
    val bb = Charsets.UTF_8.encode(cb)
    val out = ByteArray(bb.remaining())
    bb.get(out)
    val backing = bb.array()
    if (backing.isNotEmpty()) java.util.Arrays.fill(backing, 0)
    return out
}
