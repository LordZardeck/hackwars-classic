package com.hackwars.rewrite.hackscript

class HttpHookScriptEngine(
    private val maxLoopIterations: Int = 10_000,
) {
    fun execute(
        script: String,
        input: HttpHookExecutionInput,
    ): HttpHookScriptOutcome {
        if (script.isBlank()) {
            return HttpHookScriptOutcome(
                result = HttpHookExecutionResult(
                    body = input.initialBody,
                    includeStore = input.initialIncludeStore,
                ),
            )
        }

        val engineState = EngineState(input = input)
        return try {
            val program = Parser(Lexer(script).tokenize()).parseProgram()
            Interpreter(engineState, maxLoopIterations).execute(program)
            HttpHookScriptOutcome(
                result = HttpHookExecutionResult(
                    body = engineState.body,
                    includeStore = engineState.includeStore,
                ),
                diagnostics = engineState.diagnostics,
            )
        } catch (failure: ScriptFailure) {
            engineState.diagnostics += HackScriptDiagnostic(
                code = failure.code,
                message = failure.message ?: failure.code,
                line = failure.line,
                column = failure.column,
            )
            HttpHookScriptOutcome(
                result = null,
                diagnostics = engineState.diagnostics,
            )
        } catch (throwable: Throwable) {
            engineState.diagnostics += HackScriptDiagnostic(
                code = "UNEXPECTED_RUNTIME_FAILURE",
                message = throwable.message ?: throwable::class.simpleName.orEmpty(),
            )
            HttpHookScriptOutcome(
                result = null,
                diagnostics = engineState.diagnostics,
            )
        }
    }
}

data class HttpHookExecutionInput(
    val visitorIp: String,
    val hostIp: String,
    val initialBody: String,
    val initialIncludeStore: Boolean = true,
    val queryParameters: Map<String, String> = emptyMap(),
    val formParameters: Map<String, String> = emptyMap(),
)

data class HttpHookExecutionResult(
    val body: String,
    val includeStore: Boolean = true,
)

data class HackScriptDiagnostic(
    val code: String,
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
)

data class HttpHookScriptOutcome(
    val result: HttpHookExecutionResult?,
    val diagnostics: List<HackScriptDiagnostic> = emptyList(),
)

private data class EngineState(
    val input: HttpHookExecutionInput,
    var body: String = input.initialBody,
    var includeStore: Boolean = input.initialIncludeStore,
    val diagnostics: MutableList<HackScriptDiagnostic> = mutableListOf(),
)

