package com.example.geo2d

import kotlin.math.*

/**
 * Recursive-descent mathematical expression parser, AST evaluator, symbolic differentiator,
 * and graph key-point analyzer for arbitrary y = f(x) functions.
 *
 * Supports:
 *  - Operators: +, -, *, /, ^ (right-associative), unary +/-, implicit multiplication (2x, 3sin(x), (x+1)(x-1))
 *  - Constants: pi, π, e
 *  - Functions: sin, cos, tan, cot, asin, acos, atan, sinh, cosh, tanh, sqrt, cbrt, abs, ln, log (base 10 or log(base; x)), log2, log10, exp, floor, ceil, round, sign
 *  - Symbolic differentiation: expr.differentiate().simplify() + pretty formula string
 */
object MathExprParser {

    sealed class Expr {
        data class Num(val value: Double) : Expr()
        data object VarX : Expr()
        data object VarM : Expr()
        data class UnaryNeg(val inner: Expr) : Expr()
        data class Binary(val op: Char, val left: Expr, val right: Expr) : Expr()
        data class Func(val name: String, val arg: Expr, val base: Expr? = null) : Expr()

        fun eval(x: Double, m: Double = 0.0): Double = when (this) {
            is Num -> value
            is VarX -> x
            is VarM -> m
            is UnaryNeg -> -inner.eval(x, m)
            is Binary -> {
                val l = left.eval(x, m)
                val r = right.eval(x, m)
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
                val v = arg.eval(x, m)
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
                    "log" -> {
                        if (base != null) {
                            val b = base.eval(x, m)
                            if (v <= 0.0 || b <= 0.0 || abs(b - 1.0) < 1e-12) Double.NaN else ln(v) / ln(b)
                        } else {
                            if (v <= 0.0) Double.NaN else log10(v)
                        }
                    }
                    "log10" -> if (v <= 0.0) Double.NaN else log10(v)
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

        fun derivative(x: Double, stepScale: Double = 1e-5, m: Double = 0.0): Double {
            val h = max(1e-7, abs(stepScale))
            val f1 = eval(x + h, m)
            val f0 = eval(x - h, m)
            if (!f1.isFinite() || !f0.isFinite()) return Double.NaN
            return (f1 - f0) / (2.0 * h)
        }

        fun containsVarX(): Boolean = when (this) {
            is Num, is VarM -> false
            is VarX -> true
            is UnaryNeg -> inner.containsVarX()
            is Binary -> left.containsVarX() || right.containsVarX()
            is Func -> arg.containsVarX() || (base?.containsVarX() == true)
        }

        fun containsVarM(): Boolean = when (this) {
            is Num, is VarX -> false
            is VarM -> true
            is UnaryNeg -> inner.containsVarM()
            is Binary -> left.containsVarM() || right.containsVarM()
            is Func -> arg.containsVarM() || (base?.containsVarM() == true)
        }

        /**
         * Returns the exact symbolic derivative AST (with respect to x, treating m as a constant parameter),
         * or null if non-differentiable (e.g. floor/ceil).
         */
        fun symbolicDerivative(): Expr? = when (this) {
            is Num, is VarM -> Num(0.0)
            is VarX -> Num(1.0)
            is UnaryNeg -> inner.symbolicDerivative()?.let { UnaryNeg(it) }
            is Binary -> {
                val dl = left.symbolicDerivative() ?: return null
                val dr = right.symbolicDerivative() ?: return null
                when (op) {
                    '+' -> Binary('+', dl, dr)
                    '-' -> Binary('-', dl, dr)
                    '*' -> Binary('+', Binary('*', dl, right), Binary('*', left, dr))
                    '/' -> Binary('/', Binary('-', Binary('*', dl, right), Binary('*', left, dr)), Binary('^', right, Num(2.0)))
                    '^' -> {
                        val lHasX = left.containsVarX()
                        val rHasX = right.containsVarX()
                        when {
                            !lHasX && !rHasX -> Num(0.0)
                            lHasX && !rHasX -> {
                                // u^c -> c * u^(c-1) * u'
                                Binary('*', Binary('*', right, Binary('^', left, Binary('-', right, Num(1.0)))), dl)
                            }
                            !lHasX && rHasX -> {
                                // a^v -> a^v * ln(a) * v'
                                val lnFactor = if (!left.containsVarM() && abs(left.eval(0.0) - E) < 1e-9) Num(1.0) else Func("ln", left)
                                Binary('*', Binary('*', this, lnFactor), dr)
                            }
                            else -> {
                                // u^v -> u^v * (v' * ln(u) + v * u' / u)
                                Binary(
                                    '*',
                                    this,
                                    Binary('+', Binary('*', dr, Func("ln", left)), Binary('/', Binary('*', right, dl), left))
                                )
                            }
                        }
                    }
                    else -> null
                }
            }
            is Func -> {
                val du = arg.symbolicDerivative() ?: return null
                val outer: Expr = when (name) {
                    "sin" -> Func("cos", arg)
                    "cos" -> UnaryNeg(Func("sin", arg))
                    "tan" -> Binary('/', Num(1.0), Binary('^', Func("cos", arg), Num(2.0)))
                    "cot" -> Binary('/', Num(-1.0), Binary('^', Func("sin", arg), Num(2.0)))
                    "asin", "arcsin" -> Binary('/', Num(1.0), Func("sqrt", Binary('-', Num(1.0), Binary('^', arg, Num(2.0)))))
                    "acos", "arccos" -> Binary('/', Num(-1.0), Func("sqrt", Binary('-', Num(1.0), Binary('^', arg, Num(2.0)))))
                    "atan", "arctan" -> Binary('/', Num(1.0), Binary('+', Num(1.0), Binary('^', arg, Num(2.0))))
                    "sinh" -> Func("cosh", arg)
                    "cosh" -> Func("sinh", arg)
                    "tanh" -> Binary('/', Num(1.0), Binary('^', Func("cosh", arg), Num(2.0)))
                    "sqrt" -> Binary('/', Num(1.0), Binary('*', Num(2.0), Func("sqrt", arg)))
                    "cbrt" -> Binary('/', Num(1.0), Binary('*', Num(3.0), Binary('^', arg, Binary('/', Num(2.0), Num(3.0)))))
                    "abs" -> Binary('/', arg, Func("abs", arg))
                    "ln" -> Binary('/', Num(1.0), arg)
                    "log10" -> Binary('/', Num(1.0), Binary('*', arg, Func("ln", Num(10.0))))
                    "log2" -> Binary('/', Num(1.0), Binary('*', arg, Func("ln", Num(2.0))))
                    "log" -> {
                        if (base == null) {
                            Binary('/', Num(1.0), Binary('*', arg, Func("ln", Num(10.0))))
                        } else if (!base.containsVarX()) {
                            Binary('/', Num(1.0), Binary('*', arg, Func("ln", base)))
                        } else {
                            // log_b(u) = ln(u) / ln(b)
                            return Binary('/', Func("ln", arg), Func("ln", base)).symbolicDerivative()
                        }
                    }
                    "exp" -> Func("exp", arg)
                    else -> return null
                }
                Binary('*', outer, du)
            }
        }

        fun simplify(): Expr = when (this) {
            is Num, is VarX, is VarM -> this
            is UnaryNeg -> {
                when (val s = inner.simplify()) {
                    is Num -> Num(-s.value)
                    is UnaryNeg -> s.inner
                    else -> UnaryNeg(s)
                }
            }
            is Func -> {
                val sa = arg.simplify()
                val sb = base?.simplify()
                if (name == "ln" && sa is Num && abs(sa.value - E) < 1e-9) Num(1.0)
                else Func(name, sa, sb)
            }
            is Binary -> {
                val l = left.simplify()
                val r = right.simplify()
                if (l is Num && r is Num) {
                    val res = Binary(op, l, r).eval(0.0)
                    if (res.isFinite() && op != '/') return Num(res)
                    if (op == '/' && res.isFinite() && abs(res - round(res)) < 1e-9) return Num(res)
                }
                when (op) {
                    '+' -> when {
                        l is Num && abs(l.value) < 1e-12 -> r
                        r is Num && abs(r.value) < 1e-12 -> l
                        r is UnaryNeg -> Binary('-', l, r.inner).simplify()
                        else -> Binary('+', l, r)
                    }
                    '-' -> when {
                        r is Num && abs(r.value) < 1e-12 -> l
                        l is Num && abs(l.value) < 1e-12 -> UnaryNeg(r).simplify()
                        r is UnaryNeg -> Binary('+', l, r.inner).simplify()
                        else -> Binary('-', l, r)
                    }
                    '*' -> when {
                        (l is Num && abs(l.value) < 1e-12) || (r is Num && abs(r.value) < 1e-12) -> Num(0.0)
                        l is Num && abs(l.value - 1.0) < 1e-12 -> r
                        r is Num && abs(r.value - 1.0) < 1e-12 -> l
                        l is Num && abs(l.value + 1.0) < 1e-12 -> UnaryNeg(r).simplify()
                        r is Num && abs(r.value + 1.0) < 1e-12 -> UnaryNeg(l).simplify()
                        // Combine constant * (constant * expr)
                        l is Num && r is Binary && r.op == '*' && r.left is Num ->
                            Binary('*', Num(l.value * r.left.value), r.right).simplify()
                        r is Num && l is Binary && l.op == '*' && l.left is Num ->
                            Binary('*', Num(r.value * l.left.value), l.right).simplify()
                        r is Num && l !is Num -> Binary('*', r, l)
                        else -> Binary('*', l, r)
                    }
                    '/' -> when {
                        l is Num && abs(l.value) < 1e-12 -> Num(0.0)
                        r is Num && abs(r.value - 1.0) < 1e-12 -> l
                        else -> Binary('/', l, r)
                    }
                    '^' -> when {
                        r is Num && abs(r.value) < 1e-12 -> Num(1.0)
                        r is Num && abs(r.value - 1.0) < 1e-12 -> l
                        l is Num && abs(l.value) < 1e-12 -> Num(0.0)
                        l is Num && abs(l.value - 1.0) < 1e-12 -> Num(1.0)
                        else -> Binary('^', l, r)
                    }
                    else -> Binary(op, l, r)
                }
            }
        }

        fun toPrettyString(parentPrec: Int = 0): String = when (this) {
            is Num -> when {
                abs(value - PI) < 1e-9 -> "π"
                abs(value - E) < 1e-9 -> "e"
                else -> Geo2DMathHelpers.formatNum(value)
            }
            is VarX -> "x"
            is VarM -> "m"
            is UnaryNeg -> {
                val core = "-${inner.toPrettyString(3)}"
                if (parentPrec >= 2) "($core)" else core
            }
            is Func -> {
                if (name == "log" && base != null) {
                    "log_${base.toPrettyString(4)}(${arg.toPrettyString(0)})"
                } else {
                    "$name(${arg.toPrettyString(0)})"
                }
            }
            is Binary -> {
                val prec = when (op) {
                    '+', '-' -> 1
                    '*', '/' -> 2
                    '^' -> 4
                    else -> 0
                }
                val lStr = left.toPrettyString(prec)
                val rStr = right.toPrettyString(if (op == '-' || op == '/') prec + 1 else prec)
                val core = when (op) {
                    '+' -> "$lStr + $rStr"
                    '-' -> "$lStr - $rStr"
                    '*' -> if (left is Num && (right is VarX || right is VarM || right is Func || (right is Binary && right.op == '^'))) {
                        "$lStr$rStr"
                    } else {
                        "$lStr·$rStr"
                    }
                    '/' -> "$lStr / $rStr"
                    '^' -> "$lStr^$rStr"
                    else -> "$lStr $op $rStr"
                }
                if (prec < parentPrec) "($core)" else core
            }
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
        data object ArgSep : Token()
    }

    private val KNOWN_FUNCS = setOf(
        "sin", "cos", "tan", "cot", "asin", "acos", "atan", "arcsin", "arccos", "arctan",
        "sinh", "cosh", "tanh", "sqrt", "cbrt", "abs", "ln", "log", "log10", "log2",
        "exp", "floor", "ceil", "round", "sign", "sgn"
    )

    private data class CachedDerivative(val value: Pair<Expr, String>?)

    private val parseCacheLock = Any()
    private val parseCache = object : LinkedHashMap<String, ParseResult>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ParseResult>?): Boolean = size > 64
    }
    private val deriveCache = object : LinkedHashMap<String, CachedDerivative>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedDerivative>?): Boolean = size > 64
    }

    fun parse(rawInput: String): ParseResult {
        synchronized(parseCacheLock) {
            parseCache[rawInput]?.let { return it }
        }
        val result = parseUncached(rawInput)
        synchronized(parseCacheLock) {
            parseCache[rawInput] = result
        }
        return result
    }

    private fun parseUncached(rawInput: String): ParseResult {
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

    /**
     * Compiles and returns a simplified symbolic derivative + readable formula for a raw function string.
     */
    fun derive(rawInput: String): Pair<Expr, String>? {
        synchronized(parseCacheLock) {
            deriveCache[rawInput]?.let { return it.value }
        }
        val parsed = parse(rawInput) as? ParseResult.Ok
        val computed = parsed?.expr?.symbolicDerivative()?.simplify()?.let { d -> d to d.toPrettyString() }
        synchronized(parseCacheLock) {
            deriveCache[rawInput] = CachedDerivative(computed)
        }
        return computed
    }

    private fun preprocess(raw: String): String {
        var s = raw.trim()
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

        // Support shorthand log_(expr)(x) -> log(expr; x) and log_a(x) -> log(a; x)
        s = Regex("log_\\(([^()]+)\\)\\(").replace(s) { m -> "log(${m.groupValues[1]}; " }
        s = Regex("log_([0-9.,]+|e|pi)\\(").replace(s) { m -> "log(${m.groupValues[1]}; " }

        // Convert |expr| into abs(expr)
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

    /**
     * Checks if the parenthesized block starting at [openParenIdx] contains a top-level ';'.
     */
    private fun parenBlockHasSemicolon(s: String, openParenIdx: Int): Boolean {
        var depth = 0
        for (j in openParenIdx until s.length) {
            when (s[j]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return false
                }
                ';' -> if (depth == 1) return true
            }
        }
        return false
    }

    private fun tokenize(s: String): List<Token>? {
        val out = mutableListOf<Token>()
        // Tracks for each open '(' whether commas at this level should act as log(a, x) argument separators
        val commaIsArgSepStack = ArrayDeque<Boolean>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == ';' -> {
                    out.add(Token.ArgSep); i++
                }
                c == ',' -> {
                    out.add(Token.ArgSep); i++
                }
                c.isDigit() || c == '.' -> {
                    val start = i
                    var dotSeen = false
                    val commaIsArgSep = commaIsArgSepStack.lastOrNull() == true
                    val numBuf = StringBuilder()
                    while (i < s.length) {
                        val ch = s[i]
                        when {
                            ch.isDigit() -> {
                                numBuf.append(ch); i++
                            }
                            ch == '.' && !dotSeen -> {
                                dotSeen = true; numBuf.append('.'); i++
                            }
                            ch == ',' && !dotSeen && !commaIsArgSep && i > start && i + 1 < s.length && s[i + 1].isDigit() -> {
                                // Vietnamese decimal comma (e.g. 0,5x^2 or log(0,5; x))
                                dotSeen = true; numBuf.append('.'); i++
                            }
                            else -> break
                        }
                    }
                    val value = numBuf.toString().toDoubleOrNull() ?: return null
                    out.add(Token.Num(value))
                }
                c.isLetter() -> {
                    val start = i
                    while (i < s.length && (s[i].isLetter() || s[i].isDigit())) {
                        i++
                    }
                    val word = s.substring(start, i).lowercase()
                    if (word in KNOWN_FUNCS || word == "pi" || word == "e" || word == "x" || word == "m") {
                        out.add(Token.Ident(word))
                    } else {
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
                                rem[0] == 'm' -> {
                                    out.add(Token.Ident("m"))
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
                    val isLogWithoutSemi = (out.lastOrNull() as? Token.Ident)?.name == "log" && !parenBlockHasSemicolon(s, i)
                    commaIsArgSepStack.addLast(isLogWithoutSemi)
                    out.add(Token.LParen); i++
                }
                c == ')' -> {
                    if (commaIsArgSepStack.isNotEmpty()) commaIsArgSepStack.removeLast()
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
                        "m" -> Expr.VarM
                        "pi" -> Expr.Num(PI)
                        "e" -> Expr.Num(E)
                        in KNOWN_FUNCS -> {
                            if (peek() is Token.LParen) {
                                consume()
                                val first = parseExpression()
                                if (t.name == "log" && peek() is Token.ArgSep) {
                                    consume()
                                    val second = parseExpression()
                                    if (peek() !is Token.RParen) throw ParseException("Thiếu ')' sau hàm log(a; x)")
                                    consume()
                                    Expr.Func("log", arg = second, base = first)
                                } else {
                                    if (peek() !is Token.RParen) throw ParseException("Thiếu ')' sau hàm ${t.name}(...")
                                    consume()
                                    Expr.Func(t.name, arg = first)
                                }
                            } else {
                                Expr.Func(t.name, arg = parseUnary())
                            }
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
     *  - Real roots (x-intercepts where f(x) = 0)
     *  - Local extrema (where f'(x) changes sign)
     *  - Inflection points (where f''(x) changes sign)
     *  - Y-intercept (0, f(0))
     */
    fun analyzeFunction(
        expr: Expr,
        xMin: Double,
        xMax: Double,
        yMin: Double,
        yMax: Double,
        funcColorHex: String,
        precomputedDeriv: Expr? = null,
        m: Double = 0.0
    ): List<GraphKeyPoint> {
        val points = mutableListOf<GraphKeyPoint>()
        val ySpan = max(1e-6, yMax - yMin)
        val xSpan = max(1e-6, xMax - xMin)
        val dStep = xSpan * 1e-5
        val symDeriv = precomputedDeriv ?: expr.symbolicDerivative()?.simplify()
        val symDeriv2 = symDeriv?.symbolicDerivative()?.simplify()

        fun evalDeriv(x: Double): Double = symDeriv?.eval(x, m) ?: expr.derivative(x, dStep, m)
        fun evalDeriv2(x: Double): Double = symDeriv2?.eval(x, m) ?: run {
            val h = max(1e-5, xSpan * 1e-4)
            val d1 = evalDeriv(x + h)
            val d0 = evalDeriv(x - h)
            if (d1.isFinite() && d0.isFinite()) (d1 - d0) / (2.0 * h) else Double.NaN
        }

        val steps = 221
        val dx = xSpan / steps
        if (dx <= 0.0) return points

        var prevX = xMin
        var prevY = expr.eval(prevX, m)
        var prevD = evalDeriv(prevX)
        var prevD2 = evalDeriv2(prevX)

        for (i in 1..steps) {
            val curX = xMin + i * dx
            val curY = expr.eval(curX, m)
            val curD = evalDeriv(curX)
            val curD2 = evalDeriv2(curX)

            if (prevY.isFinite() && curY.isFinite() && abs(curY - prevY) < ySpan * 1.5) {
                if (prevY * curY <= 0.0 && (prevY != 0.0 || curY != 0.0)) {
                    val rootX = bisect(prevX, curX) { expr.eval(it, m) }
                    val rootY = expr.eval(rootX, m)
                    if (rootY.isFinite() && abs(rootY) < ySpan * 0.02) {
                        addDistinctPoint(points, GraphKeyPoint(rootX, 0.0, KeyPointType.ROOT, funcColorHex), dx * 1.5)
                    }
                }
            }

            if (prevD.isFinite() && curD.isFinite() && prevY.isFinite() && curY.isFinite()) {
                if (((prevD > 0.0 && curD <= 0.0) || (prevD < 0.0 && curD >= 0.0)) && abs(curY - prevY) < ySpan * 0.8) {
                    val extX = bisect(prevX, curX) { evalDeriv(it) }
                    val extY = expr.eval(extX, m)
                    if (extY.isFinite() && extY in (yMin - ySpan * 0.1)..(yMax + ySpan * 0.1)) {
                        val type = if (prevD > 0.0) KeyPointType.LOCAL_MAX else KeyPointType.LOCAL_MIN
                        addDistinctPoint(points, GraphKeyPoint(extX, extY, type, funcColorHex), dx * 2.0)
                    }
                }
            }

            if (prevD2.isFinite() && curD2.isFinite() && prevY.isFinite() && curY.isFinite() && prevD.isFinite() && curD.isFinite()) {
                if (((prevD2 > 1e-6 && curD2 < -1e-6) || (prevD2 < -1e-6 && curD2 > 1e-6)) &&
                    abs(curY - prevY) < ySpan * 0.8 &&
                    abs(curD - prevD) < ySpan * 6.0
                ) {
                    val infX = bisect(prevX, curX) { evalDeriv2(it) }
                    val infY = expr.eval(infX, m)
                    val infD = evalDeriv(infX)
                    if (infY.isFinite() && infD.isFinite() && infY in (yMin - ySpan * 0.1)..(yMax + ySpan * 0.1)) {
                        // Ensure f'' genuinely changes sign across infX (not a flat linear function)
                        val eps = dx * 0.15
                        val d2L = evalDeriv2(infX - eps)
                        val d2R = evalDeriv2(infX + eps)
                        if (d2L.isFinite() && d2R.isFinite() && d2L * d2R < -1e-8) {
                            addDistinctPoint(points, GraphKeyPoint(infX, infY, KeyPointType.INFLECTION, "#C084FC"), dx * 2.5)
                        }
                    }
                }
            }

            prevX = curX
            prevY = curY
            prevD = curD
            prevD2 = curD2
        }

        if (xMin <= 0.0 && xMax >= 0.0) {
            val y0 = expr.eval(0.0, m)
            if (y0.isFinite() && y0 in (yMin - ySpan * 0.1)..(yMax + ySpan * 0.1)) {
                addDistinctPoint(points, GraphKeyPoint(0.0, y0, KeyPointType.Y_INTERCEPT, funcColorHex), dx * 1.5)
            }
        }
        return points.take(20)
    }

    /**
     * Automatically detects vertical (TCĐ), horizontal (TCN), and oblique (TCX) asymptotes.
     */
    fun findAsymptotes(
        expr: Expr,
        xMin: Double,
        xMax: Double,
        yMin: Double,
        yMax: Double,
        colorHex: String,
        m: Double = 0.0
    ): List<GraphAsymptote> {
        val result = mutableListOf<GraphAsymptote>()
        val xSpan = max(1e-6, xMax - xMin)
        val ySpan = max(1e-6, yMax - yMin)

        // 1. Vertical asymptotes (TCĐ: x = x0) within visible window
        val steps = 260
        val dx = xSpan / steps
        val vList = mutableListOf<Double>()

        fun addVertical(rawX: Double) {
            val snapped = snapCleanVal(rawX, dx * 0.25)
            if (vList.none { abs(it - snapped) < max(1e-2, dx * 2.5) }) {
                vList.add(snapped)
            }
        }

        var prevX = xMin
        var prevY = expr.eval(prevX, m)
        for (i in 1..steps) {
            val curX = xMin + i * dx
            val curY = expr.eval(curX, m)

            val deltaY = if (prevY.isFinite() && curY.isFinite()) abs(curY - prevY) else 0.0
            val hugeJump = prevY.isFinite() && curY.isFinite() &&
                ((prevY * curY < 0.0 && deltaY > 6.0 && deltaY / dx > 25.0) ||
                    (deltaY > max(10.0, ySpan * 0.45) && deltaY / dx > 50.0))
            val edgeNaN = (prevY.isFinite() && !curY.isFinite()) || (!prevY.isFinite() && curY.isFinite())

            if (hugeJump) {
                val midY = 0.5 * (prevY + curY)
                val poleX = bisect(prevX, curX) { x ->
                    val y = expr.eval(x, m) - midY
                    if (!y.isFinite() || abs(y) > 1e9) 0.0 else 1.0 / y
                }
                val nearL = expr.eval(poleX - 1e-5, m)
                val nearR = expr.eval(poleX + 1e-5, m)
                val farL = expr.eval(poleX - dx * 0.5, m)
                val farR = expr.eval(poleX + dx * 0.5, m)
                val blowsUp = (nearL.isFinite() && abs(nearL) > 25.0 && (!farL.isFinite() || abs(nearL) > abs(farL) * 2.5)) ||
                    (nearR.isFinite() && abs(nearR) > 25.0 && (!farR.isFinite() || abs(nearR) > abs(farR) * 2.5))
                if (blowsUp) addVertical(poleX)
            } else if (edgeNaN) {
                // Handle logarithmic/one-sided vertical asymptote like ln(x - a)
                var lo = prevX
                var hi = curX
                val leftFinite = prevY.isFinite()
                repeat(24) {
                    val mid = (lo + hi) * 0.5
                    val midF = expr.eval(mid, m).isFinite()
                    if (midF == leftFinite) lo = mid else hi = mid
                }
                val boundary = (lo + hi) * 0.5
                val probeDir = if (leftFinite) -1.0 else 1.0
                val yNear = expr.eval(boundary + probeDir * 1e-6, m)
                val yMid = expr.eval(boundary + probeDir * 1e-3, m)
                val yFar = expr.eval(boundary + probeDir * 0.05, m)
                if (yNear.isFinite() && yMid.isFinite() && yFar.isFinite() &&
                    abs(yNear) > abs(yMid) + 2.0 && abs(yMid) > abs(yFar) + 1.0 && abs(yNear) > 8.0
                ) {
                    addVertical(boundary)
                }
            }

            prevX = curX
            prevY = curY
        }
        vList.take(8).forEach { result.add(GraphAsymptote.Vertical(it, colorHex)) }

        // 2. Horizontal (TCN: y = y0) & Oblique (TCX: y = ax + b) asymptotes as x -> ±∞
        val hList = mutableListOf<Double>()
        val oList = mutableListOf<Pair<Double, Double>>()

        for (sign in doubleArrayOf(1.0, -1.0)) {
            val x1 = sign * 1e4
            val x2 = sign * 1e5
            val y1 = expr.eval(x1, m)
            val y2 = expr.eval(x2, m)
            if (!y1.isFinite() || !y2.isFinite()) continue

            // Check horizontal asymptote
            val dFar = expr.derivative(x2, 1.0, m)
            if (abs(y2) < 1e4 && abs(y2 - y1) < 5e-3 && dFar.isFinite() && abs(dFar) < 1e-3) {
                // Guard against periodic functions (like sin(x)) by sampling 3 extra shifted points
                val yCheck1 = expr.eval(x2 + 1.7, m)
                val yCheck2 = expr.eval(x2 + 3.1, m)
                if (yCheck1.isFinite() && yCheck2.isFinite() &&
                    abs(yCheck1 - y2) < 2e-3 && abs(yCheck2 - y2) < 2e-3
                ) {
                    val cleanY = snapCleanVal(y2, 1e-3)
                    if (hList.none { abs(it - cleanY) < 1e-2 }) {
                        hList.add(cleanY)
                    }
                }
            } else {
                // Check oblique asymptote y = a*x + b
                val a1 = y1 / x1
                val a2 = y2 / x2
                if (a1.isFinite() && a2.isFinite() && abs(a2) in 1e-3..1e3 && abs(a2 - a1) < 2e-3) {
                    val b1 = y1 - a2 * x1
                    val b2 = y2 - a2 * x2
                    val yCheck = expr.eval(x2 + 2.3, m)
                    val bCheck = yCheck - a2 * (x2 + 2.3)
                    if (b1.isFinite() && b2.isFinite() && bCheck.isFinite() &&
                        abs(b2) < 1e4 && abs(b2 - b1) < 2e-2 && abs(bCheck - b2) < 2e-2
                    ) {
                        val cleanA = snapCleanVal(a2, 2e-3)
                        val cleanB = snapCleanVal(b2, 5e-3)
                        // Skip pure linear functions y = ax + b (not an asymptote of itself)
                        val midTest = expr.eval(0.73, m) - (cleanA * 0.73 + cleanB)
                        val midTest2 = expr.eval(-1.41, m) - (cleanA * -1.41 + cleanB)
                        val isAlreadyLine = midTest.isFinite() && midTest2.isFinite() &&
                            abs(midTest) < 1e-4 && abs(midTest2) < 1e-4
                        if (!isAlreadyLine && oList.none { abs(it.first - cleanA) < 1e-2 && abs(it.second - cleanB) < 2e-2 }) {
                            oList.add(cleanA to cleanB)
                        }
                    }
                }
            }
        }

        hList.forEach { result.add(GraphAsymptote.Horizontal(it, colorHex)) }
        oList.forEach { (a, b) -> result.add(GraphAsymptote.Oblique(a, b, colorHex)) }
        return result
    }

    private fun snapCleanVal(v: Double, tol: Double): Double {
        if (!v.isFinite()) return v
        val nearHalf = round(v * 2.0) / 2.0
        if (abs(v - nearHalf) < max(1e-3, tol)) return if (abs(nearHalf) < 1e-9) 0.0 else nearHalf
        val nearThird = round(v * 3.0) / 3.0
        if (abs(v - nearThird) < 1e-3) return nearThird
        return v
    }

    fun findIntersections(
        f1: Expr,
        f2: Expr,
        xMin: Double,
        xMax: Double,
        yMin: Double,
        yMax: Double,
        m: Double = 0.0
    ): List<GraphKeyPoint> {
        val out = mutableListOf<GraphKeyPoint>()
        val steps = 200
        val dx = (xMax - xMin) / steps
        if (dx <= 0.0) return out
        val ySpan = max(1e-6, yMax - yMin)
        var prevX = xMin
        var prevDiff = f1.eval(prevX, m) - f2.eval(prevX, m)

        for (i in 1..steps) {
            val curX = xMin + i * dx
            val curDiff = f1.eval(curX, m) - f2.eval(curX, m)
            if (prevDiff.isFinite() && curDiff.isFinite() && prevDiff * curDiff <= 0.0 && abs(curDiff - prevDiff) < ySpan * 1.5) {
                val ix = bisect(prevX, curX) { f1.eval(it, m) - f2.eval(it, m) }
                val iy = f1.eval(ix, m)
                val iy2 = f2.eval(ix, m)
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
        repeat(26) {
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
        val exists = list.any { it.type == pt.type && hypot(it.x - pt.x, it.y - pt.y) < max(1e-5, tol) }
        if (!exists) list.add(pt)
    }

    // ================= INTEGRAL, BOUNDED AREA, REVOLUTION VOLUME & VALUE TABLE =================

    data class Integral2DResult(
        val a: Double,
        val b: Double,
        val signedIntegral: Double,
        val boundedArea: Double,
        val volumeOx: Double,
        val sampleXs: DoubleArray,
        val sampleY1s: DoubleArray,
        val sampleY2s: DoubleArray
    )

    data class ValueTableRow2D(
        val x: Double,
        val y: Double,
        val dy: Double
    )

    fun computeIntegral2D(
        exprF: Expr,
        exprG: Expr?,
        a: Double,
        b: Double,
        m: Double = 0.0
    ): Integral2DResult? {
        if (!a.isFinite() || !b.isFinite()) return null
        val lo = min(a, b)
        val hi = max(a, b)
        if (hi - lo < 1e-9) {
            val y1 = exprF.eval(lo, m)
            val y2 = exprG?.eval(lo, m) ?: 0.0
            return Integral2DResult(
                a = a,
                b = b,
                signedIntegral = 0.0,
                boundedArea = 0.0,
                volumeOx = 0.0,
                sampleXs = doubleArrayOf(lo),
                sampleY1s = doubleArrayOf(y1),
                sampleY2s = doubleArrayOf(y2)
            )
        }
        val sign = if (b >= a) 1.0 else -1.0
        val n = 360
        val h = (hi - lo) / n
        var sumSigned = 0.0
        var sumArea = 0.0
        var sumVol = 0.0

        for (i in 0..n) {
            val x = lo + i * h
            val y1 = exprF.eval(x, m)
            val y2 = exprG?.eval(x, m) ?: 0.0
            if (!y1.isFinite() || !y2.isFinite()) continue
            val w = when {
                i == 0 || i == n -> 1.0
                i % 2 == 1 -> 4.0
                else -> 2.0
            }
            val diff = y1 - y2
            sumSigned += w * diff
            sumArea += w * abs(diff)
            sumVol += w * abs(y1 * y1 - y2 * y2)
        }

        val scale = h / 3.0
        val signedVal = sign * sumSigned * scale
        val areaVal = sumArea * scale
        val volVal = PI * sumVol * scale

        val samples = 140
        val xs = DoubleArray(samples + 1)
        val y1s = DoubleArray(samples + 1)
        val y2s = DoubleArray(samples + 1)
        for (i in 0..samples) {
            val x = lo + (hi - lo) * i / samples
            xs[i] = x
            y1s[i] = exprF.eval(x, m)
            y2s[i] = exprG?.eval(x, m) ?: 0.0
        }

        return Integral2DResult(
            a = a,
            b = b,
            signedIntegral = signedVal,
            boundedArea = areaVal,
            volumeOx = volVal,
            sampleXs = xs,
            sampleY1s = y1s,
            sampleY2s = y2s
        )
    }

    fun buildValueTable2D(
        expr: Expr,
        start: Double,
        end: Double,
        step: Double,
        m: Double = 0.0,
        maxRows: Int = 80
    ): List<ValueTableRow2D> {
        if (!start.isFinite() || !end.isFinite() || !step.isFinite() || step <= 1e-6) return emptyList()
        val lo = min(start, end)
        val hi = max(start, end)
        val out = ArrayList<ValueTableRow2D>()
        var x = lo
        var count = 0
        while (x <= hi + step * 1e-4 && count < maxRows) {
            val cleanX = if (abs(x) < 1e-9) 0.0 else x
            val y = expr.eval(cleanX, m)
            val dy = expr.derivative(cleanX, max(1e-5, step * 1e-3), m)
            out.add(ValueTableRow2D(cleanX, y, dy))
            x += step
            count++
        }
        return out
    }
}
