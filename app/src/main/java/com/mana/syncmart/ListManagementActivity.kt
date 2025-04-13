package com.mana.syncmart

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mana.syncmart.databinding.ActivityListManagementBinding
import com.mana.syncmart.databinding.DialogConfirmBinding
import com.mana.syncmart.databinding.DialogModifyListBinding
import android.widget.Toast
import androidx.core.view.GravityCompat
import android.util.Log
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

@Suppress("UNCHECKED_CAST", "DEPRECATION")
class ListManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListManagementBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var listAdapter: ListAdapter
    private var shoppingLists = mutableMapOf<String, ShoppingList>()
    private var realTimeListeners = mutableListOf<ListenerRegistration>()
    private var userName: String = "User" // Default until fetched

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupRecyclerView()
        setupDragAndDrop()
        fetchUserName()
        fetchShoppingLists()

        setSupportActionBar(binding.toolbar)

        val drawerLayout = binding.drawerLayout
        val navView = binding.navigationView

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu)
            title = "SyncMart"
        }

        // Navigation Drawer setup
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
                else -> false
            }
        }

        binding.floatingActionButton.setOnClickListener {
            showModifyDialog(isEditing = false)  // ✅ Correct parameter order
        }
    }

    private fun fetchUserName() {
        val userEmail = auth.currentUser?.email

        if (userEmail.isNullOrEmpty()) {
            showToast("Error: User email is null")
            return
        }

        db.collection("Users").document(userEmail).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userName = document.getString("name")?.split(" ")?.firstOrNull()?.trim() ?: "User"
                    updateToolbarTitle()
                } else {
                    showToast("User profile not found")
                }
            }
            .addOnFailureListener { e ->
                showToast("Failed to fetch user profile")
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

            // 🔹 Force UI update with userName
            binding.toolbarUser.text = "Welcome, $userName"
            Log.d("UIUpdate", "Toolbar title updated to: Welcome, $userName")
        }
    }

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
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupRecyclerView() {
        listAdapter = ListAdapter(
            onSelectionChanged = { isSelectionActive, selectedCount ->
                toggleSelectionMode(isSelectionActive, selectedCount)
            },
            onListClicked = { shoppingList ->
                if (!listAdapter.isSelectionModeActive()) {
                    val intent = Intent(this, ListActivity::class.java)
                    intent.putExtra("LIST_ID", shoppingList.id)
                    startActivity(intent)
                }
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ListManagementActivity)
            adapter = listAdapter
        }
    }

    // Add this to your activity
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Prevent automatic menu inflation by returning true without inflating
        return true
    }

    @SuppressLint("SetTextI18n")
    private fun toggleSelectionMode(isActive: Boolean, selectedCount: Int = 0) {
        if (isActive && selectedCount > 0) {
            binding.toolbarTitle.visibility = View.GONE
            binding.toolbarUser.visibility = View.GONE
            binding.toolbar.title = "$selectedCount Selected"

            binding.toolbar.menu.clear()
            binding.toolbar.inflateMenu(R.menu.selection_menu)

            val loggedInUserEmail = auth.currentUser?.email ?: return

            // Check if the logged-in user owns all selected lists
            val allOwnedByUser = listAdapter.getSelectedItems().all { id ->
                shoppingLists[id]?.owner == loggedInUserEmail
            }

            val deleteItem = binding.toolbar.menu.findItem(R.id.action_delete_selection)
            val editItem = binding.toolbar.menu.findItem(R.id.action_edit_selection)

            deleteItem?.isVisible = allOwnedByUser
            editItem?.isVisible = allOwnedByUser && selectedCount == 1

            binding.toolbar.invalidate() // ✅ Force menu refresh
        } else {
            binding.toolbarTitle.visibility = View.VISIBLE
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
            deleteSelectedItems()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun deleteSelectedItems() {
        val selectedIds = listAdapter.getSelectedItems()
        if (selectedIds.isNotEmpty()) {
            selectedIds.forEach { docId ->
                db.collection("shopping_lists").document(docId).delete()
                    .addOnSuccessListener {
                        showToast("List deleted")
                        fetchShoppingLists()

                        // ✅ Clear selection after last item is deleted
                        if (selectedIds.last() == docId) {
                            listAdapter.clearSelection()
                            toggleSelectionMode(false)
                        }
                    }
                    .addOnFailureListener {
                        showToast("Failed to delete list")
                    }
            }
        }
    }

    private fun editSelectedItem() {
        val selectedIds = listAdapter.getSelectedItems()
        if (selectedIds.size == 1) {
            val shoppingList = shoppingLists[selectedIds.first()]
            shoppingList?.let { showModifyDialog(isEditing = true, existingList = it) }

        }
    }

    private fun fetchShoppingLists() {
        val userEmail = auth.currentUser?.email ?: return

        // Clear old listeners
        realTimeListeners.forEach { it.remove() }
        realTimeListeners.clear()

        val combinedShoppingLists = mutableMapOf<String, ShoppingList>()

        // Helper to push updates to UI
        fun updateUI() {
            shoppingLists.clear()
            shoppingLists.putAll(combinedShoppingLists)
            // Sort the shopping lists by position
            val sortedLists = combinedShoppingLists.values.sortedBy { it.position }
            listAdapter.updateListPreserveSelection(sortedLists.toMutableList())
            toggleSelectionMode(false)

            binding.emptyStateText.visibility =
                if (shoppingLists.isEmpty()) View.VISIBLE else View.GONE
        }

        val ownerListener = db.collection("shopping_lists")
            .whereEqualTo("owner", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreError", "Owner listener error: ${error.message}", error)
                    return@addSnapshotListener
                }

                snapshot?.documents?.forEach { doc ->
                    val list = doc.toObject(ShoppingList::class.java)?.copy(id = doc.id)
                    if (list != null) {
                        combinedShoppingLists[doc.id] = list
                    } else {
                        combinedShoppingLists.remove(doc.id)
                    }
                }

                // Clean up deleted docs
                val currentIds = snapshot?.documents?.map { it.id } ?: emptyList()
                combinedShoppingLists.keys
                    .filter { it !in currentIds && shoppingLists[it]?.owner == userEmail }
                    .forEach { combinedShoppingLists.remove(it) }

                updateUI()
            }

        val accessListener = db.collection("shopping_lists")
            .whereArrayContains("accessEmails", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreError", "Access listener error: ${error.message}", error)
                    return@addSnapshotListener
                }

                snapshot?.documents?.forEach { doc ->
                    val list = doc.toObject(ShoppingList::class.java)?.copy(id = doc.id)
                    if (list != null) {
                        combinedShoppingLists[doc.id] = list
                    } else {
                        combinedShoppingLists.remove(doc.id)
                    }
                }

                // Clean up deleted docs
                val currentIds = snapshot?.documents?.map { it.id } ?: emptyList()
                combinedShoppingLists.keys
                    .filter { it !in currentIds && shoppingLists[it]?.accessEmails?.contains(userEmail) == true }
                    .forEach { combinedShoppingLists.remove(it) }

                updateUI()
            }

        realTimeListeners.add(ownerListener)
        realTimeListeners.add(accessListener)
    }

    private fun showModifyDialog(isEditing: Boolean, existingList: ShoppingList? = null) {
        val dialogBinding = DialogModifyListBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        // ✅ Restore selected emails when editing
        val selectedEmails = existingList?.accessEmails?.toMutableSet() ?: mutableSetOf()

        // ✅ Pre-fill list name if editing
        if (isEditing) {
            dialogBinding.editTextListName.setText(existingList?.listName ?: "")
        }

        val userEmail = auth.currentUser?.email
        if (userEmail == null) {
            Log.e("DEBUG", "User email is null, cannot fetch friends.")
            showToast("Error: User email is null")
            return
        }

        Log.d("DEBUG", "Fetching friends for userEmail: $userEmail")

        // ✅ Fetch friends list from Firestore using real-time listener
        db.collection("Users").document(userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("DEBUG", "Failed to fetch friends list: ${error.message}", error)
                    showToast("Failed to fetch friends. Check your network.")
                    toggleNoFriendsMessage(dialogBinding, true)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    Log.d("DEBUG", "User document found: ${snapshot.data}")

                    val friendsMap = snapshot.get("friendsMap") as? Map<*, *>
                    val validFriendsMap: Map<String, String> = friendsMap
                        ?.filterKeys { it is String }
                        ?.mapNotNull { (key, value) ->
                            if (key is String && value is String) key to value else null
                        }
                        ?.toMap()
                        ?: emptyMap()

                    Log.d("DEBUG", "Processed friends map: $validFriendsMap")
                    updateFriendsUI(dialogBinding, validFriendsMap, selectedEmails)
                } else {
                    Log.e("DEBUG", "User document does not exist in Firestore.")
                    showToast("Error: User profile not found")
                    toggleNoFriendsMessage(dialogBinding, true)
                }
            }

        // ✅ Save list with selected emails
        dialogBinding.btnCreate.setOnClickListener {
            val listName = dialogBinding.editTextListName.text.toString().trim()
            if (listName.isEmpty()) {
                showToast("List name cannot be empty")
                return@setOnClickListener
            }

            if (isEditing) {
                existingList?.id?.let { updateExistingList(it, listName, selectedEmails.toList()) }
            } else {
                createNewList(listName, selectedEmails.toList())
            }

            dialog.dismiss()
        }

        dialogBinding.btnCreate.text = if (isEditing) "Update" else "Create"
    }

    private fun updateFriendsUI(
        dialogBinding: DialogModifyListBinding,
        friendsMap: Map<String, String>,
        selectedEmails: MutableSet<String>
    ) {
        toggleNoFriendsMessage(dialogBinding, friendsMap.isEmpty())

        val friendsList = friendsMap.map { Friend(it.value, it.key) }
        Log.d("DEBUG", "Converted friends list: $friendsList")

        val adapter = FriendSelectionAdapter(
            context = this,
            friendsList = friendsList,
            selectedEmails = selectedEmails,
            onFriendChecked = { friend, isChecked ->
                if (isChecked) selectedEmails.add(friend.email)
                else selectedEmails.remove(friend.email)
                Log.d("DEBUG", "Friend checked: ${friend.email}, isChecked: $isChecked")
            },
            showCheckBox = true
        )

        dialogBinding.listViewMembers.adapter = adapter
        adapter.notifyDataSetChanged()
    }

    private fun toggleNoFriendsMessage(dialogBinding: DialogModifyListBinding, isEmpty: Boolean) {
        dialogBinding.textViewNoFriends.visibility = if (isEmpty) View.VISIBLE else View.GONE
        dialogBinding.listViewMembers.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun createNewList(listName: String, selectedEmails: List<String>) {
        val userEmail = auth.currentUser?.email ?: return
        val newList = mapOf(
            "listName" to listName,
            "owner" to userEmail,
            "accessEmails" to selectedEmails
        )

        db.collection("shopping_lists").add(newList)
            .addOnSuccessListener { showToast("List created successfully") }
            .addOnFailureListener { showToast("Failed to create list") }
    }

    private fun updateExistingList(listId: String, listName: String, selectedEmails: List<String>) {
        val updates = mapOf(
            "listName" to listName, // ✅ Fix: Ensure correct field name
            "accessEmails" to selectedEmails
        )

        db.collection("shopping_lists").document(listId)
            .update(updates)
            .addOnSuccessListener {
                showToast("List updated successfully")
                listAdapter.clearSelection()  // ✅ Deselect all items
                toggleSelectionMode(false)   // ✅ Exit selection mode
            }
            .addOnFailureListener {
                showToast("Failed to update list")
            }
    }

    @SuppressLint("SetTextI18n")
    private fun showDeleteConfirmation(listName: String) {
        val dialogBinding = DialogConfirmBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.listName.text = "Delete '$listName'?"

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnDelete.setOnClickListener {
            deleteList(listName)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun deleteList(listName: String) {
        db.collection("shopping_lists")
            .whereEqualTo("listName", listName)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    db.collection("shopping_lists").document(doc.id).delete()
                        .addOnSuccessListener { showToast("List deleted") }
                        .addOnFailureListener { showToast("Failed to delete list") }
                }
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        realTimeListeners.forEach { it.remove() }
        super.onDestroy()
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        if (listAdapter.isSelectionModeActive()) {
            listAdapter.clearSelection()
            toggleSelectionMode(false) // Exit selection mode
        } else {
            super.onBackPressed() // Default behavior (exit activity)
        }
    }

    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                listAdapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No swipe functionality needed here, so we can leave this empty
            }
        }

        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(binding.recyclerView)
    }


}