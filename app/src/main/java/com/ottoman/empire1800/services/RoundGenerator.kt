package com.ottoman.empire1800.services

import com.ottoman.empire1800.engine.Calibrator
import com.ottoman.empire1800.engine.PlanFormatException
import com.ottoman.empire1800.engine.RoundParser
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.RoundPlan
import kotlinx.coroutines.delay

typealias AiCall = suspend (String) -> String

/** يطلب من الذكاء الاصطناعي جولة، ويتحقق منها ويعايرها، ويعيد المحاولة عند الخطأ. */
class RoundGenerator(
    private val call: AiCall,
    private val maxAttempts: Int = 3,
    private val maxFormatFailures: Int = 2,
    private val wait: suspend (Long) -> Unit = { ms -> delay(ms) },
    private val onAttempt: ((attempt: Int, problem: String?) -> Unit)? = null,
) {
    suspend fun generate(s: GameState): RoundPlan {
        var problem: String? = null
        var lastAi: AiException? = null
        var formatFailures = 0
        for (attempt in 1..maxAttempts) {
            onAttempt?.invoke(attempt, problem)
            val prompt = buildRoundPrompt(s, problem = problem)
            try {
                val text = call(prompt)
                val raw = RoundParser.parse(text)
                return Calibrator.calibrate(raw, s)
            } catch (e: PlanFormatException) {
                problem = e.message
                lastAi = null
                formatFailures += 1
                if (formatFailures >= maxFormatFailures) break
            } catch (e: AiException) {
                if (!e.retryable) throw e
                lastAi = e
                if (attempt < maxAttempts) wait(3000L * attempt)
            }
        }
        if (lastAi != null) throw lastAi
        throw AiException(
            "كتب الذكاء الاصطناعي جولة غير صالحة $formatFailures مرات ($problem). حاول مرة أخرى أو غيّر النموذج.",
        )
    }
}
