package com.mana.syncmart

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mana.syncmart.databinding.ActivityListBinding
import com.mana.syncmart.databinding.DialogAddItemsBinding
import java.text.SimpleDateFormat
import java.util.*

@Suppress("DEPRECATION")
class ListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListBinding
    private val db = FirebaseFirestore.getInstance()
    private var listId: String? = null
    private var currentListName = "Shopping List"
    private val menuDeleteId = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        listId = intent.getStringExtra("LIST_ID")
        binding.toolbar.title = "Loading..."

        if (listId.isNullOrEmpty()) {
            showToast("❌ Invalid list ID.")
            fallbackSetup()
        } else {
            loadListData(listId!!)
        }

        binding.addElementButton.setOnClickListener { showAddItemsDialog() }
        binding.shareListButton.setOnClickListener { sharePendingItems() }
        checkAndClearFinishedItems()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(
                    Intent(this@ListActivity, ListManagementActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
                finish()
            }
        })
    }

    /** Load list metadata and setup ViewPager */
    private fun loadListData(id: String) {
        db.collection("shopping_lists").document(id).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    currentListName = doc.getString("listName") ?: "Shopping List"
                    binding.toolbar.title = currentListName
                    setupViewPager(id)
                } else {
                    showToast("❌ List not found. Using default.")
                    fallbackSetup()
                }
            }
            .addOnFailureListener {
                showToast("❌ Failed to fetch list.")
                fallbackSetup()
            }
    }

    private fun setupViewPager(id: String) {
        binding.viewPager.adapter = ViewPagerAdapter(this, id, currentListName)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = if (pos == 0) "Pending" else "Finished"
        }.attach()
    }

    /** Show dialog to add new items */
    private fun showAddItemsDialog() {
        val dialogBinding = DialogAddItemsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.setOnShowListener {
            dialogBinding.editTextTextMultiLine.requestFocus()
            Handler(mainLooper).postDelayed({
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(dialogBinding.editTextTextMultiLine, InputMethodManager.SHOW_IMPLICIT)
            }, 100)
        }

        dialog.show()

        dialogBinding.btnSend.setOnClickListener {
            val items = dialogBinding.editTextTextMultiLine.text.toString()
                .trim().lines().map { it.trim() }.filter { it.isNotEmpty() }

            if (items.isEmpty()) showToast("❌ Enter at least one item.")
            else addItemsToList(items)

            dialog.dismiss()
        }
    }

    /** Add items to Firestore */
    /** Add items to Firestore with actual user name instead of UID/email */
    private fun addItemsToList(items: List<String>) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val email = user?.email
        if (email.isNullOrEmpty()) {
            showToast("❌ Not signed in.")
            return
        }

        val userRef = db.collection("Users").document(email)
        userRef.get()
            .addOnSuccessListener { userDoc ->
                if (!userDoc.exists()) {
                    showToast("❌ User not found in database.")
                    return@addOnSuccessListener
                }

                val userName = userDoc.getString("name") ?: "Unknown"

                listId?.let { id ->
                    val newItems = items.associateWith {
                        ShoppingItem(
                            addedBy = userName,
                            addedAt = Timestamp.now(),
                            important = false,
                            pending = true
                        )
                    }

                    db.collection("shopping_lists").document(id)
                        .set(mapOf("items" to newItems), SetOptions.merge())
                        .addOnSuccessListener { showToast("✅ Items added.") }
                        .addOnFailureListener { showToast("❌ Failed to add items.") }
                }
            }
            .addOnFailureListener {
                showToast("❌ Failed to fetch user info: ${it.message}")
            }
    }



    /** Share pending items */
    private fun sharePendingItems() {
        listId?.let { id ->
            db.collection("shopping_lists").document(id).get()
                .addOnSuccessListener { doc ->
                    val itemsMap = safeItemsMap(doc)
                    val pendingItems = itemsMap.filter { it.value.pending }.keys.toList()

                    if (pendingItems.isEmpty()) showToast("❌ No items to share.")
                    else {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, pendingItems.joinToString("\n") { "• $it" })
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share List"))
                    }
                }
        }
    }

    /** Check if finished items need clearing */
    private fun checkAndClearFinishedItems() {
        val id = listId ?: return showToast("❌ listId is null")
        val prefs = getSharedPreferences("SyncMartPrefs", MODE_PRIVATE)
        val key = "lastClearedDate_$id"
        val now = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val last = prefs.getString(key, null)

        if (last == null) {
            prefs.edit { putString(key, now) }
            return
        }

        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val days = ((sdf.parse(now)!!.time - sdf.parse(last)!!.time) / (1000 * 60 * 60 * 24)).toInt()
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (days >= 2 || (days == 1 && hour >= 23)) clearFinishedItems(key, now)
        } catch (e: Exception) {
            showToast("❌ Date parsing error: ${e.message}")
        }
    }

    private fun clearFinishedItems(key: String, date: String) {
        listId?.let { id ->
            db.collection("shopping_lists").document(id).get()
                .addOnSuccessListener { doc ->
                    val itemsMap = safeItemsMap(doc)
                    val pendingOnly = itemsMap.filter { it.value.pending }
                    db.collection("shopping_lists").document(id)
                        .set(mapOf("items" to pendingOnly), SetOptions.merge())
                        .addOnSuccessListener {
                            getSharedPreferences("SyncMartPrefs", MODE_PRIVATE).edit {
                                putString(key, date)
                            }
                        }
                        .addOnFailureListener { showToast("❌ Clearing error: ${it.message}") }
                }
        }
    }

    /** Safe conversion from Firestore doc to Map<String, ShoppingItem> */
    private fun safeItemsMap(doc: com.google.firebase.firestore.DocumentSnapshot): Map<String, ShoppingItem> {
        val raw = doc["items"] as? Map<*, *> ?: return emptyMap()
        return raw.mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val map = v as? Map<*, *> ?: return@mapNotNull null
            val addedBy = map["addedBy"] as? String ?: ""
            val addedAt = map["addedAt"] as? Timestamp ?: Timestamp.now()
            val important = map["important"] as? Boolean == true
            val pending = map["pending"] as? Boolean != false
            key to ShoppingItem(addedBy, addedAt, important, pending)
        }.sortedBy { it.second.addedAt?.seconds } // sort by timestamp
            .toMap() // convert back to Map<String, ShoppingItem>
    }

    /** Toolbar selection handling */
    fun enterSelectionMode() {
        binding.toolbar.apply {
            title = "Select Items"
            menu.clear()
            menu.add(Menu.NONE, menuDeleteId, Menu.NONE, "Delete").apply {
                setIcon(android.R.drawable.ic_menu_delete)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }
            setOnMenuItemClickListener {
                if (it.itemId == menuDeleteId) {
                    (supportFragmentManager.findFragmentByTag("f0") as? PendingItemsFragment)?.showConfirmDeleteDialog()
                    true
                } else false
            }
        }
    }

    fun updateSelectionCount(count: Int) {
        binding.toolbar.title = "$count selected"
    }

    fun exitSelectionMode() {
        binding.toolbar.menu.clear()
        binding.toolbar.title = currentListName
    }

    private fun fallbackSetup() {
        binding.toolbar.title = "Shopping List"
        setupViewPager("default_list_id")
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
