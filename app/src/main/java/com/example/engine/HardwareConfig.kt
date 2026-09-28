package com.example.engine

data class HardwareConfig(
    val useGpuAcceleration: Boolean = true,
    val computeDelegate: ComputeDelegate = ComputeDelegate.GPU_VULKAN,
    val cpuThreads: Int = 4,
    val contextWindowSize: Int = 2048,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 1024,
    val systemPrompt: String = "Sen yerel olarak GPU hızlandırmasıyla çalışan çevrimdışı bir AI konsol agentısın. Hızlı, net, mantıklı ve analitik cevaplar üretirsin."
)

enum class ComputeDelegate(val label: String, val isGpu: Boolean) {
    GPU_VULKAN("Vulkan GPU (Önerilen)", true),
    GPU_OPENCL("OpenCL GPU Compute", true),
    NNAPI("Android NNAPI Accelerator", true),
    CPU_MULTITHREAD("CPU Multi-thread (AVX/NEON)", false)
}
