package com.bitwise.plugin

import com.google.common.primitives.UnsignedLongs.parseUnsignedLong
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPrefixExpression
import java.lang.Integer.toBinaryString
import java.lang.Long.toBinaryString
import com.intellij.openapi.diagnostic.Logger

/**
 * Evaluates Java/Kotlin bitwise constant expressions without depending on frontend-specific
 * Kotlin analysis APIs, so the evaluator works for both K1 and K2-backed PSI.
 */
object BitwiseExpressionEvaluator {
    private val LOG = Logger.getInstance(BitwiseExpressionEvaluator::class.java)

    @JvmStatic
    fun evaluate(expr: String?): EvalResult? {
        if (expr.isNullOrBlank()) {
            LOG.debug("Skipping empty expression evaluation")
            return null
        }
        return try {
            val normalized = expr.trim()
                .replace("\\s+".toRegex(), " ")
            Parser(normalized).parse()
        } catch (e: Exception) {
            LOG.warn("Failed to evaluate expression: $expr", e)
            null
        }
    }

    fun evaluateKotlin(expr: KtExpression): Any? {
        val result = evaluateKotlinExpression(expr, mutableSetOf()) ?: return null
        return if (result.isLong) result.value else result.value.toInt()
    }

    private fun evaluateKotlinExpression(expr: KtExpression, visited: MutableSet<KtProperty>): EvalResult? {
        return when (expr) {
            is KtParenthesizedExpression -> expr.expression?.let { evaluateKotlinExpression(it, visited) }
            is KtConstantExpression -> parseKotlinConstant(expr.text)
            is KtNameReferenceExpression -> resolveKotlinReference(expr, visited)
            is KtPrefixExpression -> evaluateKotlinPrefix(expr, visited)
            is KtBinaryExpression -> evaluateKotlinBinary(expr, visited)
            is KtCallExpression -> evaluateKotlinCall(expr, visited)
            is KtDotQualifiedExpression -> evaluateKotlinDotQualified(expr, visited)
            else -> {
                LOG.debug("Unsupported Kotlin expression for bitwise evaluation: ${expr.text}")
                null
            }
        }
    }

    private fun evaluateKotlinCall(expr: KtCallExpression, visited: MutableSet<KtProperty>): EvalResult? {
        // Handle constructor-like calls with a single argument (e.g., SourceMask(1))
        val args = expr.valueArguments
        if (args.size == 1) {
            return args[0].getArgumentExpression()?.let { evaluateKotlinExpression(it, visited) }
        }
        return null
    }

    private fun evaluateKotlinPrefix(expr: KtPrefixExpression, visited: MutableSet<KtProperty>): EvalResult? {
        val base = expr.baseExpression?.let { evaluateKotlinExpression(it, visited) } ?: return null
        return when (expr.operationToken) {
            KtTokens.MINUS -> EvalResult(-base.value, base.isLong)
            KtTokens.PLUS -> base
            KtTokens.EXCL -> null
            else -> null
        }
    }

    private fun evaluateKotlinBinary(expr: KtBinaryExpression, visited: MutableSet<KtProperty>): EvalResult? {
        val left = expr.left?.let { evaluateKotlinExpression(it, visited) } ?: return null
        val right = expr.right?.let { evaluateKotlinExpression(it, visited) } ?: return null
        val operation = expr.operationReference.getReferencedName()

        return when (operation) {
            "or" -> combine(left, right, left.value or right.value)
            "and" -> combine(left, right, left.value and right.value)
            "xor" -> combine(left, right, left.value xor right.value)
            "shl" -> combine(left, right, left.value shl right.value.toInt())
            "shr" -> combine(left, right, left.value shr right.value.toInt())
            "ushr" -> combine(left, right, left.value ushr right.value.toInt())
            else -> null
        }
    }

