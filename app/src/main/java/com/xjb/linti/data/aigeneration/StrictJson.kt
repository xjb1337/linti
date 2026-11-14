package com.xjb.linti.data.aigeneration

/** org.json 在 Android 上默认接受注释、单引号等非 JSON，解析前先限定为标准 JSON。 */
internal object StrictJson {
    fun check(text: String) {
        var position = 0
        fun whitespace() { while (position < text.length && text[position] in " \t\r\n") position++ }
        fun string() {
            require(position < text.length && text[position++] == '"')
            while (position < text.length) {
                val c = text[position++]
                if (c == '"') return
                require(c >= ' ')
                if (c == '\\') {
                    require(position < text.length)
                    when (text[position++]) {
                        '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                        'u' -> repeat(4) {
                            require(position < text.length && text[position] in "0123456789abcdefABCDEF")
                            position++
                        }
                        else -> throw IllegalArgumentException("invalid JSON")
                    }
                }
            }
            throw IllegalArgumentException("invalid JSON")
        }
        fun value(depth: Int) {
            require(depth <= 100)
            whitespace()
            require(position < text.length)
            when (text[position]) {
                '"' -> string()
                '{' -> {
                    position++; whitespace()
                    if (position < text.length && text[position] == '}') { position++; return }
                    val keys = mutableSetOf<String>()
                    while (true) {
                        whitespace(); val start = position; string()
                        // 重复字段会让模型输出的含义不明确。
                        require(keys.add(text.substring(start, position)))
                        whitespace(); require(position < text.length && text[position++] == ':')
                        value(depth + 1); whitespace(); require(position < text.length)
                        when (text[position++]) { '}' -> return; ',' -> Unit; else -> throw IllegalArgumentException("invalid JSON") }
                    }
                }
                '[' -> {
                    position++; whitespace()
                    if (position < text.length && text[position] == ']') { position++; return }
                    while (true) {
                        value(depth + 1); whitespace(); require(position < text.length)
                        when (text[position++]) { ']' -> return; ',' -> Unit; else -> throw IllegalArgumentException("invalid JSON") }
                    }
                }
                't', 'f', 'n' -> {
                    val literal = when (text[position]) { 't' -> "true"; 'f' -> "false"; else -> "null" }
                    require(text.startsWith(literal, position)); position += literal.length
                }
                else -> {
                    val match = NUMBER.find(text, position)
                    require(match != null && match.range.first == position)
                    position = match.range.last + 1
                }
            }
        }
        value(0); whitespace(); require(position == text.length)
    }
    private val NUMBER = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
}