private class Interpreter(
    private val state: EngineState,
    private val maxLoopIterations: Int,
) {
    private val variables = linkedMapOf<String, Value>()

    fun execute(program: Program) {
        val main = program.functions.firstOrNull { it.name == "main" }
            ?: throw ScriptFailure("MISSING_MAIN", "HackScript program must declare a main() function.")
        try {
            executeBlock(main.body)
        } catch (_: ReturnSignal) {
            // Return values are intentionally ignored for HTTP hooks.
        }
    }

    private fun executeBlock(statements: List<Statement>) {
        for (statement in statements) {
            executeStatement(statement)
        }
    }

    private fun executeStatement(statement: Statement) {
        when (statement) {
            is Statement.VariableDeclaration -> {
                variables[statement.name] = statement.initializer?.let(::evaluate)
                    ?: defaultValueFor(statement.typeName)
            }

            is Statement.Assignment -> {
                ensure(variables.containsKey(statement.name), "UNKNOWN_VARIABLE", "Unknown variable ${statement.name}.")
                variables[statement.name] = evaluate(statement.expression)
            }

            is Statement.ExpressionStatement -> evaluate(statement.expression)
            is Statement.IfElse -> {
                if (evaluate(statement.condition).asBoolean()) {
                    executeNested(statement.thenBranch)
                } else if (statement.elseBranch != null) {
                    executeNested(statement.elseBranch)
                }
            }

            is Statement.WhileLoop -> {
                var iterations = 0
                while (evaluate(statement.condition).asBoolean()) {
                    iterations += 1
                    ensure(
                        iterations <= maxLoopIterations,
                        "LOOP_LIMIT_EXCEEDED",
                        "While loop exceeded $maxLoopIterations iterations.",
                    )
                    executeNested(statement.body)
                }
            }

            is Statement.ReturnStatement -> {
                throw ReturnSignal(statement.expression?.let(::evaluate) ?: Value.IntValue(0))
            }

            is Statement.Block -> executeBlock(statement.statements)
        }
    }

    private fun executeNested(statement: Statement) {
        if (statement is Statement.Block) {
            executeBlock(statement.statements)
        } else {
            executeStatement(statement)
        }
    }

    private fun evaluate(expression: Expression): Value {
        return when (expression) {
            is Expression.IntLiteral -> Value.IntValue(expression.value)
            is Expression.FloatLiteral -> Value.FloatValue(expression.value)
            is Expression.StringLiteral -> Value.StringValue(expression.value)
            is Expression.BooleanLiteral -> Value.BooleanValue(expression.value)
            is Expression.VariableReference -> variables[expression.name]
                ?: throw ScriptFailure("UNKNOWN_VARIABLE", "Unknown variable ${expression.name}.")

            is Expression.Grouping -> evaluate(expression.expression)
            is Expression.Unary -> evaluateUnary(expression)
            is Expression.Binary -> evaluateBinary(expression)
            is Expression.FunctionCall -> evaluateFunctionCall(expression)
        }
    }

    private fun evaluateUnary(expression: Expression.Unary): Value {
        val value = evaluate(expression.expression)
        return when (expression.operator) {
            TokenType.BANG -> Value.BooleanValue(!value.asBoolean())
            TokenType.MINUS -> when (value) {
                is Value.IntValue -> Value.IntValue(-value.value)
                is Value.FloatValue -> Value.FloatValue(-value.value)
                else -> throw ScriptFailure("INVALID_UNARY", "Unary minus requires a numeric operand.")
            }

            else -> throw ScriptFailure("INVALID_UNARY", "Unsupported unary operator ${expression.operator}.")
        }
    }

    private fun evaluateBinary(expression: Expression.Binary): Value {
        val left = evaluate(expression.left)
        val right = evaluate(expression.right)
        return when (expression.operator) {
            TokenType.PLUS -> when {
                left is Value.StringValue || right is Value.StringValue ->
                    Value.StringValue(left.asString() + right.asString())
                left is Value.FloatValue || right is Value.FloatValue ->
                    Value.FloatValue(left.asDouble() + right.asDouble())
                else -> Value.IntValue(left.asInt() + right.asInt())
            }

            TokenType.MINUS -> numericBinary(left, right) { a, b, float ->
                if (float) Value.FloatValue(a - b) else Value.IntValue((a - b).toInt())
            }

            TokenType.STAR -> numericBinary(left, right) { a, b, float ->
                if (float) Value.FloatValue(a * b) else Value.IntValue((a * b).toInt())
            }

            TokenType.SLASH -> {
                ensure(right.asDouble() != 0.0, "DIVIDE_BY_ZERO", "Division by zero.")
                Value.FloatValue(left.asDouble() / right.asDouble())
            }

            TokenType.PERCENT -> {
                ensure(right.asInt() != 0, "DIVIDE_BY_ZERO", "Modulo by zero.")
                Value.IntValue(left.asInt() % right.asInt())
            }

            TokenType.GREATER -> Value.BooleanValue(left.asDouble() > right.asDouble())
            TokenType.GREATER_EQUAL -> Value.BooleanValue(left.asDouble() >= right.asDouble())
            TokenType.LESS -> Value.BooleanValue(left.asDouble() < right.asDouble())
            TokenType.LESS_EQUAL -> Value.BooleanValue(left.asDouble() <= right.asDouble())
            TokenType.EQUAL_EQUAL -> Value.BooleanValue(left.raw() == right.raw())
            TokenType.BANG_EQUAL -> Value.BooleanValue(left.raw() != right.raw())
            TokenType.AND_AND -> Value.BooleanValue(left.asBoolean() && right.asBoolean())
            TokenType.OR_OR -> Value.BooleanValue(left.asBoolean() || right.asBoolean())
            else -> throw ScriptFailure("INVALID_BINARY", "Unsupported binary operator ${expression.operator}.")
        }
    }

    private fun numericBinary(
        left: Value,
        right: Value,
        operation: (Double, Double, Boolean) -> Value,
    ): Value {
        val useFloat = left is Value.FloatValue || right is Value.FloatValue
        return operation(left.asDouble(), right.asDouble(), useFloat)
    }

    private fun evaluateFunctionCall(expression: Expression.FunctionCall): Value {
        val arguments = expression.arguments.map(::evaluate)
        return when (expression.name) {
            "getVisitorIP" -> Value.StringValue(state.input.visitorIp)
            "getHostIP" -> Value.StringValue(state.input.hostIp)
            "getParameter" -> Value.StringValue(parameter(arguments))
            "isParameterSet" -> Value.BooleanValue(parameter(arguments).isNotEmpty())
            "fetchGetVariable" -> Value.StringValue(queryParameter(arguments))
            "isGetVariableSet" -> Value.BooleanValue(queryParameter(arguments).isNotEmpty())
            "replaceContent" -> {
                ensure(arguments.size == 2, "BAD_ARGUMENT_COUNT", "replaceContent expects 2 arguments.")
                val key = arguments[0].asString()
                val value = arguments[1].asString()
                state.body = state.body.replace("<?$key?>", value)
                Value.IntValue(0)
            }

            "hideStore" -> {
                ensure(arguments.isEmpty(), "BAD_ARGUMENT_COUNT", "hideStore expects no arguments.")
                state.includeStore = false
                Value.IntValue(0)
            }

            "replaceAll" -> {
                ensure(arguments.size == 3, "BAD_ARGUMENT_COUNT", "replaceAll expects 3 arguments.")
                Value.StringValue(
                    arguments[0].asString().replace(
                        oldValue = arguments[1].asString(),
                        newValue = arguments[2].asString(),
                    ),
                )
            }

            "split" -> {
                ensure(arguments.size == 2, "BAD_ARGUMENT_COUNT", "split expects 2 arguments.")
                val delimiter = arguments[1].asString()
                Value.ArrayValue(
                    if (delimiter.isEmpty()) {
                        arguments[0].asString().map { Value.StringValue(it.toString()) }
                    } else {
                        arguments[0].asString().split(delimiter).map { Value.StringValue(it) }
                    },
                )
            }

            "length" -> {
                ensure(arguments.size == 1, "BAD_ARGUMENT_COUNT", "length expects 1 argument.")
                Value.IntValue(
                    when (val argument = arguments[0]) {
                        is Value.StringValue -> argument.value.length
                        is Value.ArrayValue -> argument.values.size
                        else -> argument.asString().length
                    },
                )
            }

            "parseFloat" -> {
                ensure(arguments.size == 1, "BAD_ARGUMENT_COUNT", "parseFloat expects 1 argument.")
                Value.FloatValue(arguments[0].asString().toDoubleOrNull() ?: 0.0)
            }

            "parseInt" -> {
                ensure(arguments.size == 1, "BAD_ARGUMENT_COUNT", "parseInt expects 1 argument.")
                Value.IntValue(arguments[0].asString().toIntOrNull() ?: 0)
            }

            else -> throw ScriptFailure("UNSUPPORTED_FUNCTION", "Unsupported function ${expression.name}.")
        }
    }

    private fun parameter(arguments: List<Value>): String {
        ensure(arguments.size == 1, "BAD_ARGUMENT_COUNT", "Parameter lookup expects 1 argument.")
        return state.input.formParameters[arguments[0].asString()].orEmpty()
    }

    private fun queryParameter(arguments: List<Value>): String {
        ensure(arguments.size == 1, "BAD_ARGUMENT_COUNT", "Query lookup expects 1 argument.")
        return state.input.queryParameters[arguments[0].asString()].orEmpty()
    }

    private fun defaultValueFor(typeName: String): Value {
        return when (typeName) {
            "int" -> Value.IntValue(0)
            "float" -> Value.FloatValue(0.0)
            "string" -> Value.StringValue("")
            "boolean" -> Value.BooleanValue(false)
            else -> Value.IntValue(0)
        }
    }

    private fun ensure(condition: Boolean, code: String, message: String) {
        if (!condition) {
            throw ScriptFailure(code, message)
        }
    }
}

