package com.mana.syncmart.list

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mana.syncmart.dashboard.ListManagementActivity
import com.mana.syncmart.databinding.ActivityListBinding
import com.mana.syncmart.databinding.DialogAddItemsBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListBinding
    private val db = FirebaseFirestore.getInstance()
    private var listId: String? = null
    private var currentListName = "Shopping List"
    private val menuDeleteId = 1001
    private val tag = "ListActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.toolbar.title = "Loading..."

        processIntent(intent)

        binding.addElementButton.setOnClickListener { showAddItemsDialog() }
        binding.shareListButton.setOnClickListener { sharePendingItems() }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntent(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        handleBackNavigation()
        return true
    }

    private fun handleBackNavigation() {
        val pendingFragment = supportFragmentManager.findFragmentByTag("f0") as? PendingItemsFragment
        if (pendingFragment?.isSelectionActive() == true) {
            pendingFragment.clearSelectionFromActivity()
        } else {
            navigateToListManagement()
        }
    }

    private fun navigateToListManagement() {
        startActivity(Intent(this, ListManagementActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    private fun processIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data
            Log.d(tag, "Processing deep link URI: $uri")

            if (uri?.scheme == "myapp" && uri.host == "list") {
                val deepLinkListId = uri.lastPathSegment
                if (!deepLinkListId.isNullOrEmpty() && deepLinkListId != listId) {
                    Log.d(tag, "Extracted list ID from deep link: $deepLinkListId")
                    listId = deepLinkListId
                    loadListData(deepLinkListId)
                    return
                }
            }
        }

        val newListId = intent.getStringExtra("LIST_ID") ?: intent.getStringExtra("list_id")

        if (!newListId.isNullOrEmpty()) {
            if (newListId != listId) {
                Log.d(tag, "Loading list from intent extra: $newListId")
                listId = newListId
                loadListData(newListId)
            }
        } else if (listId == null) {
            Log.w(tag, "No list ID found, using fallback")
            fallbackSetup()
        }
    }

    private fun loadListData(id: String) {
        db.collection("shopping_lists").document(id).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    currentListName = doc.getString("listName") ?: "Shopping List"
                    binding.toolbar.title = currentListName

                    // Backfill list-level lastModified if missing
                    if (doc.getTimestamp("lastModified") == null) {
                        val fallback = doc.getTimestamp("createdAt") ?: Timestamp.now()
                        db.collection("shopping_lists").document(id)
                            .update("lastModified", fallback)
                            .addOnFailureListener { e ->
                                Log.w(tag, "Failed to backfill list lastModified: ${e.message}")
                            }
                    }

                    setupViewPager(id)
                } else fallbackSetup()
            }
            .addOnFailureListener { fallbackSetup() }
    }

    private fun setupViewPager(id: String) {
        binding.viewPager.adapter = ViewPagerAdapter(this, id, currentListName)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = if (pos == 0) "Pending" else "Finished"
        }.attach()
    }

    private fun fallbackSetup() {
        binding.toolbar.title = "Shopping List"
        setupViewPager("fallback")
    }

    fun enterSelectionMode() {
        binding.toolbar.title = "Select Items"
        binding.toolbar.menu.clear()
        binding.toolbar.menu.add(Menu.NONE, menuDeleteId, Menu.NONE, "Delete")
            .setIcon(android.R.drawable.ic_menu_delete)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == menuDeleteId) {
                val frag = supportFragmentManager.findFragmentByTag("f0") as? PendingItemsFragment
                frag?.showConfirmDeleteDialog()
                true
            } else false
        }
    }

    fun updateSelectionCount(count: Int) {
        binding.toolbar.subtitle = if (count > 0) "$count selected" else null
        binding.toolbar.setSubtitleTextColor(ContextCompat.getColor(this, android.R.color.white))
    }

    fun exitSelectionMode() {
        binding.toolbar.title = currentListName
        binding.toolbar.menu.clear()
        binding.toolbar.subtitle = null
    }

    private fun showAddItemsDialog() {
        val dialogBinding = DialogAddItemsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        dialog.show()

        dialogBinding.editTextTextMultiLine.requestFocus()

        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(dialogBinding.editTextTextMultiLine, InputMethodManager.SHOW_IMPLICIT)

        dialogBinding.btnSend.setOnClickListener {
            val items = dialogBinding.editTextTextMultiLine.text.toString()
                .trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (items.isNotEmpty()) addItemsToList(items)
            dialog.dismiss()
        }
    }

    private fun addItemsToList(items: List<String>) {
        if (items.isEmpty()) return

        val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: "unknown"
        val sanitizedItems = items.map { it.trim() }.filter { it.isNotBlank() }
        if (sanitizedItems.isEmpty()) return

        val id = listId ?: return

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val nowMs = System.currentTimeMillis()
        val listTimestamp = Timestamp.now()

        // Dot-notation keys merge each item field individually — does NOT overwrite existing items
        val updates = hashMapOf<String, Any>("lastModified" to listTimestamp)

        sanitizedItems.forEachIndexed { index, itemName ->
            val itemMs = nowMs + index * 1000L
            val timestampString = sdf.format(Date(itemMs))
            val itemTimestamp = Timestamp(Date(itemMs))
            val autoId = db.collection("shopping_lists").document().id

            updates["items.$autoId.name"] = itemName
            updates["items.$autoId.addedBy"] = userEmail
            updates["items.$autoId.addedAt"] = timestampString
            updates["items.$autoId.important"] = false
            updates["items.$autoId.pending"] = true
            updates["items.$autoId.active"] = true
            updates["items.$autoId.lastModified"] = itemTimestamp
        }

        db.collection("shopping_lists").document(id)
            .update(updates)
            .addOnFailureListener { e -> Log.e(tag, "Failed to add items", e) }
    }

    private fun sharePendingItems() {
        listId?.let { id ->
            db.collection("shopping_lists").document(id).get().addOnSuccessListener { doc ->
                val items = (doc["items"] as? Map<*, *>)?.mapNotNull { entry ->
                    val map = entry.value as? Map<*, *> ?: return@mapNotNull null
                    val active = map["active"] as? Boolean ?: true
                    val pending = map["pending"] as? Boolean ?: true
                    val name = map["name"] as? String ?: return@mapNotNull null
                    if (active && pending) name else null
                } ?: emptyList()

                if (items.isNotEmpty()) {
                    startActivity(Intent.createChooser(Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, items.joinToString("\n") { "• $it" })
                    }, "Share List"))
                }
            }
        }
    }
}