    private fun evaluateKotlinDotQualified(expr: KtDotQualifiedExpression, visited: MutableSet<KtProperty>): EvalResult? {
        val selector = expr.selectorExpression ?: return null

        return when (selector) {
            is KtCallExpression -> {
                val receiver = evaluateKotlinExpression(expr.receiverExpression, visited) ?: return null
                evaluateCallOnReceiver(receiver, selector, visited)
            }
            is KtNameReferenceExpression -> {
                if (selector.getReferencedName() == "inv") {
                    val receiver = evaluateKotlinExpression(expr.receiverExpression, visited) ?: return null
                    EvalResult(receiver.value.inv(), receiver.isLong)
                } else {
                    resolveKotlinReference(selector, visited)
                }
            }
            else -> null
        }
    }

    private fun evaluateCallOnReceiver(
        receiver: EvalResult,
        call: KtCallExpression,
        visited: MutableSet<KtProperty>
    ): EvalResult? {
        val callee = call.calleeExpression?.text ?: return null
        return when (callee) {
            "inv" -> {
                if (call.valueArguments.isNotEmpty()) return null
                EvalResult(receiver.value.inv(), receiver.isLong)
            }
            "or", "and", "xor", "shl", "shr", "ushr" -> {
                val argumentExpr = call.valueArguments.singleOrNull()?.getArgumentExpression() ?: return null
                val argument = evaluateKotlinExpression(argumentExpr, visited) ?: return null
                when (callee) {
                    "or" -> combine(receiver, argument, receiver.value or argument.value)
                    "and" -> combine(receiver, argument, receiver.value and argument.value)
                    "xor" -> combine(receiver, argument, receiver.value xor argument.value)
                    "shl" -> combine(receiver, argument, receiver.value shl argument.value.toInt())
                    "shr" -> combine(receiver, argument, receiver.value shr argument.value.toInt())
                    "ushr" -> combine(receiver, argument, receiver.value ushr argument.value.toInt())
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun resolveKotlinReference(
        expr: KtNameReferenceExpression,
        visited: MutableSet<KtProperty>
    ): EvalResult? {
        val property = expr.references.firstNotNullOfOrNull { it.resolve() as? KtProperty } ?: return null
        if (!visited.add(property)) return null

        val initializer = property.initializer
        val result = initializer?.let { evaluateKotlinExpression(it, visited) }

        visited.remove(property)
        return result
    }

    private fun parseKotlinConstant(text: String): EvalResult? {
        val sanitized = text.replace("_", "")
        if (sanitized.isEmpty()) return null

        val isLong = sanitized.endsWith("L", ignoreCase = true)
        val literal = if (isLong) sanitized.dropLast(1) else sanitized

        val value = when {
            literal.startsWith("0x", ignoreCase = true) -> parseUnsignedLong(literal.drop(2), 16)
            literal.startsWith("0b", ignoreCase = true) -> parseUnsignedLong(literal.drop(2), 2)
            else -> literal.toLongOrNull()
        } ?: return null

        return EvalResult(value, isLong)
    }

    private fun combine(left: EvalResult, right: EvalResult, value: Long): EvalResult {
        return EvalResult(value, left.isLong || right.isLong)
    }

    class EvalResult(@JvmField val value: Long, @JvmField val isLong: Boolean) {
        fun toDecimalString(): String {
            return if (isLong) value.toString() else value.toInt().toString()
        }

        fun toHexString(): String {
            return if (isLong) {
                String.format("0x%016X", value)
            } else {
                String.format("0x%08X", value.toInt())
            }
        }

        fun toBinaryString(): String {
            return if (isLong) {
                val bin = String.format("%64s", toBinaryString(value)).replace(' ', '0')
                groupBinary(bin, 64)
            } else {
                val bin = String.format("%32s", toBinaryString(value.toInt())).replace(' ', '0')
                groupBinary(bin, 32)
            }
        }

        private fun groupBinary(bin: String, bits: Int): String {
            val sb = StringBuilder("0b")
            for (i in 0 until bits) {
                if (i > 0 && i % 4 == 0) sb.append('_')
                sb.append(bin[i])
            }
            return sb.toString()
        }
    }

    private class Parser(private val input: String) {
        private var pos = 0
        private var hasLong = false

        fun parse(): EvalResult {
            val value = parseOr()
            skipWhitespace()
            if (pos != input.length) {
                throw RuntimeException("Unexpected token at $pos")
            }
            return EvalResult(value, hasLong)
        }

        private fun parseOr(): Long {
            var left = parseXor()
            while (peek() == '|' && peekNext() != '|') {
                advance()
                val right = parseXor()
                left = left or right
            }
            return left
        }

        private fun parseXor(): Long {
            var left = parseAnd()
            while (peek() == '^') {
                advance()
                val right = parseAnd()
                left = left xor right
            }
            return left
        }

        private fun parseAnd(): Long {
            var left = parseShift()
            while (peek() == '&' && peekNext() != '&') {
                advance()
                val right = parseShift()
                left = left and right
            }
            return left
        }

        private fun parseShift(): Long {
            var left = parseUnary()
            while (true) {
                skipWhitespace()
                when {
                    pos + 2 < input.length && input[pos] == '>' && input[pos + 1] == '>' && input[pos + 2] == '>' -> {
                        pos += 3
                        left = left ushr parseUnary().toInt()
                    }
                    pos + 1 < input.length && input[pos] == '<' && input[pos + 1] == '<' -> {
                        pos += 2
                        left = left shl parseUnary().toInt()
                    }
                    pos + 1 < input.length && input[pos] == '>' && input[pos + 1] == '>' -> {
                        pos += 2
                        left = left shr parseUnary().toInt()
                    }
                    else -> return left
                }
            }
        }

        private fun parseUnary(): Long {
            skipWhitespace()
            return when (peek()) {
                '~' -> {
                    advance()
                    parseUnary().inv()
                }
                '-' -> {
                    advance()
                    -parseUnary()
                }
                else -> parsePrimary()
            }
        }

        private fun parsePrimary(): Long {
            skipWhitespace()
            if (peek() == '(') {
                advance()
                val value = parseOr()
                skipWhitespace()
                if (peek() != ')') throw RuntimeException("Expected ')'")
                advance()
                return value
            }
            return parseLiteral()
        }

        private fun parseLiteral(): Long {
            skipWhitespace()
            if (pos >= input.length) throw RuntimeException("Unexpected end of input")

            if (pos + 1 < input.length && input[pos] == '0' && (input[pos + 1] == 'x' || input[pos + 1] == 'X')) {
                pos += 2
                return readHex()
            }

            if (pos + 1 < input.length && input[pos] == '0' && (input[pos + 1] == 'b' || input[pos + 1] == 'B')) {
                pos += 2
                return readBinary()
            }

            return readDecimal()
        }

        private fun readHex(): Long {
            val sb = StringBuilder()
            while (pos < input.length) {
                val c = input[pos]
                if (c.isDigit() || c in 'a'..'f' || c in 'A'..'F' || c == '_') {
                    if (c != '_') sb.append(c)
                    pos++
                } else {
                    break
                }
            }
            checkLongSuffix()
            return parseUnsignedLong(sb.toString(), 16)
        }

        private fun readBinary(): Long {
            val sb = StringBuilder()
            while (pos < input.length) {
                val c = input[pos]
                if (c == '0' || c == '1' || c == '_') {
                    if (c != '_') sb.append(c)
                    pos++
                } else {
                    break
                }
            }
            checkLongSuffix()
            return parseUnsignedLong(sb.toString(), 2)
        }

        private fun readDecimal(): Long {
            val sb = StringBuilder()
            while (pos < input.length) {
                val c = input[pos]
                if (c.isDigit() || c == '_') {
                    if (c != '_') sb.append(c)
                    pos++
                } else {
                    break
                }
            }
            checkLongSuffix()
            if (sb.isEmpty()) throw RuntimeException("Expected digit")
            return sb.toString().toLong()
        }

        private fun checkLongSuffix() {
            if (pos < input.length && (input[pos] == 'L' || input[pos] == 'l')) {
                pos++
                hasLong = true
            }
        }

        private fun peek(): Char {
            skipWhitespace()
            return if (pos >= input.length) 0.toChar() else input[pos]
        }

        private fun peekNext(): Char {
            val saved = pos
            skipWhitespace()
            val next = if (pos + 1 >= input.length) 0.toChar() else input[pos + 1]
            pos = saved
            return next
        }

        private fun advance() {
            skipWhitespace()
            pos++
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) {
                pos++
            }
        }
    }
}
