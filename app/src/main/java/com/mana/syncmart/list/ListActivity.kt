package com.mana.syncmart.list

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mana.syncmart.dataclass.ShoppingList
import com.mana.syncmart.dashboard.ListManagementActivity
import com.mana.syncmart.databinding.ActivityListBinding
import com.mana.syncmart.databinding.DialogAddItemsBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.collections.get

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

        binding.toolbar.title = "Loading..."
        
        // Process the initial intent
        processIntent(intent)
        
        binding.addElementButton.setOnClickListener { showAddItemsDialog() }
        binding.shareListButton.setOnClickListener { sharePendingItems() }
        checkAndClearFinishedItems()
        
        // Set up back button to go to ListManagementActivity when coming from notification
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(this@ListActivity, ListManagementActivity::class.java)
                    .apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK })
                finish()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntent(intent)
    }
    
    private fun processIntent(intent: Intent) {
        // Try to get list ID from either extra
        val newListId = intent.getStringExtra("LIST_ID") ?: intent.getStringExtra("list_id")
        
        if (!newListId.isNullOrEmpty()) {
            // Only update if this is a different list
            if (newListId != listId) {
                listId = newListId
                loadListData(newListId)
            }
        } else if (listId == null) {
            fallbackSetup()
        }
    }
    
    private fun loadListData(id: String) {
        db.collection("shopping_lists").document(id).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    currentListName = doc.getString("listName") ?: "Shopping List"
                    binding.toolbar.title = currentListName
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

        val user = FirebaseAuth.getInstance().currentUser
        val userEmail = user?.email ?: "unknown"

        val sanitizedItems = items.map { it.trim() }.filter { it.isNotBlank() }
        if (sanitizedItems.isEmpty()) return

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val now = System.currentTimeMillis()
        
        val newItems = sanitizedItems.mapIndexed { index, itemName ->
            val timestampString = sdf.format(Date(now + index * 1000L))
            val autoId = db.collection("shopping_lists").document().id
            autoId to mapOf(
                "name" to itemName,
                "addedBy" to userEmail,
                "addedAt" to timestampString,
                "important" to false,
                "pending" to true
            )
        }.toMap()

        // Update Firestore
        listId?.let { id ->
            db.collection("shopping_lists").document(id)
                .set(mapOf("items" to newItems), SetOptions.merge())
                .addOnFailureListener { e ->
                    // Consider showing an error message to the user
                }
        }
    }


    private fun sharePendingItems() {
        listId?.let { it ->
            db.collection("shopping_lists").document(it).get().addOnSuccessListener { doc ->
                val items = (doc["items"] as? Map<*, *>)?.mapNotNull { entry ->
                    val map = entry.value as? Map<*, *> ?: return@mapNotNull null
                    val pending = map["pending"] as? Boolean != false
                    val name = map["name"] as? String ?: return@mapNotNull null
                    if (pending) name else null
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

    private fun checkAndClearFinishedItems() {
        val id = listId ?: return
        val prefs = getSharedPreferences("SyncMartPrefs", MODE_PRIVATE)
        val key = "lastClearedDate_$id"
        val now = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val last = prefs.getString(key, null) ?: run { prefs.edit { putString(key, now) }; return }

        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val days = ((sdf.parse(now)!!.time - sdf.parse(last)!!.time) / (1000 * 60 * 60 * 24)).toInt()
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (days >= 2 || (days == 1 && hour >= 23)) {
                clearFinishedItems(key, now)
            }
        } catch (_: Exception) {}
    }

    private fun clearFinishedItems(key: String, date: String) {
        listId?.let { listId ->
            val docRef = db.collection("shopping_lists").document(listId)
            docRef.get().addOnSuccessListener { doc ->
                val shoppingList = doc.toObject(ShoppingList::class.java)
                val items = shoppingList?.items ?: emptyMap()

                val updatedItems = items.filterValues { it.pending }

                docRef.update("items", updatedItems)
                    .addOnSuccessListener {
                        getSharedPreferences("SyncMartPrefs", MODE_PRIVATE).edit {
                            putString(key, date)
                        }
                    }
            }
        }
    }
}
