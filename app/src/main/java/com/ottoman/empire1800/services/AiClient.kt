package com.ottoman.empire1800.services

/** يرسل الطلب للمزود المختار، ويجرب نماذج بديلة تلقائيا عند الضغط أو توقف النموذج. */
class AiClient(
    val provider: AiProvider,
    val apiKey: String,
    val models: List<String>,
    val search: Boolean = false,
) {
    init {
        require(models.isNotEmpty())
    }

    suspend fun generateJson(prompt: String): String = run(prompt, json = true, search = search)

    suspend fun generateText(prompt: String): String = run(prompt, json = false, search = false)

    private suspend fun run(prompt: String, json: Boolean, search: Boolean): String {
        var last: AiException? = null
        var useSearch = search
        for (model in models) {
            try {
                return once(prompt, model, json, useSearch)
            } catch (e: AiException) {
                if (useSearch && e.status == 400) {
                    useSearch = false
                    try {
                        return once(prompt, model, json, false)
                    } catch (e2: AiException) {
                        last = e2
                        if (!e2.retryable && !e2.modelProblem) throw e2
                        continue
                    }
                }
                last = e
                if (!e.retryable && !e.modelProblem) throw e
            }
        }
        throw last ?: AiException("تعذر الاتصال بالذكاء الاصطناعي.")
    }

    private suspend fun once(prompt: String, model: String, json: Boolean, useSearch: Boolean): String =
        provider.generate(
            prompt = prompt,
            apiKey = apiKey,
            model = model,
            json = json,
            search = useSearch,
        )

    companion object {
        fun fromSettings(s: AiSettings): AiClient {
            val provider = aiProviderById(s.provider)
            val models = mutableListOf(s.effectiveModel)
            for (m in listOf(provider.defaultModel) + provider.fallbackModels) {
                if (!models.contains(m)) models.add(m)
            }
            return AiClient(
                provider = provider,
                apiKey = s.apiKey,
                models = models,
                search = s.useSearch && provider.supportsSearch,
            )
        }
    }
}