private sealed interface Value {
    fun asBoolean(): Boolean
    fun asString(): String
    fun asDouble(): Double
    fun asInt(): Int
    fun raw(): Any?

    data class IntValue(val value: Int) : Value {
        override fun asBoolean(): Boolean = value != 0
        override fun asString(): String = value.toString()
        override fun asDouble(): Double = value.toDouble()
        override fun asInt(): Int = value
        override fun raw(): Any = value
    }

    data class FloatValue(val value: Double) : Value {
        override fun asBoolean(): Boolean = value != 0.0
        override fun asString(): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
        override fun asDouble(): Double = value
        override fun asInt(): Int = value.toInt()
        override fun raw(): Any = value
    }

    data class StringValue(val value: String) : Value {
        override fun asBoolean(): Boolean = value.isNotEmpty()
        override fun asString(): String = value
        override fun asDouble(): Double = value.toDoubleOrNull() ?: 0.0
        override fun asInt(): Int = value.toIntOrNull() ?: 0
        override fun raw(): Any = value
    }

    data class BooleanValue(val value: Boolean) : Value {
        override fun asBoolean(): Boolean = value
        override fun asString(): String = value.toString()
        override fun asDouble(): Double = if (value) 1.0 else 0.0
        override fun asInt(): Int = if (value) 1 else 0
        override fun raw(): Any = value
    }

