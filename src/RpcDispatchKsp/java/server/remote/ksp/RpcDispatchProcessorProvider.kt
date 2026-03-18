package server.remote.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.validate
import java.io.OutputStreamWriter

private const val RPC_HANDLER_ANNOTATION = "server.remote.RpcHandler"
private const val REMOTE_CALL_HANDLER_TYPE = "server.remote.RemoteCallHandler"
private const val GENERATED_PACKAGE = "server.remote.generated"
private const val GENERATED_OBJECT = "GeneratedRemoteCallRegistry"

class RpcDispatchProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return RpcDispatchProcessor(environment.codeGenerator, environment.logger)
    }
}

private class RpcDispatchProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) {
            return emptyList()
        }

        val deferred = mutableListOf<KSAnnotated>()
        val handlerInfos = mutableListOf<HandlerInfo>()

        for (symbol in resolver.getSymbolsWithAnnotation(RPC_HANDLER_ANNOTATION)) {
            if (!symbol.validate()) {
                deferred += symbol
                continue
            }

            val declaration = symbol as? KSClassDeclaration
            if (declaration == null) {
                logger.error("@RpcHandler can only target classes/objects", symbol)
                continue
            }

            if (declaration.classKind != ClassKind.OBJECT) {
                logger.error("@RpcHandler handlers must be Kotlin objects", declaration)
                continue
            }

            if (!declaration.implementsRemoteCallHandler()) {
                logger.error("@RpcHandler ${declaration.qualifiedName?.asString()} must implement $REMOTE_CALL_HANDLER_TYPE", declaration)
                continue
            }

            val functionName = declaration.rpcFunctionName()
            if (functionName == null || functionName.isBlank()) {
                logger.error("@RpcHandler function value must be non-empty for ${declaration.qualifiedName?.asString()}", declaration)
                continue
            }

            val qualifiedName = declaration.qualifiedName?.asString()
            if (qualifiedName == null) {
                logger.error("Unable to resolve qualified name for @RpcHandler declaration", declaration)
                continue
            }

            handlerInfos += HandlerInfo(
                function = functionName,
                qualifiedName = qualifiedName,
                sourceFile = declaration.containingFile
            )
        }

        if (deferred.isNotEmpty()) {
            return deferred
        }

        val duplicates = handlerInfos.groupBy { it.function }.filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            for ((function, handlers) in duplicates) {
                logger.error(
                    "Duplicate @RpcHandler registrations for function '$function': " +
                        handlers.joinToString { it.qualifiedName }
                )
            }
            return emptyList()
        }

        generateRegistry(handlerInfos.sortedBy { it.function })
        generated = true
        return emptyList()
    }

    private fun generateRegistry(handlerInfos: List<HandlerInfo>) {
        val sources = handlerInfos.mapNotNull { it.sourceFile }.toTypedArray()
        val dependencies = Dependencies(aggregating = true, *sources)
        val file = codeGenerator.createNewFile(dependencies, GENERATED_PACKAGE, GENERATED_OBJECT)

        OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
            writer.appendLine("package $GENERATED_PACKAGE")
            writer.appendLine()
            writer.appendLine("import assignments.RemoteFunctionCall")
            writer.appendLine("import server.remote.RemoteCallContext")
            writer.appendLine("import server.remote.RemoteCallHandler")
            writer.appendLine()
            writer.appendLine("object $GENERATED_OBJECT {")
            writer.appendLine("    private val handlers: Map<String, RemoteCallHandler> = mapOf(")
            for ((index, handler) in handlerInfos.withIndex()) {
                val suffix = if (index == handlerInfos.lastIndex) "" else ","
                writer.appendLine("        \"${handler.function.escapeKotlinString()}\" to ${handler.qualifiedName}$suffix")
            }
            writer.appendLine("    )")
            writer.appendLine()
            writer.appendLine("    fun dispatch(rfc: RemoteFunctionCall, context: RemoteCallContext): Boolean {")
            writer.appendLine("        val function = rfc.function ?: return false")
            writer.appendLine("        val handler = handlers[function] ?: return false")
            writer.appendLine("        handler.handle(rfc, context)")
            writer.appendLine("        return true")
            writer.appendLine("    }")
            writer.appendLine("}")
        }
    }

    private fun KSClassDeclaration.implementsRemoteCallHandler(): Boolean {
        return superTypes.any { superType ->
            val resolved = superType.resolve()
            resolved.declaration.qualifiedName?.asString() == REMOTE_CALL_HANDLER_TYPE
        }
    }

    private fun KSClassDeclaration.rpcFunctionName(): String? {
        val annotation = annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == RPC_HANDLER_ANNOTATION
        } ?: return null

        return annotation.arguments
            .firstOrNull { it.name?.asString() == "function" }
            ?.value as? String
    }
}

private data class HandlerInfo(
    val function: String,
    val qualifiedName: String,
    val sourceFile: KSFile?
)

private fun String.escapeKotlinString(): String {
    return replace("\\", "\\\\").replace("\"", "\\\"")
}
