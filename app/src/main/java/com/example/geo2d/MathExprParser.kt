package com.example.geo2d

import kotlin.math.*

/**
 * Recursive-descent mathematical expression parser & evaluator for arbitrary y = f(x) functions.
 * Supports:
 *  - Operators: +, -, *, /, ^ (right-associative), unary +/-, implicit multiplication (2x, 3sin(x), (x+1)(x-1))
 *  - Constants: pi, π, e
 *  - Functions: sin, cos, tan, cot, asin, acos, atan, sinh, cosh, tanh, sqrt, cbrt, abs, ln, log, log2, exp, floor, ceil, round, sign
 *  - Preprocessing: accepts "y = ..." or "f(x) = ..." prefix, superscript ² ³, unicode × ÷ −
 */
object MathExprParser {

    sealed class Expr {
        data class Num(val value: Double) : Expr()
        data object VarX : Expr()
        data class UnaryNeg(val inner: Expr) : Expr()
        data class Binary(val op: Char, val left: Expr, val right: Expr) : Expr()
        data class Func(val name: String, val arg: Expr) : Expr()

        fun eval(x: Double): Double = when (this) {
            is Num -> value
            is VarX -> x
            is UnaryNeg -> -inner.eval(x)
            is Binary -> {
                val l = left.eval(x)
                val r = right.eval(x)
                when (op) {
                    '+' -> l + r
                    '-' -> l - r
                    '*' -> l * r
                    '/' -> if (r == 0.0) Double.NaN else l / r
                    '^' -> l.pow(r)
                    else -> Double.NaN
                }
            }
            is Func -> {
                val v = arg.eval(x)
                when (name) {
                    "sin" -> sin(v)
                    "cos" -> cos(v)
                    "tan" -> {
                        val c = cos(v)
                        if (abs(c) < 1e-9) Double.NaN else sin(v) / c
                    }
                    "cot" -> {
                        val s = sin(v)
                        if (abs(s) < 1e-9) Double.NaN else cos(v) / s
                    }
                    "asin", "arcsin" -> asin(v)
                    "acos", "arccos" -> acos(v)
                    "atan", "arctan" -> atan(v)
                    "sinh" -> sinh(v)
                    "cosh" -> cosh(v)
                    "tanh" -> tanh(v)
                    "sqrt" -> if (v < 0.0) Double.NaN else sqrt(v)
                    "cbrt" -> cbrt(v)
                    "abs" -> abs(v)
                    "ln" -> if (v <= 0.0) Double.NaN else ln(v)
                    "log", "log10" -> if (v <= 0.0) Double.NaN else log10(v)
                    "log2" -> if (v <= 0.0) Double.NaN else log2(v)
                    "exp" -> exp(v)
                    "floor" -> floor(v)
                    "ceil" -> ceil(v)
                    "round" -> round(v)
                    "sign", "sgn" -> sign(v)
                    else -> Double.NaN
                }
            }
        }

        fun derivative(x: Double): Double {
            val h = 1e-5
            val f1 = eval(x + h)
            val f0 = eval(x - h)
            if (!f1.isFinite() || !f0.isFinite()) return Double.NaN
            return (f1 - f0) / (2.0 * h)
        }
    }

    sealed class ParseResult {
        data class Ok(val expr: Expr) : ParseResult()
        data class Error(val message: String) : ParseResult()
    }

    private sealed class Token {
        data class Num(val value: Double) : Token()
        data class Ident(val name: String) : Token()
        data class Op(val ch: Char) : Token()
        data object LParen : Token()
        data object RParen : Token()
    }

    private val KNOWN_FUNCS = setOf(
        "sin", "cos", "tan", "cot", "asin", "acos", "atan", "arcsin", "arccos", "arctan",
        "sinh", "cosh", "tanh", "sqrt", "cbrt", "abs", "ln", "log", "log10", "log2",
        "exp", "floor", "ceil", "round", "sign", "sgn"
    )

