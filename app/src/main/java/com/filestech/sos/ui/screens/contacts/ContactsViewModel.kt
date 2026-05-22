package com.filestech.sos.ui.screens.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.sos.core.result.AppError
import com.filestech.sos.core.result.Outcome
import com.filestech.sos.domain.contact.EmergencyContact
import com.filestech.sos.domain.contact.EmergencyContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactsUiState(
    val contacts: List<EmergencyContact> = emptyList(),
    val editing: EmergencyContact? = null, // null = no dialog, has id 0 = "new", id>0 = "edit"
    val pendingDelete: EmergencyContact? = null,
)

sealed interface ContactsEvent {
    data object SavedNew : ContactsEvent
    data object Updated : ContactsEvent
    data object Deleted : ContactsEvent
    data class ValidationError(val reason: ValidationReason) : ContactsEvent
    data object PersistenceError : ContactsEvent
}

enum class ValidationReason { NameBlank, PhoneInvalid, PriorityNegative }

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repository: EmergencyContactRepository,
) : ViewModel() {

    private val editing = MutableStateFlow<EmergencyContact?>(null)
    private val pendingDelete = MutableStateFlow<EmergencyContact?>(null)

    val state: StateFlow<ContactsUiState> = combine(
        repository.observeAll(),
        editing,
        pendingDelete,
    ) { list, edit, del ->
        ContactsUiState(contacts = list, editing = edit, pendingDelete = del)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ContactsUiState(),
    )

    private val _events = Channel<ContactsEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // === Dialog control ===

    fun openNewContactDialog() {
        editing.value = EmergencyContact(id = 0L, displayName = "", phoneNumber = "")
    }

    fun openEditDialog(contact: EmergencyContact) {
        editing.value = contact
    }

    fun dismissDialog() {
        editing.value = null
    }

    fun askDelete(contact: EmergencyContact) {
        pendingDelete.value = contact
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    // === Mutations ===

    fun saveCurrent(displayName: String, phoneNumber: String, cascadePriority: Int) {
        val current = editing.value ?: return
        viewModelScope.launch {
            val outcome = if (current.id == 0L) {
                repository.add(displayName, phoneNumber, cascadePriority)
            } else {
                repository.update(
                    current.copy(
                        displayName = displayName,
                        phoneNumber = phoneNumber,
                        cascadePriority = cascadePriority,
                    ),
                )
            }
            when (outcome) {
                is Outcome.Success<*> -> {
                    editing.value = null
                    _events.trySend(if (current.id == 0L) ContactsEvent.SavedNew else ContactsEvent.Updated)
                }
                is Outcome.Failure -> {
                    val event = (outcome.error as? AppError.Validation)?.let { v ->
                        val reason = when (v.message) {
                            "display_name_blank" -> ValidationReason.NameBlank
                            "phone_blank", "phone_no_digits", "phone_invalid" -> ValidationReason.PhoneInvalid
                            "cascade_priority_negative" -> ValidationReason.PriorityNegative
                            else -> null
                        }
                        reason?.let { ContactsEvent.ValidationError(it) }
                    } ?: ContactsEvent.PersistenceError
                    _events.trySend(event)
                }
            }
        }
    }

    fun confirmDelete() {
        val target = pendingDelete.value ?: return
        viewModelScope.launch {
            when (repository.deleteById(target.id)) {
                is Outcome.Success<*> -> {
                    pendingDelete.value = null
                    _events.trySend(ContactsEvent.Deleted)
                }
                is Outcome.Failure -> {
                    pendingDelete.value = null
                    _events.trySend(ContactsEvent.PersistenceError)
                }
            }
        }
    }
}
