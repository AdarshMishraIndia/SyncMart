package com.mana.syncmart.dashboard

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mana.syncmart.ShoppingItem
import com.mana.syncmart.ShoppingList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL

data class UiState(
    val isLoading: Boolean = false,
    val shoppingLists: List<ShoppingList> = emptyList(),
    val isConnected: Boolean = false,
    val userName: String = "User",
    val navigateToAuth: Boolean = false,
    val navigateToFriendActivity: Boolean = false,
    val navigateToRegister: Boolean = false
)

class ListManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val _uiState = MutableLiveData(UiState())
    val uiState: LiveData<UiState> get() = _uiState
    private val _toastMessage = SingleLiveEvent<String>()
    val toastMessage: LiveData<String> get() = _toastMessage
    private val shoppingLists = mutableMapOf<String, ShoppingList>()
    private val realTimeListeners = mutableListOf<ListenerRegistration>()
    private val isConnectedFlow = MutableStateFlow(false)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        setupInternetMonitoring()
        fetchUserName()
    }

    private fun setupInternetMonitoring() {
        val cm = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                viewModelScope.launch {
                    isConnectedFlow.value = true
                    fetchShoppingLists()
                }
            }

            override fun onLost(network: Network) {
                viewModelScope.launch { isConnectedFlow.value = false }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, networkCallback)

        viewModelScope.launch {
            isConnectedFlow.collect { connected ->
                _uiState.value = _uiState.value?.copy(isConnected = connected)
            }
        }
    }

    fun fetchUserName() {
        val userEmail = auth.currentUser?.email ?: return
        db.collection("Users").document(userEmail).get()
            .addOnSuccessListener { document ->
                val userName = document.getString("name")?.split(" ")?.firstOrNull()?.trim() ?: "User"
                _uiState.value = _uiState.value?.copy(userName = userName) ?: UiState(userName = userName)
            }
            .addOnFailureListener { _toastMessage.value = "Failed to fetch user profile" }
    }

    fun fetchShoppingLists() {
        _uiState.value = _uiState.value?.copy(isLoading = true) ?: UiState(isLoading = true)
        val userEmail = auth.currentUser?.email ?: return
        val combinedLists = mutableMapOf<String, ShoppingList>()

        fun updateUI() {
            val newSorted = combinedLists.values.sortedBy { it.position }
            shoppingLists.clear()
            newSorted.forEach { shoppingLists[it.id] = it }
            _uiState.value = _uiState.value?.copy(shoppingLists = newSorted, isLoading = false)
        }

        // Fetch owned lists
        db.collection("shopping_lists")
            .whereEqualTo("owner", userEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot?.documents?.forEach { doc ->
                    val list = doc.toObject(ShoppingList::class.java)?.copy(id = doc.id)
                    list?.let { combinedLists[doc.id] = it }
                }
                // Fetch shared lists
                db.collection("shopping_lists")
                    .whereArrayContains("accessEmails", userEmail)
                    .get()
                    .addOnSuccessListener { accessSnapshot ->
                        accessSnapshot?.documents?.forEach { doc ->
                            val list = doc.toObject(ShoppingList::class.java)?.copy(id = doc.id)
                            list?.let { combinedLists[doc.id] = it }
                        }
                        updateUI()
                        setupRealTimeListeners(combinedLists)
                    }
                    .addOnFailureListener {
                        _toastMessage.value = "Failed to fetch shared lists"
                        _uiState.value = _uiState.value?.copy(isLoading = false)
                    }
            }
            .addOnFailureListener {
                _toastMessage.value = "Failed to fetch shopping lists"
                _uiState.value = _uiState.value?.copy(isLoading = false)
            }
    }

    private fun setupRealTimeListeners(combinedLists: MutableMap<String, ShoppingList>) {
        realTimeListeners.forEach { it.remove() }
        realTimeListeners.clear()

        val userEmail = auth.currentUser?.email ?: return
        val collections = listOf(
            db.collection("shopping_lists").whereEqualTo("owner", userEmail),
            db.collection("shopping_lists").whereArrayContains("accessEmails", userEmail)
        )

        collections.forEach { col ->
            realTimeListeners.add(
                col.addSnapshotListener { snapshot, _ ->
                    snapshot ?: return@addSnapshotListener
                    for (change in snapshot.documentChanges) {
                        val docId = change.document.id
                        when (change.type) {
                            DocumentChange.Type.ADDED,
                            DocumentChange.Type.MODIFIED -> {
                                try {
                                    val list = change.document.toObject(ShoppingList::class.java).copy(id = docId)
                                    combinedLists[docId] = list
                                } catch (e: Exception) {
                                    _toastMessage.value = "Failed to parse list: ${e.message}"
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                combinedLists.remove(docId)
                            }
                        }
                    }
                    updateUI(combinedLists)
                }
            )
        }
    }

    private fun updateUI(combinedLists: MutableMap<String, ShoppingList>) {
        val newSorted = combinedLists.values.sortedBy { it.position }
        shoppingLists.clear()
        newSorted.forEach { shoppingLists[it.id] = it }
        _uiState.value = _uiState.value?.copy(shoppingLists = newSorted)
    }

    fun createNewList(listName: String, accessEmails: List<String>) {
        val userEmail = auth.currentUser?.email ?: return
        val newList = mapOf(
            "listName" to listName,
            "owner" to userEmail,
            "accessEmails" to accessEmails,
            "items" to mapOf<String, ShoppingItem>(),
            "createdAt" to Timestamp.now(),
            "position" to 0
        )
        db.collection("shopping_lists").add(newList)
            .addOnFailureListener { _toastMessage.value = "Failed to create list" }
    }

    fun updateExistingList(listId: String, listName: String, accessEmails: List<String>) {
        val updates = mapOf(
            "listName" to listName,
            "accessEmails" to accessEmails
        )
        db.collection("shopping_lists").document(listId).update(updates)
            .addOnFailureListener { _toastMessage.value = "Failed to update list" }
    }

    fun deleteSelectedItems(selectedIds: List<String>) {
        val batch = db.batch()
        selectedIds.forEach { docId ->
            batch.delete(db.collection("shopping_lists").document(docId))
        }
        batch.commit()
            .addOnSuccessListener {
                selectedIds.forEach { shoppingLists.remove(it) }
                _uiState.value = _uiState.value?.copy(shoppingLists = shoppingLists.values.sortedBy { it.position })
            }
            .addOnFailureListener { _toastMessage.value = "Failed to delete lists" }
    }

    fun deleteAccount() {
        val user = auth.currentUser ?: return
        val userEmail = user.email ?: return
        db.collection("Users").document(userEmail).delete()
            .addOnSuccessListener {
                user.delete()
                    .addOnSuccessListener {
                        _toastMessage.value = "Account deleted successfully"
                        _uiState.value = _uiState.value?.copy(navigateToRegister = true)
                    }
                    .addOnFailureListener { _toastMessage.value = "Failed to delete authentication account" }
            }
            .addOnFailureListener { _toastMessage.value = "Failed to delete Firestore account" }
    }

    fun sendWhatsAppNotification(success: (Boolean) -> Unit) {
        val apiUrl =
            "https://bhashsms.com/api/sendmsg.php?user=Urban_BW&pass=ucbl123&sender=BUZWAP&phone=9040292104&text=dddd&priority=wa&stype=normal"

        viewModelScope.launch(Dispatchers.IO) {
            var wasSuccessful = false
            try {
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Sending Notification..."
                }

                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.inputStream.close()
                wasSuccessful = true

            } catch (_: SocketTimeoutException) {
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Request timed out. Please try again."
                }
            } catch (_: IOException) {
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Network error occurred."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Unexpected error: ${e.message}"
                }
            }

            withContext(Dispatchers.Main) {
                if (wasSuccessful) _toastMessage.value = "Notification Sent"
                success(wasSuccessful)
            }
        }
    }

    fun logout() {
        auth.signOut()
        _uiState.value = _uiState.value?.copy(navigateToAuth = true)
    }

    fun navigateToFriendActivity() { _uiState.value = _uiState.value?.copy(navigateToFriendActivity = true) }
    fun navigateToEditProfile() { _uiState.value = _uiState.value?.copy(navigateToRegister = true) }
    fun resetNavigateToAuth() { _uiState.value = _uiState.value?.copy(navigateToAuth = false) }
    fun resetNavigateToFriendActivity() { _uiState.value = _uiState.value?.copy(navigateToFriendActivity = false) }
    fun resetNavigateToRegister() { _uiState.value = _uiState.value?.copy(navigateToRegister = false) }

    override fun onCleared() {
        realTimeListeners.forEach { it.remove() }
        scope.cancel()
        super.onCleared()
    }
}
