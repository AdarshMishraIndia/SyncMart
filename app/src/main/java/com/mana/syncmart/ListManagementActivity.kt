package com.mana.syncmart

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mana.syncmart.databinding.ActivityListManagementBinding
import com.mana.syncmart.databinding.DialogConfirmBinding
import com.mana.syncmart.databinding.DialogModifyListBinding

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

        binding.floatingActionButton.setOnClickListener {
            showModifyDialog(isEditing = false)
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
                else -> false
            }
        }
    }

    private fun fetchUserName() {
        val userEmail = auth.currentUser?.email ?: return showToast("Error: User email is null")

        db.collection("Users").document(userEmail).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userName = document.getString("name")?.split(" ")?.firstOrNull()?.trim() ?: "User"
                    updateToolbarTitle()
                } else showToast("User profile not found")
            }
            .addOnFailureListener { showToast("Failed to fetch user profile") }
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
        showToast("Logged out successfully")
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
            binding.toolbarTitle.visibility = View.GONE
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
            deleteSelectedItems(selectedIds)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun deleteSelectedItems(selectedIds: List<String>) {
        selectedIds.forEachIndexed { index, docId ->
            db.collection("shopping_lists").document(docId).delete()
                .addOnSuccessListener {
                    // Remove from your local cache/map if applicable
                    shoppingLists.remove(docId)

                    // Correct usage: pass the actual docId
                    listAdapter.removeItemById(docId)

                    // When all deletions are done
                    if (index == selectedIds.lastIndex) {
                        listAdapter.clearSelection()
                        toggleSelectionMode(false)
                        binding.emptyStateText.visibility =
                            if (shoppingLists.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                .addOnFailureListener {
                    showToast("Failed to delete list: $docId")
                }
        }
    }



    private fun editSelectedItem() {
        val selectedId = listAdapter.getSelectedItems().singleOrNull() ?: return
        shoppingLists[selectedId]?.let { showModifyDialog(isEditing = true, existingList = it) }
    }

    private fun fetchShoppingLists() {
        val userEmail = auth.currentUser?.email ?: return
        val combinedLists = mutableMapOf<String, ShoppingList>()

        fun updateUI() {
            val newSorted = combinedLists.values.sortedBy { it.position }
            if (newSorted != listAdapter.currentList) {
                shoppingLists.clear()
                newSorted.forEach { shoppingLists[it.id] = it }
                listAdapter.updateListPreserveSelection(newSorted.toMutableList())
                toggleSelectionMode(false)
                binding.emptyStateText.visibility = if (shoppingLists.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        realTimeListeners.forEach { it.remove() }
        realTimeListeners.clear()

        val ownerListener = db.collection("shopping_lists")
            .whereEqualTo("owner", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                snapshot?.documents?.forEach { doc ->
                    doc.toObject(ShoppingList::class.java)?.copy(id = doc.id)?.let {
                        combinedLists[doc.id] = it
                    }
                }
                updateUI()
            }

        val accessListener = db.collection("shopping_lists")
            .whereArrayContains("accessEmails", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                snapshot?.documents?.forEach { doc ->
                    doc.toObject(ShoppingList::class.java)?.copy(id = doc.id)?.let {
                        combinedLists[doc.id] = it
                    }
                }
                updateUI()
            }

        realTimeListeners.addAll(listOf(ownerListener, accessListener))
    }

    private fun showModifyDialog(isEditing: Boolean, existingList: ShoppingList? = null) {
        val dialogBinding = DialogModifyListBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        val selectedEmails = existingList?.accessEmails?.toMutableSet() ?: mutableSetOf()
        dialogBinding.editTextListName.setText(existingList?.listName ?: "")

        val userEmail = auth.currentUser?.email ?: return showToast("Error: User email is null")

        db.collection("Users").document(userEmail).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
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
            if (name.isEmpty()) return@setOnClickListener showToast("List name cannot be empty")
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
        db.collection("shopping_lists").add(newList).addOnSuccessListener {
            showToast("List created successfully")
        }.addOnFailureListener { showToast("Failed to create list") }
    }

    private fun updateExistingList(listId: String, name: String, accessEmails: List<String>) {
        val updates = mapOf("listName" to name, "accessEmails" to accessEmails)
        db.collection("shopping_lists").document(listId).update(updates).addOnSuccessListener {
            showToast("List updated successfully")
            listAdapter.clearSelection()
            toggleSelectionMode(false)
        }.addOnFailureListener { showToast("Failed to update list") }
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        realTimeListeners.forEach { it.remove() }
        super.onDestroy()
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
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
}
