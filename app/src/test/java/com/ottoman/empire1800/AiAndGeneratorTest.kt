package com.ottoman.empire1800

import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.services.AiClient
import com.ottoman.empire1800.services.AiException
import com.ottoman.empire1800.services.AiProvider
import com.ottoman.empire1800.services.AiSettings
import com.ottoman.empire1800.services.RoundGenerator
import com.ottoman.empire1800.services.aiProviders
import com.ottoman.empire1800.services.buildRoundPrompt
import com.ottoman.empire1800.services.parseChatCompletionText
import com.ottoman.empire1800.services.parseGeminiText
import com.ottoman.empire1800.support.FakeAi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class StubProvider(
    override val id: String = "stub",
    override val name: String = "Stub",
    override val defaultModel: String = "m1",
    override val fallbackModels: List<String> = listOf("m2"),
    override val supportsSearch: Boolean = true,
    private val handler: (model: String, search: Boolean) -> String,
) : AiProvider {
    val calls = mutableListOf<Pair<String, Boolean>>()

    override suspend fun generate(
        prompt: String,
        apiKey: String,
        model: String,
        json: Boolean,
        maxTokens: Int,
        search: Boolean,
    ): String {
        calls.add(model to search)
        return handler(model, search)
    }
}

class AiAndGeneratorTest {

    @Test
    fun parsesGeminiAndChatCompletionResponses() {
        val geminiJson = """{"candidates":[{"content":{"parts":[{"text":"{\"ok\":true}"}]}}]}"""
        assertEquals("""{"ok":true}""", parseGeminiText(geminiJson))

        val groqJson = """{"choices":[{"message":{"content":" {\"ok\":1} "}}]}"""
        assertEquals("""{"ok":1}""", parseChatCompletionText(groqJson))

        assertTrue(aiProviders.map { it.id }.containsAll(listOf("gemini", "groq")))
    }

    @Test
    fun aiClientFallsBackToNextModelAndRetriesWithoutSearchOn400() = runTest {
        val p1 = StubProvider { _, search ->
            if (search) throw AiException("bad", status = 400)
            "ok-no-search"
        }
        val c1 = AiClient(p1, "k", listOf("m1", "m2"), search = true)
        assertEquals("ok-no-search", c1.generateJson("prompt"))
        assertEquals(listOf("m1" to true, "m1" to false), p1.calls)

        val p2 = StubProvider { model, _ ->
            if (model == "m1") throw AiException("429", retryable = true, status = 429)
            "ok-m2"
        }
        val c2 = AiClient(p2, "k", listOf("m1", "m2"), search = false)
        assertEquals("ok-m2", c2.generateJson("prompt"))
        assertEquals(listOf("m1" to false, "m2" to false), p2.calls)

        val p3 = StubProvider { _, _ ->
            throw AiException("401", retryable = false, modelProblem = false, status = 401)
        }
        val c3 = AiClient(p3, "k", listOf("m1", "m2"), search = false)
        var authFailed = false
        try {
            c3.generateJson("prompt")
        } catch (e: AiException) {
            authFailed = e.status == 401
        }
        assertTrue(authFailed)
        assertEquals(listOf("m1" to false), p3.calls)
    }

    @Test
    fun aiClientFromSettingsBuildsOrderedUniqueModels() {
        val gCustom = AiClient.fromSettings(AiSettings(provider = "gemini", model = "custom-model", apiKey = "abc"))
        assertEquals(
            listOf("custom-model", "gemini-4-hydrogen", "gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.5-flash"),
            gCustom.models,
        )

        val gDefault = AiClient.fromSettings(AiSettings(provider = "gemini", model = "", apiKey = "abc"))
        assertEquals(
            listOf("gemini-4-hydrogen", "gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.5-flash"),
            gDefault.models,
        )

        val qDefault = AiClient.fromSettings(AiSettings(provider = "groq", model = "", apiKey = "gsk_123"))
        assertEquals(
            listOf("openai/gpt-oss-120b", "llama-3.3-70b-versatile", "openai/gpt-oss-20b", "llama-3.1-8b-instant"),
            qDefault.models,
        )
    }

    @Test
    fun roundPromptStaticPrefixAndReasonableLength() {
        val s1 = GameState(round = 0)
        val s2 = GameState(round = 40, debt = 25_000_000.0, foreignDebt = 10_000_000.0)
        val p1 = buildRoundPrompt(s1)
        val p2 = buildRoundPrompt(s2)
        assertTrue(p1.length < 9000)
        assertTrue(p2.length < 11000)
        assertEquals(p1.substring(0, 200), p2.substring(0, 200))
        assertTrue(p2.contains("الدين الخارجي"))
        assertTrue(buildRoundPrompt(s1, problem = "نقص قرار").contains("نقص قرار"))
    }

    @Test
    fun roundGeneratorRetriesFormatAndTransientAiErrors() = runTest {
        var n = 0
        val valid = FakeAi(seed = 7)
        val gen = RoundGenerator(
            call = {
                n++
                when (n) {
                    1 -> "not-json"
                    2 -> throw AiException("rate", retryable = true)
                    else -> valid.call("")
                }
            },
            wait = { _ -> },
        )
        val plan = gen.generate(GameState())
        assertEquals(3, n)
        assertEquals(3, plan.decisions.size)
        assertFalse(plan.headline.isEmpty())
    }
}