    data class ArrayValue(val values: List<Value>) : Value {
        override fun asBoolean(): Boolean = values.isNotEmpty()
        override fun asString(): String = values.joinToString(",") { it.asString() }
        override fun asDouble(): Double = values.size.toDouble()
        override fun asInt(): Int = values.size
        override fun raw(): Any = values.map { it.raw() }
    }
}

private data class Program(
    val functions: List<FunctionDeclaration>,
)

private data class FunctionDeclaration(
    val name: String,
    val body: List<Statement>,
)

private sealed interface Statement {
    data class VariableDeclaration(
        val typeName: String,
        val name: String,
        val initializer: Expression?,
    ) : Statement

    data class Assignment(
        val name: String,
        val expression: Expression,
    ) : Statement

    data class ExpressionStatement(
        val expression: Expression,
    ) : Statement

    data class IfElse(
        val condition: Expression,
        val thenBranch: Statement,
        val elseBranch: Statement?,
    ) : Statement

    data class WhileLoop(
        val condition: Expression,
        val body: Statement,
    ) : Statement

    data class ReturnStatement(
        val expression: Expression?,
    ) : Statement

    data class Block(
        val statements: List<Statement>,
    ) : Statement
}

private sealed interface Expression {
    data class IntLiteral(val value: Int) : Expression
    data class FloatLiteral(val value: Double) : Expression
    data class StringLiteral(val value: String) : Expression
    data class BooleanLiteral(val value: Boolean) : Expression
    data class VariableReference(val name: String) : Expression
    data class Grouping(val expression: Expression) : Expression
    data class Unary(val operator: TokenType, val expression: Expression) : Expression
    data class Binary(val left: Expression, val operator: TokenType, val right: Expression) : Expression
    data class FunctionCall(val name: String, val arguments: List<Expression>) : Expression
}