    fun parse(rawInput: String): ParseResult {
        val cleaned = preprocess(rawInput)
        if (cleaned.isBlank()) return ParseResult.Error("Vui lòng nhập biểu thức hàm số (VD: x^2 - 2x)")

        val tokens = tokenize(cleaned) ?: return ParseResult.Error("Biểu thức chứa ký tự không hợp lệ")
        if (tokens.isEmpty()) return ParseResult.Error("Biểu thức trống")

        val expanded = insertImplicitMultiplication(tokens)
        val parser = ParserState(expanded)
        return try {
            val ast = parser.parseExpression()
            if (parser.hasMore()) {
                ParseResult.Error("Cú pháp thừa ký tự ở cuối biểu thức")
            } else {
                ParseResult.Ok(ast)
            }
        } catch (e: ParseException) {
            ParseResult.Error(e.message ?: "Lỗi cú pháp hàm số")
        }
    }

    private fun preprocess(raw: String): String {
        var s = raw.trim()
        // Strip leading "y =" or "f(x) =" or "g(x) ="
        val eqIdx = s.indexOf('=')
        if (eqIdx != -1) {
            s = s.substring(eqIdx + 1)
        }
        s = s.replace("−", "-")
            .replace("–", "-")
            .replace("×", "*")
            .replace("·", "*")
            .replace("÷", "/")
            .replace("π", "pi")
            .replace("²", "^2")
            .replace("³", "^3")
            .replace("√", "sqrt")
            .replace("[", "(")
            .replace("]", ")")
            .replace("{", "(")
            .replace("}", ")")
            .replace(",", ".")

        // Convert |expr| into abs(expr) for simple non-nested vertical bars
        if (s.contains('|')) {
            val out = StringBuilder()
            var open = false
            for (ch in s) {
                if (ch == '|') {
                    if (!open) {
                        out.append("abs(")
                        open = true
                    } else {
                        out.append(")")
                        open = false
                    }
                } else {
                    out.append(ch)
                }
            }
            if (open) out.append(")")
            s = out.toString()
        }
        return s.trim()
    }

