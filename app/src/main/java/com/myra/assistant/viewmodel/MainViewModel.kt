package com.myra.assistant.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.myra.assistant.ai.DynamicDecisionEngine
import com.myra.assistant.ai.IntentAnalyzer
import com.myra.assistant.models.CommandType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val aiResponse = MutableLiveData<String>()
    val userMessage = MutableLiveData<String>()

    fun processCommand(rawCommand: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val command = IntentAnalyzer.analyze(rawCommand)
            if (command.type != CommandType.CONVERSATION) {
                val response = DynamicDecisionEngine.execute(getApplication(), command)
                if (response.isNotBlank()) aiResponse.postValue(response)
            }
        }
    }

    fun isDirectCommand(text: String): Boolean {
        val cmd = IntentAnalyzer.analyze(text)
        return cmd.type != CommandType.CONVERSATION && cmd.type != CommandType.UNKNOWN
    }
}