private class Parser(
    private val tokens: List<Token>,
) {
    private var current = 0

    fun parseProgram(): Program {
        val functions = mutableListOf<FunctionDeclaration>()
        while (!isAtEnd()) {
            functions += parseFunction()
        }
        return Program(functions)
    }

    private fun parseFunction(): FunctionDeclaration {
        if (match(TokenType.INT, TokenType.FLOAT, TokenType.STRING, TokenType.BOOLEAN, TokenType.VOID)) {
            // Return type is intentionally ignored in the phased runtime.
        } else {
            throw error(peek(), "Expected function return type.")
        }
        val name = consume(TokenType.IDENTIFIER, "Expected function name.").lexeme
        consume(TokenType.LEFT_PAREN, "Expected '(' after function name.")
        consume(TokenType.RIGHT_PAREN, "Expected ')' after function name.")
        val block = parseBlock()
        return FunctionDeclaration(name = name, body = block.statements)
    }

    private fun parseStatement(): Statement {
        return when {
            match(TokenType.IF) -> parseIfStatement()
            match(TokenType.WHILE) -> parseWhileStatement()
            match(TokenType.RETURN) -> parseReturnStatement()
            check(TokenType.LEFT_BRACE) -> parseBlock()
            checkAnyTypeKeyword() -> parseVariableDeclaration()
            check(TokenType.IDENTIFIER) && checkNext(TokenType.EQUAL) -> parseAssignment()
            else -> parseExpressionStatement()
        }
    }

    private fun parseVariableDeclaration(): Statement.VariableDeclaration {
        val typeToken = advance()
        val name = consume(TokenType.IDENTIFIER, "Expected variable name.").lexeme
        val initializer = if (match(TokenType.EQUAL)) parseExpression() else null
        consume(TokenType.SEMICOLON, "Expected ';' after variable declaration.")
        return Statement.VariableDeclaration(
            typeName = typeToken.lexeme,
            name = name,
            initializer = initializer,
        )
    }

    private fun parseAssignment(): Statement.Assignment {
        val name = consume(TokenType.IDENTIFIER, "Expected variable name.").lexeme
        consume(TokenType.EQUAL, "Expected '=' in assignment.")
        val expression = parseExpression()
        consume(TokenType.SEMICOLON, "Expected ';' after assignment.")
        return Statement.Assignment(name = name, expression = expression)
    }

    private fun parseIfStatement(): Statement.IfElse {
        consume(TokenType.LEFT_PAREN, "Expected '(' after if.")
        val condition = parseExpression()
        consume(TokenType.RIGHT_PAREN, "Expected ')' after if condition.")
        val thenBranch = parseStatement()
        val elseBranch = if (match(TokenType.ELSE)) parseStatement() else null
        return Statement.IfElse(condition, thenBranch, elseBranch)
    }

    private fun parseWhileStatement(): Statement.WhileLoop {
        consume(TokenType.LEFT_PAREN, "Expected '(' after while.")
        val condition = parseExpression()
        consume(TokenType.RIGHT_PAREN, "Expected ')' after while condition.")
        return Statement.WhileLoop(condition, parseStatement())
    }

    private fun parseReturnStatement(): Statement.ReturnStatement {
        val expression = if (check(TokenType.SEMICOLON)) null else parseExpression()
        consume(TokenType.SEMICOLON, "Expected ';' after return.")
        return Statement.ReturnStatement(expression)
    }

    private fun parseExpressionStatement(): Statement.ExpressionStatement {
        val expression = parseExpression()
        consume(TokenType.SEMICOLON, "Expected ';' after expression.")
        return Statement.ExpressionStatement(expression)
    }

    private fun parseBlock(): Statement.Block {
        consume(TokenType.LEFT_BRACE, "Expected '{'.")
        val statements = mutableListOf<Statement>()
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            statements += parseStatement()
        }
        consume(TokenType.RIGHT_BRACE, "Expected '}' after block.")
        return Statement.Block(statements)
    }

    private fun parseExpression(): Expression = parseOr()

    private fun parseOr(): Expression {
        var expression = parseAnd()
        while (match(TokenType.OR_OR)) {
            val operator = previous().type
            val right = parseAnd()
            expression = Expression.Binary(expression, operator, right)
        }
        return expression
    }

    private fun parseAnd(): Expression {
        var expression = parseEquality()
        while (match(TokenType.AND_AND)) {
            val operator = previous().type
            val right = parseEquality()
            expression = Expression.Binary(expression, operator, right)
        }
        return expression
    }

    private fun parseEquality(): Expression {
        var expression = parseComparison()
        while (match(TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL)) {
            val operator = previous().type
            val right = parseComparison()
            expression = Expression.Binary(expression, operator, right)
        }
        return expression
    }

    private fun parseComparison(): Expression {
        var expression = parseTerm()
        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            val operator = previous().type
            val right = parseTerm()
            expression = Expression.Binary(expression, operator, right)
        }
        return expression
    }

    private fun parseTerm(): Expression {
        var expression = parseFactor()
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val operator = previous().type
            val right = parseFactor()
            expression = Expression.Binary(expression, operator, right)
        }
        return expression
    }

    private fun parseFactor(): Expression {
        var expression = parseUnary()
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            val operator = previous().type
            val right = parseUnary()
            expression = Expression.Binary(expression, operator, right)
        }
        return expression
    }

    private fun parseUnary(): Expression {
        return if (match(TokenType.BANG, TokenType.MINUS)) {
            Expression.Unary(previous().type, parseUnary())
        } else {
            parsePrimary()
        }
    }

    private fun parsePrimary(): Expression {
        if (match(TokenType.FALSE)) return Expression.BooleanLiteral(false)
        if (match(TokenType.TRUE)) return Expression.BooleanLiteral(true)
        if (match(TokenType.STRING_LITERAL)) return Expression.StringLiteral(previous().literal as String)
        if (match(TokenType.INT_LITERAL)) return Expression.IntLiteral(previous().literal as Int)
        if (match(TokenType.FLOAT_LITERAL)) return Expression.FloatLiteral(previous().literal as Double)
        if (match(TokenType.LEFT_PAREN)) {
            val expression = parseExpression()
            consume(TokenType.RIGHT_PAREN, "Expected ')' after expression.")
            return Expression.Grouping(expression)
        }
        if (match(TokenType.IDENTIFIER)) {
            val name = previous().lexeme
            if (match(TokenType.LEFT_PAREN)) {
                val arguments = mutableListOf<Expression>()
                if (!check(TokenType.RIGHT_PAREN)) {
                    do {
                        arguments += parseExpression()
                    } while (match(TokenType.COMMA))
                }
                consume(TokenType.RIGHT_PAREN, "Expected ')' after arguments.")
                return Expression.FunctionCall(name, arguments)
            }
            return Expression.VariableReference(name)
        }
        throw error(peek(), "Expected expression.")
    }

    private fun checkAnyTypeKeyword(): Boolean {
        return check(TokenType.INT) || check(TokenType.FLOAT) || check(TokenType.STRING) || check(TokenType.BOOLEAN)
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw error(peek(), message)
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return type == TokenType.EOF
        return peek().type == type
    }

    private fun checkNext(type: TokenType): Boolean {
        if (current + 1 >= tokens.size) return false
        return tokens[current + 1].type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current += 1
        return previous()
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    private fun peek(): Token = tokens[current]

    private fun previous(): Token = tokens[current - 1]

    private fun error(token: Token, message: String): ScriptFailure {
        return ScriptFailure(
            code = "PARSE_ERROR",
            message = message,
            line = token.line,
            column = token.column,
        )
    }
}

