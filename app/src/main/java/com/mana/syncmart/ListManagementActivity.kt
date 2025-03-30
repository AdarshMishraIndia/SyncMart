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
            showModifyDialog(null, isEditing = false)
        }
    }

    private fun fetchUserName() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userName = document.getString("name") ?: "User"
                    updateToolbarTitle()
                }
            }
            .addOnFailureListener {
                showToast("Failed to fetch user name")
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
            binding.toolbar.title = "SyncMart"
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
            shoppingLists.values.toMutableList(),
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

            binding.toolbar.invalidate() // ✅ Force menu refresh

            val editItem = binding.toolbar.menu.findItem(R.id.action_edit_selection)
            editItem?.isVisible = (selectedCount == 1)
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
                        fetchShoppingLists() // ✅ Force real-time data refresh
                    }
                    .addOnFailureListener {
                        showToast("Failed to delete list")
                    }
            }
            listAdapter.clearSelection()
            toggleSelectionMode(false)
        }
    }



    private fun editSelectedItem() {
        val selectedIds = listAdapter.getSelectedItems()
        if (selectedIds.size == 1) {
            val shoppingList = shoppingLists[selectedIds.first()]
            shoppingList?.let { showModifyDialog(it, isEditing = true) }
        }
    }

    // ✅ FETCH LISTS WHERE USER IS EITHER OWNER OR IN accessEmails
    private fun fetchShoppingLists() {
        val userEmail = auth.currentUser?.email ?: return

        // Remove old listeners to prevent duplication
        realTimeListeners.forEach { it.remove() }
        realTimeListeners.clear()

        val combinedShoppingLists = mutableMapOf<String, ShoppingList>()

        // Listen for lists where the user is the owner
        val ownerListener = db.collection("shopping_lists")
            .whereEqualTo("owner", userEmail)
            .addSnapshotListener { ownerSnapshot, error ->
                if (error != null) {
                    showToast("Failed to fetch lists")
                    return@addSnapshotListener
                }

                ownerSnapshot?.documents?.forEach { doc ->
                    val list = doc.toObject(ShoppingList::class.java)?.copy(id = doc.id)
                    list?.let { combinedShoppingLists[doc.id] = it }
                }

                updateUI(combinedShoppingLists)
            }

        // Listen for lists where the user has access
        val accessListener = db.collection("shopping_lists")
            .whereArrayContains("accessEmails", userEmail)
            .addSnapshotListener { accessSnapshot, error ->
                if (error != null) {
                    showToast("Failed to fetch lists")
                    return@addSnapshotListener
                }

                accessSnapshot?.documents?.forEach { doc ->
                    val list = doc.toObject(ShoppingList::class.java)?.copy(id = doc.id)
                    list?.let { combinedShoppingLists[doc.id] = it }
                }

                updateUI(combinedShoppingLists)
            }

        // Store listeners for cleanup
        realTimeListeners.add(ownerListener)
        realTimeListeners.add(accessListener)
    }

    private fun updateUI(updatedLists: Map<String, ShoppingList>) {
        shoppingLists.clear()
        shoppingLists.putAll(updatedLists)
        listAdapter.updateList(shoppingLists.values.toMutableList())

        // ✅ Ensure selection mode exits when all items are deselected
        if (listAdapter.getSelectedItems().isEmpty()) {
            toggleSelectionMode(false)
        }

        binding.emptyStateText.visibility =
            if (shoppingLists.isEmpty()) View.VISIBLE else View.GONE
    }



    @SuppressLint("NotifyDataSetChanged")
    private fun showModifyDialog(existingList: ShoppingList?, isEditing: Boolean) {
        val dialogBinding = DialogModifyListBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        if (isEditing && existingList != null) {
            dialogBinding.editTextListName.setText(existingList.listName)
        }

        // Fetch user's friends
        val userEmail = auth.currentUser?.email ?: return
        db.collection("users").document(userEmail).get()
            .addOnSuccessListener { document ->
                val friendsMap = document.get("friendsMap") as? Map<String, String> ?: emptyMap()
                toggleNoFriendsMessage(dialogBinding, friendsMap.isEmpty())
            }
            .addOnFailureListener {
                showToast("Failed to fetch friends list")
                toggleNoFriendsMessage(dialogBinding, true) // Assume empty in case of failure
            }

        dialogBinding.btnCreate.setOnClickListener {
            val listName = dialogBinding.editTextListName.text.toString().trim()
            if (listName.isEmpty()) {
                showToast("List name cannot be empty")
                return@setOnClickListener
            }

            if (!isEditing) {
                createNewList(listName, emptyList()) // Adjust this if selecting friends is needed
            } else {
                existingList?.id?.let { updateExistingList(it, listName) }
            }
            dialog.dismiss()
        }

        dialogBinding.btnCreate.text = if (isEditing) "Update" else "Create"
        dialog.show()
    }



    private fun toggleNoFriendsMessage(dialogBinding: DialogModifyListBinding, isEmpty: Boolean) {
        dialogBinding.textViewNoFriends.visibility = if (isEmpty) View.VISIBLE else View.GONE
        dialogBinding.listViewMembers.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }


    private fun createNewList(listName: String, accessEmails: List<String>) {
        val userEmail = auth.currentUser?.email ?: return

        val newList = hashMapOf(
            "listName" to listName,
            "accessEmails" to accessEmails,  // Members only (owner excluded)
            "owner" to userEmail             // Owner stored separately
        )

        db.collection("shopping_lists").add(newList)
            .addOnSuccessListener { showToast("List created") }
            .addOnFailureListener { showToast("Failed to create list") }
    }

    private fun updateExistingList(docId: String, newListName: String) {
        db.collection("shopping_lists").document(docId)
            .update("listName", newListName)
            .addOnSuccessListener { showToast("List updated") }
            .addOnFailureListener { showToast("Failed to update list") }
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

}