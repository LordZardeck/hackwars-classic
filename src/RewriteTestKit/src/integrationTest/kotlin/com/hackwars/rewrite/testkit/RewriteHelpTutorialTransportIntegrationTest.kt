package com.hackwars.rewrite.testkit

import com.hackwars.rewrite.protocol.ProtocolTimeoutPolicy
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.CommandEnvelope
import hackwars.rewrite.v1.FrameEnvelope
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.URLClassLoader
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteHelpTutorialTransportIntegrationTest {
    private val runtimeClassLoader: ClassLoader by lazy {
        val repoRoot = locateRepoRoot()
        val urls = listOf(
            repoRoot.resolve("src/RewriteGameCore/build/classes/kotlin/main"),
            repoRoot.resolve("src/RewriteGameServer/build/classes/kotlin/main"),
        ).filter { it.exists() }.map { it.toURI().toURL() }.toTypedArray()
        URLClassLoader.newInstance(urls, this::class.java.classLoader)
    }

    @Test
    fun retainedHelpTutorialTransportWorksThroughRealGameHarness() = runTest {
        val fixture = createFixture()
        val connection = fixture.authenticatedConnection()

        connection.send(
            frameCommand(
                commandId = "help-topics",
                commandName = "requesthelptopiclist",
                payloadJson = """{}""",
                expectsResponse = true,
            ),
        )
        val helpTopicsJson = commandResponseJson(connection.awaitFrame())
        assertTrue(helpTopicsJson.contains("\"topicGroup\":\"Tutorials\""))
        assertTrue(helpTopicsJson.contains("\"name\":\"First Attack\""))
        assertTrue(helpTopicsJson.contains("\"targetUrl\":\"http://203.0.113.210/\""))

        connection.send(
            frameCommand(
                commandId = "tutorial-page",
                commandName = "requesttutorial",
                payloadJson = """{}""",
                expectsResponse = true,
            ),
        )
        val tutorialJson = commandResponseJson(connection.awaitFrame())
        assertTrue(tutorialJson.contains("\"title\":\"First Attack\""))
        assertTrue(tutorialJson.contains("In order to do this, go to Applications"))

        connection.send(
            frameCommand(
                commandId = "help-webpage",
                commandName = "requestwebpage",
                payloadJson = """{"targetIp":"203.0.113.210","sourceIp":"192.0.2.10","parameters":{}}""",
                expectsResponse = true,
            ),
        )
        val webpageJson = commandResponseJson(connection.awaitFrame())
        assertTrue(webpageJson.contains("\"title\":\"First Attack\""))
        assertTrue(webpageJson.contains("Visit the Store to buy a basic banking binary."))
    }

    private fun TestScope.createFixture(): Fixture {
        val localId = gameStateId("192.0.2.10")
        val repository = inMemoryComputerStateRepository(mapOf(localId to localState("192.0.2.10")))
        val interests = inMemoryInterestRegistry()
        val searchCatalogRepository = newInstance(
            "com.hackwars.rewrite.gamecore.InMemorySearchCatalogRepository",
            emptyList<Any?>(),
        )
        val networkDirectoryRepository = invokeCompanion(
            "com.hackwars.rewrite.gamecore.InMemoryNetworkDirectoryRepository",
            "defaultWorld",
            "1",
        )
        val retainedHelpTutorialRepository = newInstance(
            "com.hackwars.rewrite.gamecore.DefaultRetainedHelpTutorialRepository",
        )
        val playerProfileRepository = newKotlinInstance(
            "com.hackwars.rewrite.gamecore.InMemoryPersonalSettingsProfileRepository",
            arrayOf(emptyMap<Any, Any>()),
            defaultMask = 1,
        )
        val ftpPasswordRepository = newKotlinInstance(
            "com.hackwars.rewrite.gamecore.InMemoryFtpPasswordRepository",
            arrayOf(emptyMap<Any, Any>()),
            defaultMask = 1,
        )
        val dispatcher = newKotlinInstance(
            "com.hackwars.rewrite.gamecore.DefaultCommandDispatcher",
            arrayOf(repository, interests, null, null, null, null, null),
            defaultMask = maskFor(2, 3, 4, 5, 6),
        )
        val adapter = newKotlinInstance(
            "com.hackwars.rewrite.gameserver.RewriteGameProtocolAdapter",
            arrayOf(
                dispatcher,
                interests,
                null,
                null,
                null,
                null,
                networkDirectoryRepository,
                searchCatalogRepository,
                retainedHelpTutorialRepository,
                playerProfileRepository,
                ftpPasswordRepository,
                null,
                null,
                null,
                null,
            ),
            defaultMask = maskFor(2, 3, 4, 5, 11, 12, 13, 14),
        )
        val harnessAdapter = RealGameHarnessAdapter(adapter)
        val harness = InMemoryRewriteServiceHarness(
            adapter = harnessAdapter,
            verifier = FakeSessionTicketVerifier(
                catalog = FakeSessionCatalog(
                    accounts = listOf(
                        FakePlayerAccount(
                            playFabId = "PF-LOCALUSER",
                            playerIp = "192.0.2.10",
                            sessionTicket = "SESSION-LOCALUSER",
                        ),
                    ),
                ),
                clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
            ),
            scope = backgroundScope,
            timeoutPolicy = ProtocolTimeoutPolicy(
                authTimeout = 5.seconds,
                idleTimeout = 45.seconds,
            ),
            clock = { Instant.ofEpochMilli(testScheduler.currentTime) },
        )
        harnessAdapter.attachHarness(harness)
        return Fixture(harness)
    }

    private suspend fun Fixture.authenticatedConnection(): InMemoryClientConnection {
        val connection = harness.connect()
        connection.send(
            RewriteFrames.authRequest(
                service = RewriteService.GAME,
                sessionTicket = "SESSION-LOCALUSER",
                clientBuild = "rewrite-it",
                playFabIdHint = "PF-LOCALUSER",
                requestedIp = "192.0.2.10",
            ),
        )
        connection.awaitFrame()
        connection.awaitFrame()
        connection.drainFrames()
        return connection
    }

    private fun commandResponseJson(frame: FrameEnvelope): String {
        return frame.command_response!!.payload.toByteArray().toString(Charsets.UTF_8)
    }

    private fun frameCommand(
        commandId: String,
        commandName: String,
        payloadJson: String,
        expectsResponse: Boolean,
    ): FrameEnvelope {
        return RewriteFrames.command(
            commandId = commandId,
            commandName = commandName,
            payload = payloadJson.encodeToByteArray(),
            expectsResponse = expectsResponse,
        )
    }

    private fun gameStateId(value: String): Any {
        return newInstance("com.hackwars.rewrite.gamecore.GameStateId", value)
    }

    private fun localState(playerIp: String): Any {
        return invokeCompanion(
            "com.hackwars.rewrite.gamecore.ComputerState",
            "empty-bO-giLg",
            playerIp,
            "PF-LOCALUSER",
            playerIp,
            "",
            false,
        )
    }

    private fun inMemoryComputerStateRepository(seed: Map<Any, Any>): Any {
        return newKotlinInstance(
            "com.hackwars.rewrite.gamecore.InMemoryComputerStateRepository",
            arrayOf(seed),
            defaultMask = 0,
        )
    }

    private fun inMemoryInterestRegistry(): Any {
        return newInstance("com.hackwars.rewrite.gamecore.InMemoryInterestRegistry")
    }

    private fun invokeCompanion(
        className: String,
        methodName: String,
        vararg args: Any?,
    ): Any {
        val companion = companionObject(className)
        val method = companion.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterCount == args.size
        } ?: error("No companion method named $methodName with ${args.size} parameters found for $className")
        method.isAccessible = true
        return method.invoke(companion, *args)
    }

    private fun companionObject(className: String): Any {
        val clazz = loadRuntimeClass(className)
        val field = clazz.getDeclaredField("Companion")
        field.isAccessible = true
        return field.get(null)
    }

    private fun newInstance(className: String, vararg args: Any?): Any {
        val clazz = loadRuntimeClass(className)
        val ctor = clazz.declaredConstructors.firstOrNull { it.parameterCount == args.size }
            ?: error("No constructor with ${args.size} parameters found for $className")
        ctor.isAccessible = true
        return ctor.newInstance(*args)
    }

    private fun newKotlinInstance(
        className: String,
        args: Array<Any?>,
        defaultMask: Int,
    ): Any {
        val clazz = loadRuntimeClass(className)
        val ctor = clazz.declaredConstructors.firstOrNull {
            it.parameterTypes.lastOrNull()?.name == "kotlin.jvm.internal.DefaultConstructorMarker"
        } ?: error("No Kotlin synthetic constructor found for $className")
        ctor.isAccessible = true
        val fullArgs = args.toMutableList()
        fullArgs += defaultMask
        fullArgs += null
        return ctor.newInstance(*fullArgs.toTypedArray())
    }

    private fun loadRuntimeClass(className: String): Class<*> {
        return try {
            Class.forName(className, true, runtimeClassLoader)
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException("Missing runtime class: $className", e)
        }
    }

    private fun maskFor(vararg defaultedIndices: Int): Int {
        return defaultedIndices.fold(0) { acc, index -> acc or (1 shl index) }
    }

    private fun locateRepoRoot(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        while (current.parentFile != null) {
            val candidate = current.resolve("src/RewriteGameCore/build/classes/kotlin/main")
            if (candidate.exists()) {
                return current
            }
            current = current.parentFile
        }
        error("Unable to locate repository root from ${File(System.getProperty("user.dir")).absolutePath}")
    }

    private inner class RealGameHarnessAdapter(
        private val adapter: Any,
    ) : RewriteServiceAdapter {
        override val service: RewriteService = RewriteService.GAME

        private lateinit var harness: InMemoryRewriteServiceHarness

        fun attachHarness(harness: InMemoryRewriteServiceHarness) {
            this.harness = harness
        }

        override suspend fun onSessionStarted(session: InMemoryAuthenticatedSession): List<FrameEnvelope> {
            val method = adapter.javaClass.methods.first {
                it.name == "onSessionStarted" && it.parameterCount == 3
            }
            @Suppress("UNCHECKED_CAST")
            return invokeSuspend(
                method = method,
                target = adapter,
                *arrayOf(session.toGameSession(), gameConnectionTransportProxy()),
            ) as List<FrameEnvelope>
        }

        override suspend fun onCommand(
            session: InMemoryAuthenticatedSession,
            command: CommandEnvelope,
        ): List<FrameEnvelope> {
            val method = adapter.javaClass.methods.first {
                it.name == "onCommand" && it.parameterCount == 4
            }
            @Suppress("UNCHECKED_CAST")
            return invokeSuspend(
                method = method,
                target = adapter,
                *arrayOf(session.toGameSession(), command, gameConnectionTransportProxy()),
            ) as List<FrameEnvelope>
        }

        private fun gameConnectionTransportProxy(): Any {
            val transportInterface = runtimeClassLoader.loadClass(
                "com.hackwars.rewrite.gameserver.GameConnectionTransport",
            )
            return Proxy.newProxyInstance(
                runtimeClassLoader,
                arrayOf(transportInterface),
            ) { proxy, method, args ->
                when (method.name) {
                    "send" -> {
                        val connectionId = args?.getOrNull(0) as String
                        val frame = args[1] as FrameEnvelope
                        @Suppress("UNCHECKED_CAST")
                        val continuation = args[2] as Continuation<Any?>
                        runBlocking {
                            harness.push(connectionId, frame)
                        }
                        continuation.resume(Unit)
                        COROUTINE_SUSPENDED
                    }

                    "toString" -> "RealGameHarnessTransportProxy"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> null
                }
            }
        }

        private suspend fun invokeSuspend(
            method: Method,
            target: Any,
            vararg args: Any?,
        ): Any? {
            return kotlinx.coroutines.suspendCancellableCoroutine { outer ->
                val continuation = object : Continuation<Any?> {
                    override val context = outer.context

                    override fun resumeWith(result: Result<Any?>) {
                        result.fold(
                            onSuccess = { value -> outer.resume(value) },
                            onFailure = { error -> outer.resumeWithException(error) },
                        )
                    }
                }
                try {
                    val result = method.invoke(target, *args, continuation)
                    if (result !== COROUTINE_SUSPENDED) {
                        outer.resume(result as Any?)
                    }
                } catch (error: Throwable) {
                    outer.resumeWithException(error.cause ?: error)
                }
            }
        }

        private fun InMemoryAuthenticatedSession.toGameSession(): Any {
            return newInstance(
                "com.hackwars.rewrite.gameserver.AuthenticatedGameSession",
                connectionId,
                verifiedSession.playFabId,
                verifiedSession.playerIp,
            )
        }
    }

    private data class Fixture(
        val harness: InMemoryRewriteServiceHarness,
    )
}
