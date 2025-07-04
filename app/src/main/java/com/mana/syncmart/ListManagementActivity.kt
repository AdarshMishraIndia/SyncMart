package com.mana.syncmart

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mana.syncmart.databinding.ActivityListManagementBinding
import com.mana.syncmart.databinding.DialogConfirmBinding
import com.mana.syncmart.databinding.DialogModifyListBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException
import java.io.IOException

@Suppress("UNCHECKED_CAST", "DEPRECATION")
class ListManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListManagementBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var listAdapter: ListAdapter
    private val shoppingLists = mutableMapOf<String, ShoppingList>()
    private val realTimeListeners = mutableListOf<ListenerRegistration>()
    private var userName: String = "User"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu)
            title = "SyncMart"
        }

        setupRecyclerView()
        setupDragAndDrop()
        setupNavigationDrawer()
        fetchUserName()
        fetchShoppingLists()



        binding.addListButton.setOnClickListener {
            showModifyDialog(isEditing = false)
        }

        var isRequestInProgress = false

        binding.textViewSendWapp.setOnClickListener {
            if (!isRequestInProgress) {
                isRequestInProgress = true

                // Set progress background (red_pink_bg)
                binding.textViewSendWapp.background =
                    ContextCompat.getDrawable(this, R.drawable.red_pink_bg)

                sendWhatsAppNotification(success = { wasSuccessful ->
                    isRequestInProgress = false

                    binding.textViewSendWapp.setBackgroundResource(R.drawable.green_blue_bg )
                })
            }
        }

    }

    private fun setupNavigationDrawer() {
        val drawerLayout = binding.drawerLayout
        val navView = binding.navigationView
        val navMenu = navView.menu
        val toggleItem = navMenu.findItem(R.id.nav_friends_and_lists)
        toggleItem.title = getString(R.string.friends)
        toggleItem.setIcon(R.drawable.ic_friends)

        binding.toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_friends_and_lists -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    startActivity(Intent(this, FriendActivity::class.java))
                    true
                }
                R.id.nav_logout -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    logoutUser()
                    true
                }
                R.id.nav_delete_account -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    confirmDeleteAccount()
                    true
                }
                R.id.nav_edit_profile -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    val intent = Intent(this, RegisterActivity::class.java)
                    intent.putExtra("isEditingProfile", true)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun confirmDeleteAccount() {
        val dialogBinding = DialogConfirmBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.listName.text = "Are you sure you want to delete your account?"
        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnDelete.setOnClickListener {
            dialog.dismiss()
            deleteAccount()
        }

        dialog.show()
    }

    private fun deleteAccount() {
        val user = auth.currentUser ?: return
        val userEmail = user.email ?: return

        showCustomToast("Deleting account...")

        // Step 1: Delete Firestore user document
        db.collection("Users").document(userEmail).delete()
            .addOnSuccessListener {
                // Step 2: Delete Firebase auth user
                user.delete()
                    .addOnSuccessListener {
                        showCustomToast("✅ Account deleted successfully")
                        startActivity(Intent(this, RegisterActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        finish()
                    }
                    .addOnFailureListener {
                        showCustomToast("❌ Failed to delete authentication account. Please re-authenticate.")
                    }
            }
            .addOnFailureListener {
                showCustomToast("❌ Failed to delete Firestore account.")
            }
    }

    private fun fetchUserName() {
        val userEmail = auth.currentUser?.email ?: return

        db.collection("Users").document(userEmail).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userName = document.getString("name")?.split(" ")?.firstOrNull()?.trim() ?: "User"
                    updateToolbarTitle()
                }
            }
            .addOnFailureListener {
                showCustomToast("Failed to fetch user profile")
            }
    }

    @SuppressLint("SetTextI18n")
    private fun updateToolbarTitle() {
        val selectedCount = listAdapter.getSelectedItems().size
        if (selectedCount > 0) {
            binding.toolbarTitle.visibility = View.GONE
            binding.toolbarUser.visibility = View.GONE
            binding.toolbar.title = "$selectedCount Selected"
        } else {
            binding.toolbarTitle.visibility = View.VISIBLE
            binding.toolbarUser.visibility = View.VISIBLE
            binding.toolbarUser.text = "Welcome, $userName"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean = true

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                binding.drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            R.id.action_delete_selection -> {
                confirmDeleteSelectedItems()
                true
            }
            R.id.action_edit_selection -> {
                editSelectedItem()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun logoutUser() {
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun setupRecyclerView() {
        listAdapter = ListAdapter(
            onSelectionChanged = { isActive, count -> toggleSelectionMode(isActive, count) },
            onListClicked = { list ->
                if (!listAdapter.isSelectionModeActive()) {
                    startActivity(Intent(this, ListActivity::class.java).putExtra("LIST_ID", list.id))
                }
            }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ListManagementActivity)
            adapter = listAdapter
        }
    }

    @SuppressLint("SetTextI18n")
    private fun toggleSelectionMode(isActive: Boolean, selectedCount: Int = 0) {
        if (isActive && selectedCount > 0) {
            binding.toolbarUser.visibility = View.GONE
            binding.toolbar.title = "$selectedCount Selected"
            binding.toolbar.menu.clear()
            binding.toolbar.inflateMenu(R.menu.selection_menu)

            val userEmail = auth.currentUser?.email ?: return
            val allOwned = listAdapter.getSelectedItems().all { shoppingLists[it]?.owner == userEmail }

            binding.toolbar.menu.findItem(R.id.action_delete_selection)?.isVisible = allOwned
            binding.toolbar.menu.findItem(R.id.action_edit_selection)?.isVisible = allOwned && selectedCount == 1

            binding.toolbar.invalidate()
        } else {
            binding.toolbarUser.visibility = View.VISIBLE
            binding.toolbar.title = "SyncMart"
            binding.toolbar.menu.clear()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun confirmDeleteSelectedItems() {
        val selectedIds = listAdapter.getSelectedItems()
        if (selectedIds.isEmpty()) return

        val dialogBinding = DialogConfirmBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.listName.text = "Delete ${selectedIds.size} selected lists?"
        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnDelete.setOnClickListener {
            deleteSelectedItems(selectedIds)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun deleteSelectedItems(selectedIds: List<String>) {
        val batch = db.batch()
        selectedIds.forEach { docId ->
            val docRef = db.collection("shopping_lists").document(docId)
            batch.delete(docRef)
        }

        batch.commit()
            .addOnSuccessListener {
                selectedIds.forEach { docId -> shoppingLists.remove(docId) }
                val updatedList = shoppingLists.values.sortedBy { it.position }
                listAdapter.updateListPreserveSelection(updatedList)
                binding.emptyStateText.visibility = if (shoppingLists.isEmpty()) View.VISIBLE else View.GONE
                listAdapter.clearSelection()
                toggleSelectionMode(false)
                fetchShoppingLists()
            }
            .addOnFailureListener {
                showCustomToast("Failed to delete lists")
            }
    }

    private fun editSelectedItem() {
        val selectedId = listAdapter.getSelectedItems().singleOrNull() ?: return
        shoppingLists[selectedId]?.let { showModifyDialog(true, it) }
    }

    private fun isListContentEqual(newList: List<ShoppingList>, currentList: List<ShoppingList>): Boolean {
        if (newList.size != currentList.size) return false
        return newList.zip(currentList).all { (newItem, currentItem) -> newItem == currentItem }
    }

    private fun fetchShoppingLists() {
        val userEmail = auth.currentUser?.email ?: return
        val combinedLists = mutableMapOf<String, ShoppingList>()

        fun updateUI() {
            val newSorted = combinedLists.values.sortedBy { it.position }
            if (!isListContentEqual(newSorted, listAdapter.currentList)) {
                shoppingLists.clear()
                newSorted.forEach { shoppingLists[it.id] = it }
                listAdapter.updateListPreserveSelection(newSorted.toMutableList())
                toggleSelectionMode(false)
                binding.emptyStateText.visibility = if (shoppingLists.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        db.collection("shopping_lists")
            .whereEqualTo("owner", userEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot?.documents?.forEach { doc ->
                    val shoppingList = doc.toObject(ShoppingList::class.java)
                    if (shoppingList != null) {
                        combinedLists[doc.id] = shoppingList.copy(id = doc.id)
                    }
                }

                db.collection("shopping_lists")
                    .whereArrayContains("accessEmails", userEmail)
                    .get()
                    .addOnSuccessListener { accessSnapshot ->
                        accessSnapshot?.documents?.forEach { doc ->
                            val shoppingList = doc.toObject(ShoppingList::class.java)
                            if (shoppingList != null) {
                                combinedLists[doc.id] = shoppingList.copy(id = doc.id)
                            }
                        }

                        updateUI()

                        realTimeListeners.forEach { it.remove() }
                        realTimeListeners.clear()

                        realTimeListeners.add(
                            db.collection("shopping_lists")
                                .whereEqualTo("owner", userEmail)
                                .addSnapshotListener { snapshot, _ ->
                                    if (snapshot == null) return@addSnapshotListener

                                    for (change in snapshot.documentChanges) {
                                        val docId = change.document.id
                                        when (change.type) {
                                            com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                            com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                                try {
                                                    val list = change.document.toObject(ShoppingList::class.java).copy(id = docId)
                                                    combinedLists[docId] = list
                                                } catch (e: Exception) {
                                                    showCustomToast("❌ Failed to parse list: ${e.message}")
                                                }
                                            }
                                            com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                                combinedLists.remove(docId)
                                            }
                                        }
                                    }
                                    updateUI()
                                }
                        )

                        realTimeListeners.add(
                            db.collection("shopping_lists")
                                .whereArrayContains("accessEmails", userEmail)
                                .addSnapshotListener { snapshot, _ ->
                                    if (snapshot == null) return@addSnapshotListener

                                    for (change in snapshot.documentChanges) {
                                        val docId = change.document.id
                                        when (change.type) {
                                            com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                            com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                                try {
                                                    val list = change.document.toObject(ShoppingList::class.java).copy(id = docId)
                                                    combinedLists[docId] = list
                                                } catch (e: Exception) {
                                                    showCustomToast("❌ Failed to parse list: ${e.message}")
                                                }
                                            }
                                            com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                                combinedLists.remove(docId)
                                            }
                                        }
                                    }
                                    updateUI()
                                }
                        )

                    }
                    .addOnFailureListener {
                        showCustomToast("Failed to fetch lists with access")
                    }
            }
            .addOnFailureListener {
                showCustomToast("Failed to fetch shopping lists")
            }
    }

    private fun showModifyDialog(isEditing: Boolean, existingList: ShoppingList? = null) {
        val dialogBinding = DialogModifyListBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        val selectedEmails = existingList?.accessEmails?.toMutableSet() ?: mutableSetOf()
        dialogBinding.editTextListName.setText(existingList?.listName ?: "")

        val userEmail = auth.currentUser?.email ?: return

        db.collection("Users").document(userEmail).addSnapshotListener { snapshot, _ ->
            if (snapshot == null || !snapshot.exists()) {
                toggleNoFriendsMessage(dialogBinding, true)
                return@addSnapshotListener
            }

            val rawMap = snapshot.get("friendsMap") as? Map<*, *> ?: emptyMap<String, String>()
            val validMap = rawMap.entries.mapNotNull {
                (it.key as? String)?.let { k -> (it.value as? String)?.let { v -> k to v } }
            }.toMap()

            updateFriendsUI(dialogBinding, validMap, selectedEmails)
        }

        dialogBinding.btnCreate.text = if (isEditing) "Update" else "Create"
        dialogBinding.btnCreate.setOnClickListener {
            val name = dialogBinding.editTextListName.text.toString().trim()
            if (name.isEmpty()) return@setOnClickListener
            if (isEditing) existingList?.id?.let { updateExistingList(it, name, selectedEmails.toList()) }
            else createNewList(name, selectedEmails.toList())
            dialog.dismiss()
        }
    }

    private fun updateFriendsUI(dialogBinding: DialogModifyListBinding, friendsMap: Map<String, String>, selectedEmails: MutableSet<String>) {
        toggleNoFriendsMessage(dialogBinding, friendsMap.isEmpty())

        val friendsList = friendsMap.map { Friend(it.value, it.key) }
        val adapter = FriendSelectionAdapter(this, friendsList, selectedEmails, { friend, isChecked ->
            if (isChecked) selectedEmails.add(friend.email) else selectedEmails.remove(friend.email)
        }, showCheckBox = true)

        dialogBinding.listViewMembers.adapter = adapter
        adapter.notifyDataSetChanged()
    }

    private fun toggleNoFriendsMessage(dialogBinding: DialogModifyListBinding, isEmpty: Boolean) {
        dialogBinding.textViewNoFriends.visibility = if (isEmpty) View.VISIBLE else View.GONE
        dialogBinding.listViewMembers.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun createNewList(listName: String, accessEmails: List<String>) {
        val userEmail = auth.currentUser?.email ?: return
        val newList = mapOf("listName" to listName, "owner" to userEmail, "accessEmails" to accessEmails)
        db.collection("shopping_lists").add(newList).addOnFailureListener {
            showCustomToast("Failed to create list")
        }
    }

    private fun updateExistingList(listId: String, name: String, accessEmails: List<String>) {
        val updates = mapOf("listName" to name, "accessEmails" to accessEmails)
        db.collection("shopping_lists").document(listId).update(updates)
            .addOnSuccessListener {
                listAdapter.clearSelection()
                toggleSelectionMode(false)
            }
            .addOnFailureListener {
                showCustomToast("Failed to update list")
            }
    }

    private fun sendWhatsAppNotification(success: (Boolean) -> Unit) {
        val apiUrl = "https://bhashsms.com/api/sendmsg.php?" +
                "user=Urban_BW&pass=ucbl123&sender=BUZWAP&" +
                "phone=9040292104&text=dddd&priority=wa&stype=normal"

        CoroutineScope(Dispatchers.IO).launch {
            var wasSuccessful = false

            try {
                withContext(Dispatchers.Main) {
                    showCustomToast("📨 Sending Notification...")
                }

                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000  // Short timeout
                conn.readTimeout = 3000

                // "Fire" the request by opening the connection and immediately closing
                conn.inputStream.close()
                wasSuccessful = true

            } catch (_: SocketTimeoutException) {
                // Log or handle timeout silently
            } catch (_: IOException) {
                // Log or handle network error silently
            } catch (_: Exception) {
                // Log or handle generic error silently
            }

            // Notify main thread of result
            withContext(Dispatchers.Main) {
                if (wasSuccessful) {
                    showCustomToast("✅ Notification Sent")
                }
                success(wasSuccessful)
            }
        }
    }



    override fun onDestroy() {
        realTimeListeners.forEach { it.remove() }
        super.onDestroy()
    }

    @Deprecated("Use OnBackPressedDispatcher instead")
    override fun onBackPressed() {
        if (listAdapter.isSelectionModeActive()) {
            listAdapter.clearSelection()
            toggleSelectionMode(false)
        } else super.onBackPressed()
    }

    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                listAdapter.moveItem(vh.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerView)
    }

    @Suppress("DEPRECATION")
    private fun showCustomToast(message: String) {
        val inflater = LayoutInflater.from(this)
        val layout = inflater.inflate(R.layout.custom_toast_layout, findViewById(android.R.id.content), false)

        val toastText = layout.findViewById<TextView>(R.id.toast_text)
        toastText.text = message

        val toast = Toast(this)
        toast.duration = Toast.LENGTH_LONG
        toast.setGravity(Gravity.CENTER, 0, 0) // ✅ Center toast on screen
        toast.view = layout

        toast.show()
    }
}
