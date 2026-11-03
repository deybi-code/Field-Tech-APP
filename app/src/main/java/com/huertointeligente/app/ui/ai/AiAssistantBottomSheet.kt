package com.huertointeligente.app.ui.ai

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.huertointeligente.app.R
import com.huertointeligente.app.data.model.Huerto
import com.huertointeligente.app.data.model.SensorReading
import com.huertointeligente.app.databinding.DialogAiAssistantBinding
import com.huertointeligente.app.databinding.ItemAiMessageBinding
import com.huertointeligente.app.domain.Crops
import com.huertointeligente.app.ui.MainViewModel
import com.huertointeligente.app.util.relativeTime
import kotlinx.coroutines.launch
import java.util.Locale

class AiAssistantBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogAiAssistantBinding? = null
    private val b get() = _binding!!
    private val mainVm: MainViewModel by activityViewModels()
    private val aiVm: AiViewModel by viewModels()

    private lateinit var adapter: AiMessagesAdapter
    private var attachedBitmap: Bitmap? = null
    private var textToSpeech: TextToSpeech? = null
    private var lastSpokenTimestamp: Long = 0L

    // PhotoPicker Launcher (Android Native PhotoPicker)
    private val pickMediaLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                try {
                    val bitmap = loadBitmapFromUri(uri)
                    attachedBitmap = bitmap
                    b.ivImagePreview.setImageBitmap(bitmap)
                    b.layoutImagePreview.visibility = View.VISIBLE
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "No se pudo cargar la imagen", Toast.LENGTH_SHORT).show()
                }
            }
        }

    // Speech-to-Text Voice Dictation Launcher
    private val voiceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0]
                    b.etAiInput.setText(spokenText)
                    b.etAiInput.setSelection(spokenText.length)
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAiAssistantBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializar sintetizador de voz Text-To-Speech
        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("es", "ES")
            }
        }

        adapter = AiMessagesAdapter { message ->
            speakMessage(message.text)
        }
        b.rvAiMessages.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        b.rvAiMessages.adapter = adapter

        // Configurar chips de atajo rápido
        b.chipIrrigate.setOnClickListener { sendPrompt(b.chipIrrigate.text.toString()) }
        b.chipTemp.setOnClickListener { sendPrompt(b.chipTemp.text.toString()) }
        b.chipPests.setOnClickListener { sendPrompt(b.chipPests.text.toString()) }

        // Botón Adjuntar Imagen (PhotoPicker)
        b.btnAttachImage.setOnClickListener {
            pickMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        // Botón Quitar Imagen Adjunta
        b.btnRemoveImage.setOnClickListener {
            attachedBitmap = null
            b.layoutImagePreview.visibility = View.GONE
        }

        // Botón Dictado por Voz (Speech-to-Text)
        b.btnVoiceInput.setOnClickListener {
            launchVoiceDictation()
        }

        b.btnAiSend.setOnClickListener {
            val query = b.etAiInput.text?.toString().orEmpty()
            if (query.isNotBlank() || attachedBitmap != null) {
                sendPrompt(query)
                b.etAiInput.setText("")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    mainVm.selected.collect { updateContextPill(it, mainVm.reading.value) }
                }
                launch {
                    mainVm.reading.collect { updateContextPill(mainVm.selected.value, it) }
                }
                launch {
                    aiVm.messages.collect { list ->
                        adapter.submitList(list)
                        if (list.isNotEmpty()) {
                            b.rvAiMessages.smoothScrollToPosition(list.size - 1)
                            val lastMsg = list.last()
                            if (lastMsg.sender == AiMessage.Sender.AI && lastMsg.timestamp > lastSpokenTimestamp) {
                                lastSpokenTimestamp = lastMsg.timestamp
                                speakMessage(lastMsg.text)
                            }
                        }
                    }
                }
                launch {
                    aiVm.loading.collect { isLoading ->
                        b.progressAi.visibility = if (isLoading) View.VISIBLE else View.INVISIBLE
                        b.btnAiSend.isEnabled = !isLoading
                    }
                }
            }
        }
    }

    private fun speakMessage(text: String) {
        val cleanText = text.replace(Regex("[*#_~`•🤖📸🔍🌱⚠️💧🌡️🐛🌾📡💡]"), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
        if (cleanText.isNotEmpty()) {
            textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "AiSpeech")
        }
    }

    private fun launchVoiceDictation() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla tu pregunta para FieldTech...")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Reconocimiento de voz no disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        val cr = requireContext().contentResolver
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(cr, uri)
        }
        return if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
    }

    private fun updateContextPill(huerto: Huerto?, reading: SensorReading?) {
        val hName = huerto?.name ?: "Lote 1"
        val crop = Crops.forHuerto(huerto)
        val cropName = crop.id.replaceFirstChar { it.uppercase() }
        val sm = reading?.soilMoisture?.let { "%.0f%%".format(it) } ?: "--"
        val at = reading?.airTemp?.let { "%.1f°C".format(it) } ?: "--"

        _binding?.tvSensorContext?.text = "🌾 $hName · $cropName | Suelo: $sm | Aire: $at"
    }

    private fun sendPrompt(text: String) {
        val bmp = attachedBitmap
        attachedBitmap = null
        b.layoutImagePreview.visibility = View.GONE

        aiVm.sendMessage(
            prompt = text,
            imageBitmap = bmp,
            huerto = mainVm.selected.value,
            reading = mainVm.reading.value
        )
    }

    override fun onDestroyView() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        _binding = null
        super.onDestroyView()
    }

    private class AiMessagesAdapter(
        private val onItemClick: (AiMessage) -> Unit
    ) : RecyclerView.Adapter<AiMessagesAdapter.Vh>() {

        private var items = listOf<AiMessage>()

        fun submitList(newList: List<AiMessage>) {
            items = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
            val binding = ItemAiMessageBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Vh(binding)
        }

        override fun onBindViewHolder(holder: Vh, position: Int) {
            holder.bind(items[position], onItemClick)
        }

        override fun getItemCount(): Int = items.size

        class Vh(private val b: ItemAiMessageBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(msg: AiMessage, onItemClick: (AiMessage) -> Unit) {
                b.tvMessageText.text = msg.text
                b.tvMessageTime.text = relativeTime(msg.timestamp)

                val ctx = b.root.context
                val isAi = msg.sender == AiMessage.Sender.AI

                b.root.setOnClickListener {
                    if (isAi) onItemClick(msg)
                }

                val layoutParams = b.cardMessage.layoutParams as LinearLayout.LayoutParams
                layoutParams.gravity = if (isAi) android.view.Gravity.START else android.view.Gravity.END
                b.cardMessage.layoutParams = layoutParams

                val cardBg = if (isAi) {
                    ContextCompat.getColor(ctx, R.color.surface)
                } else {
                    ContextCompat.getColor(ctx, R.color.brand_container)
                }
                val strokeCol = if (isAi) {
                    ContextCompat.getColor(ctx, R.color.outline_soft)
                } else {
                    ContextCompat.getColor(ctx, R.color.brand)
                }

                b.cardMessage.setCardBackgroundColor(cardBg)
                b.cardMessage.strokeColor = strokeCol
            }
        }
    }

    companion object {
        const val TAG = "AiAssistantBottomSheet"
        fun newInstance() = AiAssistantBottomSheet()
    }
}
