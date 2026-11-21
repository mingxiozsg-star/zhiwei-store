package com.zhiwei.store.core

import java.io.IOException
import java.io.Reader

/**
 * 极简流式 JSON 读取器。
 * F-Droid 的 index-v1.json 有几十 MB，整串 JSONObject 解析会直接 OOM，
 * 这里按 token 逐层读取，边读边回调，内存占用只有当前条目。
 */
class JsonStreamReader(private val reader: Reader) {

    private var c: Int = reader.read()

    private fun advance() {
        c = reader.read()
    }

    fun skipWhitespace() {
        while (c == 0x20 || c == 0x0A || c == 0x0D || c == 0x09) advance()
    }

    fun peek(): Int {
        skipWhitespace()
        return c
    }

    fun expect(ch: Char) {
        skipWhitespace()
        if (c != ch.code) {
            throw IOException("JSON 语法错误：期望 '$ch'，实际 ${if (c < 0) "EOF" else c.toChar()}")
        }
        advance()
    }

    fun beginObject() = expect('{')
    fun endObject() = expect('}')
    fun beginArray() = expect('[')
    fun endArray() = expect(']')

    fun optionalComma() {
        skipWhitespace()
        if (c == ','.code) advance()
    }

    fun hasNextEntry(): Boolean {
        skipWhitespace()
        return c != '}'.code && c >= 0
    }

    fun hasNextArrayItem(): Boolean {
        skipWhitespace()
        return c != ']'.code && c >= 0
    }

    fun readName(): String {
        val name = readString()
        expect(':')
        return name
    }

    fun readString(): String {
        skipWhitespace()
        if (c != '"'.code) throw IOException("JSON 语法错误：期望字符串")
        advance()
        val sb = StringBuilder(64)
        while (true) {
            when {
                c < 0 -> throw IOException("JSON 字符串未闭合")
                c == '"'.code -> {
                    advance()
                    return sb.toString()
                }
                c == '\\'.code -> {
                    advance()
                    when {
                        c == '"'.code -> sb.append('"')
                        c == '\\'.code -> sb.append('\\')
                        c == '/'.code -> sb.append('/')
                        c == 'b'.code -> sb.append('\b')
                        c == 'f'.code -> sb.append('\u000C')
                        c == 'n'.code -> sb.append('\n')
                        c == 'r'.code -> sb.append('\r')
                        c == 't'.code -> sb.append('\t')
                        c == 'u'.code -> {
                            advance()
                            var v = 0
                            var k = 0
                            while (k < 4) {
                                if (c < 0) throw IOException("JSON unicode 转义不完整")
                                val d = Character.digit(c.toChar(), 16)
                                if (d < 0) throw IOException("JSON unicode 转义非法")
                                v = v * 16 + d
                                advance()
                                k++
                            }
                            sb.append(v.toChar())
                            continue
                        }
                        else -> sb.append(c.toChar())
                    }
                    advance()
                }
                else -> {
                    sb.append(c.toChar())
                    advance()
                }
            }
        }
    }

    /** 读取数字 / true / false / null 等裸值 */
    fun readLiteral(): String {
        skipWhitespace()
        val sb = StringBuilder(16)
        while (c >= 0 && c != ','.code && c != '}'.code && c != ']'.code &&
            c != 0x20 && c != 0x0A && c != 0x0D && c != 0x09
        ) {
            sb.append(c.toChar())
            advance()
        }
        return sb.toString()
    }

    fun readValueOrNull(): String? {
        skipWhitespace()
        if (c < 0) return null
        return when (c) {
            '"'.code -> readString()
            'n'.code -> {
                readLiteral()
                null
            }
            't'.code -> {
                readLiteral()
                "true"
            }
            'f'.code -> {
                readLiteral()
                "false"
            }
            else -> readLiteral()
        }
    }

    fun skipValue() {
        when (peek()) {
            '{'.code -> {
                beginObject()
                while (true) {
                    optionalComma()
                    if (!hasNextEntry()) break
                    readName()
                    skipValue()
                }
                endObject()
            }
            '['.code -> {
                beginArray()
                while (true) {
                    optionalComma()
                    if (!hasNextArrayItem()) break
                    skipValue()
                }
                endArray()
            }
            else -> readValueOrNull()
        }
    }
}