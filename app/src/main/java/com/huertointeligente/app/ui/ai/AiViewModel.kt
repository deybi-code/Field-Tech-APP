package com.huertointeligente.app.ui.ai

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huertointeligente.app.data.model.Huerto
import com.huertointeligente.app.data.model.SensorReading
import com.huertointeligente.app.domain.Crops
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AiRepository()
    
    private val _messages = MutableStateFlow<List<AiMessage>>(
        listOf(
            AiMessage(
                sender = AiMessage.Sender.AI,
                text = "🤖 ¡Hola! Soy el Chatbot Agrícola de FieldTech 🌾. Estoy conectado en tiempo real a los sensores de tu huerto para ayudarte con el riego, clima, control de plagas y recomendaciones de cultivo. ¿En qué te puedo ayudar hoy?"
            )
        )
    )
    val messages: StateFlow<List<AiMessage>> = _messages.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun sendMessage(
        prompt: String,
        imageBitmap: Bitmap? = null,
        huerto: Huerto?,
        reading: SensorReading?
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty() && imageBitmap == null) return

        val userMsg = AiMessage(
            sender = AiMessage.Sender.USER,
            text = cleanPrompt.ifEmpty { "Analiza la muestra de mi cultivo" },
            imageBitmap = imageBitmap
        )
        _messages.value = _messages.value + userMsg
        _loading.value = true

        val crop = Crops.forHuerto(huerto)
        val cropName = crop.id.replaceFirstChar { it.uppercase() }

        viewModelScope.launch {
            val result = repository.sendMessage(
                userText = cleanPrompt,
                imageBitmap = imageBitmap,
                huerto = huerto,
                reading = reading,
                cropName = cropName
            )
            _loading.value = false
            result.onSuccess { replyText ->
                _messages.value = _messages.value + AiMessage(sender = AiMessage.Sender.AI, text = replyText)
            }.onFailure { err ->
                _messages.value = _messages.value + AiMessage(
                    sender = AiMessage.Sender.AI,
                    text = "⚠️ Error de conexión con Groq API: ${err.message}. Verifica tu API Key en AiConfig.kt."
                )
            }
        }
    }
}
