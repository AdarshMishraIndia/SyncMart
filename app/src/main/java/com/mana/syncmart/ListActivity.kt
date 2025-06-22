package com.mana.syncmart

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mana.syncmart.databinding.ActivityListBinding
import com.mana.syncmart.databinding.DialogAddItemsBinding
import java.text.SimpleDateFormat
import java.util.*

@Suppress("DEPRECATION")
class ListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListBinding
    private lateinit var db: FirebaseFirestore
    private var listId: String? = null
    private var currentListName = "Shopping List"
    private val menuDeleteId = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        db = FirebaseFirestore.getInstance()
        listId = intent.getStringExtra("LIST_ID")
        binding.toolbar.title = "Loading..."

        if (listId == null) {
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
                startActivity(Intent(this@ListActivity, ListManagementActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                })
                finish()
            }
        })
    }

    private fun loadListData(id: String) {
        db.collection("shopping_lists").document(id).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("listName") ?: "Shopping List"
                    currentListName = name
                    binding.toolbar.title = name
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

    private fun showAddItemsDialog() {
        val dialogBinding = DialogAddItemsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        // Request focus and show keyboard
        dialogBinding.editTextTextMultiLine.apply {
            requestFocus()
            post {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }

        dialogBinding.btnSend.setOnClickListener {
            val items = dialogBinding.editTextTextMultiLine.text.toString()
                .trim().lines().map { it.trim() }.filter { it.isNotEmpty() }

            if (items.isEmpty()) showToast("❌ Enter at least one item.")
            else addItemsToPendingList(items)

            dialog.dismiss()
        }
    }

    private fun addItemsToPendingList(items: List<String>) {
        listId?.let {
            db.collection("shopping_lists").document(it)
                .set(mapOf("pendingItems" to items.associateWith { false }), SetOptions.merge())
                .addOnFailureListener { showToast("❌ Failed to add items.") }
        }
    }

    private fun sharePendingItems() {
        listId?.let {
            db.collection("shopping_lists").document(it).get()
                .addOnSuccessListener { doc ->
                    val items = (doc["pendingItems"] as? Map<*, *>)?.keys?.filterIsInstance<String>().orEmpty()
                    if (items.isEmpty()) showToast("❌ No items to share.")
                    else {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, items.joinToString("\n") { "• $it" })
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share List"))
                    }
                }
                .addOnFailureListener { showToast("❌ Unable to fetch items.") }
        }
    }

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
        listId?.let {
            db.collection("shopping_lists").document(it)
                .update("finishedItems", emptyList<String>())
                .addOnSuccessListener {
                    getSharedPreferences("SyncMartPrefs", MODE_PRIVATE).edit {
                        putString(key, date)
                    }
                }
                .addOnFailureListener { showToast("❌ Clearing error: ${it.message}") }
        }
    }

    private fun showToast(msg: String) {
        val layout = LayoutInflater.from(this).inflate(R.layout.custom_toast_layout, findViewById(android.R.id.content), false)
        layout.findViewById<TextView>(R.id.toast_text).text = msg
        Toast(this).apply {
            duration = Toast.LENGTH_LONG
            setGravity(Gravity.CENTER, 0, 0)
            view = layout
            show()
        }
    }

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
        if (binding.toolbar.title == "Loading...") {
            currentListName = "Shopping List"
            binding.toolbar.title = currentListName
        }
        listId?.let { setupViewPager(it) }
    }
}