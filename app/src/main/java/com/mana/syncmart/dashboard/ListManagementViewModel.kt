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
import com.mana.syncmart.dataclass.ShoppingItem
import com.mana.syncmart.dataclass.ShoppingList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
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

    // Track lists already processed for migration/backfill this session
    private val migratedIds = mutableSetOf<String>()

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
            val activeLists = combinedLists.values.filter { it.active }
            val newSorted = activeLists.sortedBy { it.position }

            activeLists.firstOrNull { list ->
                list.id.length == 20 || !list.id.contains("_")
            }?.let { oldList -> migrateListId(oldList) }

            shoppingLists.clear()
            newSorted.forEach { shoppingLists[it.id] = it }
            _uiState.value = _uiState.value?.copy(shoppingLists = newSorted, isLoading = false)
        }

        db.collection("shopping_lists")
            .whereEqualTo("owner", userEmail)
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot?.documents?.forEach { doc ->
                    val list = doc.toObject(ShoppingList::class.java)?.copy(id = doc.id)
                    list?.let { combinedLists[doc.id] = it }
                }
                db.collection("shopping_lists")
                    .whereArrayContains("accessEmails", userEmail)
                    .whereEqualTo("active", true)
                    .get()
                    .addOnSuccessListener { accessSnapshot ->
                        accessSnapshot?.documents?.forEach { doc ->
                            val list = doc.toObject(ShoppingList::class.java)?.copy(id = doc.id)
                            list?.let { combinedLists[doc.id] = it }
                        }
                        updateUI()
                        setupRealTimeListeners(combinedLists)

                        // Backfill list-level AND item-level missing fields
                        combinedLists.forEach { (docId, list) ->
                            if (docId !in migratedIds) {
                                if (list.lastModified == null) {
                                    backfillListAndItemFields(docId)
                                } else {
                                    // List-level is fine; still check items
                                    backfillItemFieldsOnly(docId)
                                }
                            }
                        }
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

    /**
     * Backfills list-level lastModified AND scans all items for missing active/lastModified.
     * Used when the list itself is missing lastModified (older lists).
     */
    private fun backfillListAndItemFields(listId: String) {
        if (listId in migratedIds) return
        migratedIds.add(listId)

        val docRef = db.collection("shopping_lists").document(listId)
        docRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) return@addOnSuccessListener

            val flatUpdates = hashMapOf<String, Any>()

            // Backfill list-level lastModified
            if (snapshot.get("lastModified") == null) {
                val fallback = snapshot.getTimestamp("createdAt") ?: Timestamp.now()
                flatUpdates["lastModified"] = fallback
            }

            // Scan and backfill item-level active and lastModified
            val itemsMap = snapshot.get("items") as? Map<*, *> ?: emptyMap<Any, Any>()
            val now = Timestamp.now()
            itemsMap.forEach { entry ->
                val itemId = entry.key as? String ?: return@forEach
                val value = entry.value as? Map<*, *> ?: return@forEach
                if (value["active"] == null) flatUpdates["items.$itemId.active"] = true
                if (value["lastModified"] == null) flatUpdates["items.$itemId.lastModified"] = now
            }

            if (flatUpdates.isNotEmpty()) {
                docRef.update(flatUpdates)
                    .addOnFailureListener { migratedIds.remove(listId) }
            }
        }
    }

    /**
     * Used when list-level lastModified is already present but items may still be legacy.
     * Avoids a redundant list-level write.
     */
    private fun backfillItemFieldsOnly(listId: String) {
        if (listId in migratedIds) return

        val docRef = db.collection("shopping_lists").document(listId)
        docRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) return@addOnSuccessListener

            val itemsMap = snapshot.get("items") as? Map<*, *> ?: emptyMap<Any, Any>()
            val flatUpdates = hashMapOf<String, Any>()
            val now = Timestamp.now()

            itemsMap.forEach { entry ->
                val itemId = entry.key as? String ?: return@forEach
                val value = entry.value as? Map<*, *> ?: return@forEach
                if (value["active"] == null) flatUpdates["items.$itemId.active"] = true
                if (value["lastModified"] == null) flatUpdates["items.$itemId.lastModified"] = now
            }

            if (flatUpdates.isNotEmpty()) {
                flatUpdates["lastModified"] = now  // Bump list-level too since items changed
                migratedIds.add(listId)
                docRef.update(flatUpdates)
                    .addOnFailureListener { migratedIds.remove(listId) }
            } else {
                // Nothing to backfill — mark as done so we don't check again this session
                migratedIds.add(listId)
            }
        }
    }

    private fun setupRealTimeListeners(combinedLists: MutableMap<String, ShoppingList>) {
        realTimeListeners.forEach { it.remove() }
        realTimeListeners.clear()

        val userEmail = auth.currentUser?.email ?: return
        val collections = listOf(
            db.collection("shopping_lists").whereEqualTo("owner", userEmail).whereEqualTo("active", true),
            db.collection("shopping_lists").whereArrayContains("accessEmails", userEmail).whereEqualTo("active", true)
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
                                    if (list.active) {
                                        combinedLists[docId] = list
                                        // Backfill on real-time update if needed
                                        if (docId !in migratedIds) {
                                            if (list.lastModified == null) backfillListAndItemFields(docId)
                                            else backfillItemFieldsOnly(docId)
                                        }
                                    } else {
                                        combinedLists.remove(docId)
                                    }
                                } catch (e: Exception) {
                                    _toastMessage.value = "Failed to parse list: ${e.message}"
                                }
                            }
                            DocumentChange.Type.REMOVED -> combinedLists.remove(docId)
                        }
                    }
                    updateUI(combinedLists)
                }
            )
        }
    }

    private fun updateUI(combinedLists: MutableMap<String, ShoppingList>) {
        val newSorted = combinedLists.values
            .filter { it.active }
            .sortedBy { it.position }
        shoppingLists.clear()
        newSorted.forEach { shoppingLists[it.id] = it }
        _uiState.value = _uiState.value?.copy(shoppingLists = newSorted)
    }

    fun createNewList(listName: String, accessEmails: List<String>) {
        val userEmail = auth.currentUser?.email ?: return

        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val randomSuffix = (1..6).map { allowedChars.random() }.joinToString("")
        val slug = listName.lowercase().replace(" ", "_").take(15)
        val customId = "${slug}_$randomSuffix"

        val now = Timestamp.now()
        val newList = mapOf(
            "listName" to listName,
            "owner" to userEmail,
            "accessEmails" to accessEmails,
            "items" to mapOf<String, ShoppingItem>(),
            "createdAt" to now,
            "lastModified" to now,
            "position" to 0,
            "active" to true
        )

        db.collection("shopping_lists").document(customId).set(newList)
            .addOnFailureListener { _toastMessage.value = "Failed to create list" }
    }

    fun updateExistingList(listId: String, listName: String, accessEmails: List<String>) {
        val updates = mapOf(
            "listName" to listName,
            "accessEmails" to accessEmails,
            "lastModified" to Timestamp.now()
        )
        db.collection("shopping_lists").document(listId).update(updates)
            .addOnFailureListener { _toastMessage.value = "Failed to update list" }
    }

    /**
     * Soft deletes selected lists. Stamps lastModified so the change is trackable.
     */
    fun deleteSelectedItems(selectedIds: List<String>) {
        val batch = db.batch()
        val now = Timestamp.now()
        selectedIds.forEach { docId ->
            val docRef = db.collection("shopping_lists").document(docId)
            batch.update(docRef, mapOf("active" to false, "lastModified" to now))
        }
        batch.commit()
            .addOnSuccessListener {
                selectedIds.forEach { shoppingLists.remove(it) }
                _uiState.value = _uiState.value?.copy(
                    shoppingLists = shoppingLists.values.filter { it.active }.sortedBy { it.position }
                )
                _toastMessage.value = "Lists moved to archive"
            }
            .addOnFailureListener { _toastMessage.value = "Failed to delete lists" }
    }

    fun deleteAccount() {
        val user = auth.currentUser ?: return
        val userEmail = user.email ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ownedListsQuery = db.collection("shopping_lists")
                    .whereEqualTo("owner", userEmail).get().await()
                val memberListsQuery = db.collection("shopping_lists")
                    .whereArrayContains("accessEmails", userEmail).get().await()

                for (doc in ownedListsQuery.documents) {
                    val accessEmails = doc.get("accessEmails") as? List<*>
                    val newOwner = accessEmails?.firstOrNull { it is String && it != userEmail } as? String
                    if (newOwner != null) {
                        doc.reference.update(hashMapOf(
                            "owner" to newOwner,
                            "accessEmails" to accessEmails.filter { it != newOwner }
                        )).await()
                    } else {
                        doc.reference.delete().await()
                    }
                }

                for (doc in memberListsQuery.documents) {
                    val accessEmails = (doc.get("accessEmails") as? List<*>)?.filterIsInstance<String>() ?: continue
                    val updated = accessEmails.filter { it != userEmail }
                    if (updated != accessEmails) doc.reference.update("accessEmails", updated).await()
                }

                db.collection("Users").document(userEmail).delete().await()
                user.delete().await()

                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Account deleted successfully"
                    _uiState.value = _uiState.value?.copy(navigateToAuth = true)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Error deleting account: ${e.message}"
                }
            }
        }
    }

    fun sendWhatsAppNotification(success: (Boolean) -> Unit) {
        val apiUrl = "https://bhashsms.com/api/sendmsg.php?user=Urban_BW&pass=ucbl123&sender=BUZWAP&phone=9668514995&text=dddd&priority=wa&stype=normal"

        viewModelScope.launch(Dispatchers.IO) {
            var wasSuccessful = false
            try {
                withContext(Dispatchers.Main) { _toastMessage.value = "Sending Notification..." }
                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.inputStream.close()
                wasSuccessful = true
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { _toastMessage.value = "Notification failed" }
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

    fun navigateToEditProfile() {
        _uiState.value = _uiState.value?.copy(navigateToRegister = true)
    }

    fun navigateToFriendActivity() {
        _uiState.value = _uiState.value?.copy(navigateToFriendActivity = true)
    }

    fun resetNavigateToAuth() {
        _uiState.value = _uiState.value?.copy(navigateToAuth = false)
    }

    fun resetNavigateToFriendActivity() {
        _uiState.value = _uiState.value?.copy(navigateToFriendActivity = false)
    }

    fun resetNavigateToRegister() {
        _uiState.value = _uiState.value?.copy(navigateToRegister = false)
    }

    /**
     * Migrates old auto-ID format to slug-based ID.
     * Ensures active, lastModified, and all item-level active/lastModified fields are present
     * in the migrated document.
     */
    fun migrateListId(oldList: ShoppingList) {
        val oldId = oldList.id
        if (oldId.contains("_") && oldId.length > 7) return
        if (oldId in migratedIds) return
        migratedIds.add(oldId)

        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val randomSuffix = (1..6).map { allowedChars.random() }.joinToString("")
        val slug = oldList.listName.lowercase().replace(" ", "_").trim().take(15)
        val newId = "${slug}_$randomSuffix"

        val oldRef = db.collection("shopping_lists").document(oldId)
        val newRef = db.collection("shopping_lists").document(newId)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = oldRef.get().await()
                if (!snapshot.exists()) return@launch

                val data = snapshot.data?.toMutableMap() ?: return@launch
                val now = Timestamp.now()

                data["active"] = true
                if (data["lastModified"] == null) {
                    data["lastModified"] = data["createdAt"] ?: now
                }

                // Backfill item-level active and lastModified inside the migrated document
                @Suppress("UNCHECKED_CAST")
                val items = data["items"] as? MutableMap<String, MutableMap<String, Any>>
                items?.forEach { (_, itemData) ->
                    if (itemData["active"] == null) itemData["active"] = true
                    if (itemData["lastModified"] == null) itemData["lastModified"] = now
                }

                newRef.set(data).await()
                oldRef.delete().await()

                withContext(Dispatchers.Main) {
                    _toastMessage.value = "List migrated successfully"
                    fetchShoppingLists()
                }
            } catch (e: Exception) {
                migratedIds.remove(oldId)
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Migration failed: ${e.message}"
                }
            }
        }
    }
}