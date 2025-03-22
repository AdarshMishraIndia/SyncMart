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

@Suppress("UNCHECKED_CAST")
class ListManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListManagementBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var listAdapter: ListAdapter
    private var shoppingLists = mutableMapOf<String, ShoppingList>()
    private var realTimeListeners = mutableListOf<ListenerRegistration>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupRecyclerView()
        fetchShoppingLists() // ✅ Fetch lists where user is either in accessEmails OR owner

        setSupportActionBar(binding.toolbar)

        val drawerLayout = binding.drawerLayout
        val navView = binding.navigationView

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)  // Enable the home button
            setHomeAsUpIndicator(R.drawable.ic_menu) // Set hamburger icon
            title = "SyncMart"  // Ensure title is set
        }

        // ✅ Open drawer when clicking hamburger icon
        binding.toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // ✅ Handle navigation drawer item clicks
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_friends -> {
                    drawerLayout.closeDrawer(GravityCompat.START) // Close drawer
                    startActivity(Intent(this, FriendActivity::class.java)) // Open FriendActivity
                    true
                }
                R.id.nav_logout -> {
                    drawerLayout.closeDrawer(GravityCompat.START) // Close drawer before logging out
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


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return true // No options menu needed
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                binding.drawerLayout.openDrawer(GravityCompat.START)
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
            onDeleteClicked = { listName -> showDeleteConfirmation(listName) },
            onModifyClicked = { shoppingList -> showModifyDialog(shoppingList, isEditing = true) },
            onListClicked = { shoppingList ->
                val intent = Intent(this, ListActivity::class.java)
                intent.putExtra("LIST_ID", shoppingList.id)
                startActivity(intent)
            },
            loggedInUserEmail = auth.currentUser?.email ?: ""
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ListManagementActivity)
            adapter = listAdapter
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

        binding.emptyStateText.visibility =
            if (shoppingLists.isEmpty()) View.VISIBLE else View.GONE
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun showModifyDialog(existingList: ShoppingList?, isEditing: Boolean) {
        val dialogBinding = DialogModifyListBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val friends = mutableListOf<Friend>()
        val selectedEmails = existingList?.accessEmails?.toMutableSet() ?: mutableSetOf()

        // ✅ Use the modified ListView adapter
        val friendAdapter = FriendSelectionAdapter(this, friends, selectedEmails, { friend, isChecked ->
            if (isChecked) selectedEmails.add(friend.email) else selectedEmails.remove(friend.email)
        }, true) // 🔹 Show checkboxes


        // ✅ Pre-fill the EditText with existing list name if editing
        if (isEditing && existingList != null) {
            dialogBinding.editTextListName.setText(existingList.listName)
        }

        // ✅ Set adapter directly to ListView (No LayoutManager needed)
        dialogBinding.listViewMembers.adapter = friendAdapter

        db.collection("Users").document(auth.currentUser?.email ?: return).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val friendsMap = document.get("friendsMap") as? Map<String, String> ?: emptyMap()

                    friends.clear()
                    friendsMap.forEach { (email, name) ->
                        if (email != "name") { // Ignore the "name" field inside friendsMap
                            friends.add(Friend(name, email))
                        }
                    }

                    runOnUiThread {
                        friendAdapter.notifyDataSetChanged() // Refresh ListView
                        toggleNoFriendsMessage(dialogBinding, friends.isEmpty()) // Show/hide message
                    }
                } else {
                    showToast("User document not found")
                }
            }
            .addOnFailureListener { e ->
                showToast("Failed to load friends: ${e.message}")
            }


        dialogBinding.btnCreate.setOnClickListener {
            val listName = dialogBinding.editTextListName.text.toString().trim()
            if (listName.isEmpty()) {
                showToast("List name cannot be empty")
                return@setOnClickListener
            }

            val validEmails = selectedEmails.toList() // Get selected members

            if (!isEditing) {
                createNewList(listName, validEmails)
            } else {
                existingList?.id?.let { updateExistingList(it, listName, validEmails) }
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

    private fun updateExistingList(docId: String, newListName: String, emails: List<String>) {
        db.collection("shopping_lists").document(docId)
            .update(mapOf(
                "listName" to newListName,
                "accessEmails" to emails  // Keep owner unchanged
            ))
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
}