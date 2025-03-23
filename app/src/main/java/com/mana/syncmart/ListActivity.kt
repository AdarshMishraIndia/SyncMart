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
        val sharedPreferences = getSharedPreferences("SyncMartPrefs", MODE_PRIVATE)
        val lastClearedDate = sharedPreferences.getString("lastClearedDate", "")

        // Get the current date in "yyyy-MM-dd" format
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (lastClearedDate != currentDate) {
            clearFinishedItemsForAllLists()
            sharedPreferences.edit { putString("lastClearedDate", currentDate) }
        }
    }

    private fun clearFinishedItemsForAllLists() {
        db.collection("shopping_lists").get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    document.reference.update(mapOf(
                        "finishedItems" to arrayListOf<String>(), // ✅ Ensures the field exists
                        "pendingItems" to FieldValue.arrayUnion() // 🔥 Keeps `pendingItems` intact
                    ))
                }
            }
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
                showCustomToast("⚠️ Please enter at least one item.")
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
                .addOnSuccessListener {
                    showCustomToast("✅ Items added successfully!")
                }
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
                            showCustomToast("⚠️ No items to share.")
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
                    showCustomToast("❌ Unable to fetch items. Please try again.")
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