private class Lexer(
    private val source: String,
) {
    private val tokens = mutableListOf<Token>()
    private var start = 0
    private var current = 0
    private var line = 1
    private var column = 1
    private var tokenColumn = 1

    fun tokenize(): List<Token> {
        while (!isAtEnd()) {
            start = current
            tokenColumn = column
            scanToken()
        }
        tokens += Token(TokenType.EOF, "", null, line, column)
        return tokens
    }

    private fun scanToken() {
        when (val character = advance()) {
            '(' -> add(TokenType.LEFT_PAREN)
            ')' -> add(TokenType.RIGHT_PAREN)
            '{' -> add(TokenType.LEFT_BRACE)
            '}' -> add(TokenType.RIGHT_BRACE)
            ',' -> add(TokenType.COMMA)
            ';' -> add(TokenType.SEMICOLON)
            '+' -> add(TokenType.PLUS)
            '-' -> add(TokenType.MINUS)
            '*' -> add(TokenType.STAR)
            '%' -> add(TokenType.PERCENT)
            '!' -> add(if (match('=')) TokenType.BANG_EQUAL else TokenType.BANG)
            '=' -> add(if (match('=')) TokenType.EQUAL_EQUAL else TokenType.EQUAL)
            '<' -> add(if (match('=')) TokenType.LESS_EQUAL else TokenType.LESS)
            '>' -> add(if (match('=')) TokenType.GREATER_EQUAL else TokenType.GREATER)
            '&' -> {
                if (match('&')) add(TokenType.AND_AND) else throw ScriptFailure("LEX_ERROR", "Unexpected '&'.", line, tokenColumn)
            }

            '|' -> {
                if (match('|')) add(TokenType.OR_OR) else throw ScriptFailure("LEX_ERROR", "Unexpected '|'.", line, tokenColumn)
            }

            '/' -> {
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) advance()
                } else {
                    add(TokenType.SLASH)
                }
            }

            ' ', '\r', '\t' -> Unit
            '\n' -> Unit
            '"' -> string()
            else -> when {
                character.isDigit() -> number()
                character.isIdentifierStart() -> identifier()
                else -> throw ScriptFailure("LEX_ERROR", "Unexpected character '$character'.", line, tokenColumn)
            }
        }
    }

    private fun identifier() {
        while (peek().isIdentifierPart()) advance()
        val text = source.substring(start, current)
        add(
            when (text) {
                "int" -> TokenType.INT
                "float" -> TokenType.FLOAT
                "string" -> TokenType.STRING
                "boolean" -> TokenType.BOOLEAN
                "void" -> TokenType.VOID
                "if" -> TokenType.IF
                "else" -> TokenType.ELSE
                "while" -> TokenType.WHILE
                "return" -> TokenType.RETURN
                "true" -> TokenType.TRUE
                "false" -> TokenType.FALSE
                else -> TokenType.IDENTIFIER
            },
        )
    }

    private fun number() {
        while (peek().isDigit()) advance()
        var isFloat = false
        if (peek() == '.' && peekNext().isDigit()) {
            isFloat = true
            advance()
            while (peek().isDigit()) advance()
        }
        val literal = source.substring(start, current)
        if (isFloat) {
            add(TokenType.FLOAT_LITERAL, literal.toDouble())
        } else {
            add(TokenType.INT_LITERAL, literal.toInt())
        }
    }

    private fun string() {
        val builder = StringBuilder()
        while (!isAtEnd() && peek() != '"') {
            val character = advance()
            if (character == '\\' && !isAtEnd()) {
                val escaped = advance()
                builder.append(
                    when (escaped) {
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        '\\' -> '\\'
                        '"' -> '"'
                        else -> escaped
                    },
                )
            } else {
                builder.append(character)
            }
        }
        if (isAtEnd()) {
            throw ScriptFailure("LEX_ERROR", "Unterminated string literal.", line, tokenColumn)
        }
        advance()
        add(TokenType.STRING_LITERAL, builder.toString())
    }

    private fun add(type: TokenType, literal: Any? = null) {
        tokens += Token(
            type = type,
            lexeme = source.substring(start, current),
            literal = literal,
            line = line,
            column = tokenColumn,
        )
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false
        current += 1
        column += 1
        return true
    }

    private fun advance(): Char {
        val character = source[current]
        current += 1
        if (character == '\n') {
            line += 1
            column = 1
        } else {
            column += 1
        }
        return character
    }

    private fun peek(): Char = if (isAtEnd()) '\u0000' else source[current]

    private fun peekNext(): Char = if (current + 1 >= source.length) '\u0000' else source[current + 1]

    private fun isAtEnd(): Boolean = current >= source.length
}

