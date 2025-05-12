package com.mana.syncmart

// 🔹 Core Android Functionality
import android.app.Dialog
import android.os.Bundle
import android.content.Intent
import android.view.Gravity
import android.view.LayoutInflater

// 🔹 UI Components & User Interaction
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast

// 🔹 Firebase Firestore Integration
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

// 🔹 Activity Lifecycle & Navigation
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback

// 🔹 ViewPager & TabLayout (UI Navigation)
import com.google.android.material.tabs.TabLayoutMediator
import com.mana.syncmart.databinding.ActivityListBinding

// 🔹 Date & Time Handling
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit

class ListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListBinding
    private lateinit var db: FirebaseFirestore
    private var listId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        listId = intent.getStringExtra("LIST_ID")

        setupViewPager()
        setupAddElementButton()
        binding.shareListButton.setOnClickListener {
            sharePendingItems()
        }

        // Check and clear finished items if the date has changed
        checkAndClearFinishedItems()

        // Handle back button press using OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@ListActivity, ListManagementActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
        })
    }

    private fun checkAndClearFinishedItems() {
        if (listId == null) {
            showCustomToast("❌ listId is null, cannot check for cleanup.")
            return
        }

        val sharedPreferences = getSharedPreferences("SyncMartPrefs", MODE_PRIVATE)
        val key = "lastClearedDate_$listId"
        val lastClearedDate = sharedPreferences.getString(key, null)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        if (lastClearedDate == null) {
            // First-time setup
            sharedPreferences.edit { putString(key, currentDate) }
            return
        }

        try {
            val lastDate = dateFormat.parse(lastClearedDate)
            val today = dateFormat.parse(currentDate)

            val daysDiff = if (lastDate != null && today != null) {
                ((today.time - lastDate.time) / (1000 * 60 * 60 * 24)).toInt()
            } else {
                showCustomToast("❌ Failed to calculate date difference. Skipping cleanup.")
                return
            }

            when {
                daysDiff >= 2 -> {
                    // More than one day passed, clean immediately
                    clearFinishedItemsForList(key, currentDate)
                }
                daysDiff == 1 && currentHour >= 23 -> {
                    // One day passed and it's after 11PM
                    clearFinishedItemsForList(key, currentDate)
                }
            }
        } catch (e: Exception) {
            showCustomToast("❌ Date parsing error: ${e.message}")
        }
    }

    private fun clearFinishedItemsForList(prefKey: String, currentDate: String) {
        listId?.let { id ->
            db.collection("shopping_lists").document(id)
                .update("finishedItems", arrayListOf<String>())
                .addOnSuccessListener {
                    val sharedPreferences = getSharedPreferences("SyncMartPrefs", MODE_PRIVATE)
                    sharedPreferences.edit { putString(prefKey, currentDate) }
                }
                .addOnFailureListener { e ->
                    showCustomToast("❌ Error clearing finished items: ${e.message}")
                }
        } ?: showCustomToast("❌ listId is null, cannot clear finished items.")
    }

    private fun setupViewPager() {
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Pending"
                1 -> "Finished"
                else -> null
            }
        }.attach()
    }

    private fun setupAddElementButton() {
        binding.addElementButton.setOnClickListener {
            showAddItemsDialog()
        }
    }

    private fun showAddItemsDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_items)

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val editTextMultiLine = dialog.findViewById<EditText>(R.id.editTextTextMultiLine)
        val sendButton = dialog.findViewById<ImageButton>(R.id.btn_send)

        // Show keyboard automatically when the dialog appears
        editTextMultiLine.requestFocus()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        sendButton.setOnClickListener {
            val inputText = editTextMultiLine.text.toString().trim()

            if (inputText.isEmpty()) {
                showCustomToast("❌ Please enter at least one item.")
                dialog.dismiss() // Close the dialog and do nothing
            } else {
                val itemsList = inputText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                addItemsToPendingList(itemsList)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun addItemsToPendingList(items: List<String>) {
        listId?.let { id ->
            db.collection("shopping_lists").document(id)
                .update("pendingItems", FieldValue.arrayUnion(*items.toTypedArray()))
                .addOnFailureListener {
                    showCustomToast("❌ Unable to add items. Please try again.")
                }
        }
    }

    private fun sharePendingItems() {
        listId?.let { id ->
            db.collection("shopping_lists").document(id).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val pendingItems = (document.get("pendingItems") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                        if (pendingItems.isEmpty()) {
                            showCustomToast("❌ No items to share.")
                            return@addOnSuccessListener
                        }

                        val shareText = pendingItems.joinToString("\n") { "• $it" }

                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }

                        startActivity(Intent.createChooser(intent, "Share List"))
                    }
                }
                .addOnFailureListener {
                    showCustomToast("❌ Unable to fetch items.")
                }
        }
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