    private fun tokenize(s: String): List<Token>? {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    var dotSeen = false
                    while (i < s.length && (s[i].isDigit() || (s[i] == '.' && !dotSeen))) {
                        if (s[i] == '.') dotSeen = true
                        i++
                    }
                    val numStr = s.substring(start, i)
                    val value = numStr.toDoubleOrNull() ?: return null
                    out.add(Token.Num(value))
                }
                c.isLetter() -> {
                    val start = i
                    while (i < s.length && (s[i].isLetter() || s[i].isDigit())) {
                        i++
                    }
                    val word = s.substring(start, i).lowercase()
                    // Split combined identifiers like "xsin" or "pix" if not a known func/const
                    if (word in KNOWN_FUNCS || word == "pi" || word == "e" || word == "x") {
                        out.add(Token.Ident(word))
                    } else {
                        // Try breaking into known prefixes (e.g. "2pix" -> "pi", "x" or "x2")
                        var pos = 0
                        while (pos < word.length) {
                            val rem = word.substring(pos)
                            val matchedFunc = KNOWN_FUNCS.filter { rem.startsWith(it) }.maxByOrNull { it.length }
                            when {
                                matchedFunc != null -> {
                                    out.add(Token.Ident(matchedFunc))
                                    pos += matchedFunc.length
                                }
                                rem.startsWith("pi") -> {
                                    out.add(Token.Ident("pi"))
                                    pos += 2
                                }
                                rem[0] == 'x' -> {
                                    out.add(Token.Ident("x"))
                                    pos++
                                }
                                rem[0] == 'e' -> {
                                    out.add(Token.Ident("e"))
                                    pos++
                                }
                                rem[0].isDigit() -> {
                                    val numStart = pos
                                    while (pos < word.length && word[pos].isDigit()) pos++
                                    out.add(Token.Num(word.substring(numStart, pos).toDouble()))
                                }
                                else -> return null
                            }
                        }
                    }
                }
                c == '(' -> {
                    out.add(Token.LParen); i++
                }
                c == ')' -> {
                    out.add(Token.RParen); i++
                }
                c in setOf('+', '-', '*', '/', '^') -> {
                    out.add(Token.Op(c)); i++
                }
                else -> return null
            }
        }
        return out
    }

    private fun insertImplicitMultiplication(tokens: List<Token>): List<Token> {
        if (tokens.size < 2) return tokens
        val result = mutableListOf<Token>()
        for (i in tokens.indices) {
            val cur = tokens[i]
            if (i > 0) {
                val prev = tokens[i - 1]
                val prevIsValue = prev is Token.Num || prev is Token.RParen ||
                    (prev is Token.Ident && prev.name !in KNOWN_FUNCS)
                val curStartsFactor = cur is Token.LParen || cur is Token.Ident || cur is Token.Num
                if (prevIsValue && curStartsFactor) {
                    result.add(Token.Op('*'))
                }
            }
            result.add(cur)
        }
        return result
    }

    private class ParseException(msg: String) : RuntimeException(msg)

    private class ParserState(private val tokens: List<Token>) {
        private var pos = 0

        fun hasMore(): Boolean = pos < tokens.size
        private fun peek(): Token? = tokens.getOrNull(pos)
        private fun consume(): Token = tokens[pos++]

        fun parseExpression(): Expr {
            var left = parseTerm()
            while (true) {
                val t = peek()
                if (t is Token.Op && (t.ch == '+' || t.ch == '-')) {
                    consume()
                    val right = parseTerm()
                    left = Expr.Binary(t.ch, left, right)
                } else break
            }
            return left
        }

        private fun parseTerm(): Expr {
            var left = parsePower()
            while (true) {
                val t = peek()
                if (t is Token.Op && (t.ch == '*' || t.ch == '/')) {
                    consume()
                    val right = parsePower()
                    left = Expr.Binary(t.ch, left, right)
                } else break
            }
            return left
        }

        private fun parsePower(): Expr {
            val base = parseUnary()
            val t = peek()
            if (t is Token.Op && t.ch == '^') {
                consume()
                // Right-associative exponentiation
                val exponent = parsePower()
                return Expr.Binary('^', base, exponent)
            }
            return base
        }

        private fun parseUnary(): Expr {
            val t = peek()
            if (t is Token.Op && t.ch == '+') {
                consume()
                return parseUnary()
            }
            if (t is Token.Op && t.ch == '-') {
                consume()
                // Note: -x^2 means -(x^2) in standard math notation
                return Expr.UnaryNeg(parsePower())
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Expr {
            val t = peek() ?: throw ParseException("Thiếu toán hạng trong biểu thức")
            return when (t) {
                is Token.Num -> {
                    consume()
                    Expr.Num(t.value)
                }
                is Token.LParen -> {
                    consume()
                    val inner = parseExpression()
                    if (peek() !is Token.RParen) throw ParseException("Thiếu dấu đóng ngoặc ')'")
                    consume()
                    inner
                }
                is Token.Ident -> {
                    consume()
                    when (t.name) {
                        "x" -> Expr.VarX
                        "pi" -> Expr.Num(PI)
                        "e" -> Expr.Num(E)
                        in KNOWN_FUNCS -> {
                            val arg = if (peek() is Token.LParen) {
                                consume()
                                val e = parseExpression()
                                if (peek() !is Token.RParen) throw ParseException("Thiếu ')' sau hàm ${t.name}(...")
                                consume()
                                e
                            } else {
                                // Allow shorthand like "sin x" or "sqrt 4"
                                parseUnary()
                            }
                            Expr.Func(t.name, arg)
                        }
                        else -> throw ParseException("Không hiểu ký hiệu '${t.name}'")
                    }
                }
                else -> throw ParseException("Vị trí dấu không hợp lệ")
            }
        }
    }

    /**
     * Finds key features of a compiled function within [xMin, xMax]:
     *  - Y-intercept (0, f(0))
     *  - Real roots (x-intercepts where f(x) = 0)
     *  - Local extrema (where f'(x) changes sign)
     */
    fun analyzeFunction(
        expr: Expr,
        xMin: Double,
        xMax: Double,
        yMin: Double,
        yMax: Double,
        funcColorHex: String
    ): List<GraphKeyPoint> {
        val points = mutableListOf<GraphKeyPoint>()
        val ySpan = max(1.0, yMax - yMin)

        val steps = 221
        val dx = (xMax - xMin) / steps
        if (dx <= 0.0) return points

        var prevX = xMin
        var prevY = expr.eval(prevX)
        var prevD = expr.derivative(prevX)

        for (i in 1..steps) {
            val curX = xMin + i * dx
            val curY = expr.eval(curX)
            val curD = expr.derivative(curX)

            if (prevY.isFinite() && curY.isFinite() && abs(curY - prevY) < ySpan * 1.5) {
                // Root detection
                if (prevY * curY <= 0.0 && (prevY != 0.0 || curY != 0.0)) {
                    val rootX = bisect(prevX, curX) { expr.eval(it) }
                    val rootY = expr.eval(rootX)
                    if (rootY.isFinite() && abs(rootY) < ySpan * 0.02) {
                        addDistinctPoint(points, GraphKeyPoint(rootX, 0.0, KeyPointType.ROOT, funcColorHex), dx * 1.5)
                    }
                }
            }

            // Local extremum detection (derivative sign change or exact zero crossing)
            if (prevD.isFinite() && curD.isFinite() && prevY.isFinite() && curY.isFinite()) {
                if (((prevD > 0.0 && curD <= 0.0) || (prevD < 0.0 && curD >= 0.0)) && abs(curY - prevY) < ySpan * 0.8) {
                    val extX = bisect(prevX, curX) { expr.derivative(it) }
                    val extY = expr.eval(extX)
                    if (extY.isFinite() && extY in (yMin - ySpan * 0.1)..(yMax + ySpan * 0.1)) {
                        val type = if (prevD > 0.0) KeyPointType.LOCAL_MAX else KeyPointType.LOCAL_MIN
                        addDistinctPoint(points, GraphKeyPoint(extX, extY, type, funcColorHex), dx * 2.0)
                    }
                }
            }

            prevX = curX
            prevY = curY
            prevD = curD
        }

        // Y-intercept (added if not already marked by a root or extremum at x = 0)
        if (xMin <= 0.0 && xMax >= 0.0) {
            val y0 = expr.eval(0.0)
            if (y0.isFinite() && y0 in (yMin - ySpan * 0.1)..(yMax + ySpan * 0.1)) {
                addDistinctPoint(points, GraphKeyPoint(0.0, y0, KeyPointType.Y_INTERCEPT, funcColorHex), dx * 1.5)
            }
        }
        return points.take(16)
    }

    /**
     * Finds intersections between two compiled functions within [xMin, xMax].
     */
    fun findIntersections(
        f1: Expr,
        f2: Expr,
        xMin: Double,
        xMax: Double,
        yMin: Double,
        yMax: Double
    ): List<GraphKeyPoint> {
        val out = mutableListOf<GraphKeyPoint>()
        val steps = 200
        val dx = (xMax - xMin) / steps
        val ySpan = max(1.0, yMax - yMin)
        var prevX = xMin
        var prevDiff = f1.eval(prevX) - f2.eval(prevX)

        for (i in 1..steps) {
            val curX = xMin + i * dx
            val curDiff = f1.eval(curX) - f2.eval(curX)
            if (prevDiff.isFinite() && curDiff.isFinite() && prevDiff * curDiff <= 0.0 && abs(curDiff - prevDiff) < ySpan * 1.5) {
                val ix = bisect(prevX, curX) { f1.eval(it) - f2.eval(it) }
                val iy = f1.eval(ix)
                val iy2 = f2.eval(ix)
                if (iy.isFinite() && iy2.isFinite() && abs(iy - iy2) < ySpan * 0.03 && iy in yMin..yMax) {
                    addDistinctPoint(out, GraphKeyPoint(ix, iy, KeyPointType.INTERSECTION, "#FBBF24"), dx * 2.0)
                }
            }
            prevX = curX
            prevDiff = curDiff
        }
        return out.take(10)
    }

    private fun bisect(a: Double, b: Double, fn: (Double) -> Double): Double {
        var lo = a
        var hi = b
        var fLo = fn(lo)
        repeat(24) {
            val mid = (lo + hi) * 0.5
            val fMid = fn(mid)
            if (!fMid.isFinite()) return mid
            if (fLo * fMid <= 0.0) {
                hi = mid
            } else {
                lo = mid
                fLo = fMid
            }
        }
        return (lo + hi) * 0.5
    }

    private fun addDistinctPoint(list: MutableList<GraphKeyPoint>, pt: GraphKeyPoint, tol: Double) {
        val exists = list.any { hypot(it.x - pt.x, it.y - pt.y) < max(0.08, tol) }
        if (!exists) list.add(pt)
    }
}
