package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.HttpHookExecutionInput
import com.hackwars.rewrite.hackscript.HttpHookExecutionResult
import com.hackwars.rewrite.hackscript.HttpHookScriptEngine
import org.slf4j.LoggerFactory

class HackScriptHttpHookRuntime(
    private val engine: HttpHookScriptEngine = HttpHookScriptEngine(),
) : HttpHookRuntime {
    override suspend fun onEnter(request: HttpHookRequest): HttpHookExecutionResult? {
        return executeSlot(
            request = request,
            slot = ProgramScriptSlot.ENTER,
            body = request.targetState.website.body,
            includeStore = true,
        )
    }

    override suspend fun onSubmit(request: HttpHookRequest): HttpHookExecutionResult? {
        val afterSubmit = executeSlot(
            request = request,
            slot = ProgramScriptSlot.SUBMIT,
            body = request.targetState.website.body,
            includeStore = true,
        ) ?: return null
        return executeSlot(
            request = request,
            slot = ProgramScriptSlot.ENTER,
            body = afterSubmit.body,
            includeStore = afterSubmit.includeStore,
        )
    }

    override suspend fun onExit(request: HttpHookRequest) {
        executeSlot(
            request = request,
            slot = ProgramScriptSlot.EXIT,
            body = request.targetState.website.body,
            includeStore = true,
        )
    }

    private fun executeSlot(
        request: HttpHookRequest,
        slot: ProgramScriptSlot,
        body: String,
        includeStore: Boolean,
    ): HttpHookExecutionResult? {
        val script = request.installedApplication.scriptBundle?.script(slot).orEmpty()
        if (script.isBlank()) {
            return HttpHookExecutionResult(
                body = body,
                includeStore = includeStore,
            )
        }

        val outcome = engine.execute(
            script = script,
            input = HttpHookExecutionInput(
                visitorIp = request.sourceStateId.value,
                hostIp = request.targetStateId.value,
                initialBody = body,
                initialIncludeStore = includeStore,
                queryParameters = request.queryParameters,
                formParameters = request.formParameters,
            ),
        )
        if (outcome.result != null) {
            return outcome.result
        }

        logger.warn(
            "Rewrite HTTP hook {} failed for source={} target={}: {}",
            slot.name.lowercase(),
            request.sourceStateId.value,
            request.targetStateId.value,
            outcome.diagnostics.joinToString { "${it.code}:${it.message}" },
        )
        return null
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(HackScriptHttpHookRuntime::class.java)
    }
}
