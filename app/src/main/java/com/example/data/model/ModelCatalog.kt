package com.example.data.model

object ModelCatalog {
    fun getInitialModels(): List<LlmModelEntity> {
        return listOf(
            LlmModelEntity(
                id = "smollm_135m_q4",
                name = "SmolLM 135M Instruct",
                fileName = "smollm-135m-instruct-q4_k_m.gguf",
                parameterSize = "135M",
                quantization = "Q4_K_M",
                totalSizeBytes = 84_000_000L, // ~84 MB
                ramRequiredMb = 240,
                gpuAccelerated = true,
                description = "Ultra hafif, ultra hızlı mobil LLM. Düşük kaynak tüketimi ve anında yanıt.",
                downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM-135M-Instruct-GGUF/resolve/main/smollm-135m-instruct-q4_k_m.gguf",
                isSelected = true
            ),
            LlmModelEntity(
                id = "qwen25_05b_q4",
                name = "Qwen 2.5 0.5B Instruct",
                fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                parameterSize = "0.5B",
                quantization = "Q4_K_M",
                totalSizeBytes = 398_000_000L, // ~398 MB
                ramRequiredMb = 580,
                gpuAccelerated = true,
                description = "Yüksek akıl yürütme ve çoklu dil yeteneği. Mobil GPU için optimize edilmiş mimari.",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                isSelected = false
            ),
            LlmModelEntity(
                id = "tinyllama_11b_q4",
                name = "TinyLlama 1.1B Chat",
                fileName = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
                parameterSize = "1.1B",
                quantization = "Q4_K_M",
                totalSizeBytes = 669_000_000L, // ~669 MB
                ramRequiredMb = 950,
                gpuAccelerated = true,
                description = "Android cihazlarda standart popüler lokal model. Hızlı çıkarım ve tutarlı konuşma.",
                downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
                isSelected = false
            ),
            LlmModelEntity(
                id = "deepseek_r1_qwen_15b",
                name = "DeepSeek-R1 Distill 1.5B",
                fileName = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                parameterSize = "1.5B",
                quantization = "Q4_K_M",
                totalSizeBytes = 1_120_000_000L, // ~1.1 GB
                ramRequiredMb = 1450,
                gpuAccelerated = true,
                description = "Lokal zincirleme düşünme (Chain-of-Thought) ve mantıksal problem çözme ajanı.",
                downloadUrl = "https://huggingface.co/unsloth/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                isSelected = false
            ),
            LlmModelEntity(
                id = "phi2_27b_q4",
                name = "Microsoft Phi-2 2.7B",
                fileName = "phi-2.Q4_K_M.gguf",
                parameterSize = "2.7B",
                quantization = "Q4_K_M",
                totalSizeBytes = 1_680_000_000L, // ~1.68 GB
                ramRequiredMb = 2100,
                gpuAccelerated = true,
                description = "Kodlama ve mantık odaklı kompakt yapay zeka. Üst düzey cihazlar ve GPU için ideal.",
                downloadUrl = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf",
                isSelected = false
            )
        )
    }
}