private data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any?,
    val line: Int,
    val column: Int,
)

private enum class TokenType {
    LEFT_PAREN,
    RIGHT_PAREN,
    LEFT_BRACE,
    RIGHT_BRACE,
    COMMA,
    SEMICOLON,
    PLUS,
    MINUS,
    STAR,
    SLASH,
    PERCENT,
    BANG,
    BANG_EQUAL,
    EQUAL,
    EQUAL_EQUAL,
    GREATER,
    GREATER_EQUAL,
    LESS,
    LESS_EQUAL,
    AND_AND,
    OR_OR,
    IDENTIFIER,
    STRING_LITERAL,
    INT_LITERAL,
    FLOAT_LITERAL,
    INT,
    FLOAT,
    STRING,
    BOOLEAN,
    VOID,
    IF,
    ELSE,
    WHILE,
    RETURN,
    TRUE,
    FALSE,
    EOF,
}

private class ScriptFailure(
    val code: String,
    override val message: String,
    val line: Int? = null,
    val column: Int? = null,
) : RuntimeException(message)

private class ReturnSignal(
    val value: Value,
) : RuntimeException(null, null, false, false)

private fun Char.isIdentifierStart(): Boolean = isLetter() || this == '_'

private fun Char.isIdentifierPart(): Boolean = isIdentifierStart() || isDigit()
