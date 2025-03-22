package com.mana.syncmart

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*

class FriendActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: FriendSelectionAdapter
    private lateinit var emptyStateText: TextView
    private lateinit var toolbarText: TextView

    private val selectedEmails = mutableSetOf<String>() // To track selected friends
    private val friendsList = mutableListOf<Friend>()

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private var userEmail: String? = null
    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend)

        // Setup Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "SyncMart"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        // Get references
        listView = findViewById(R.id.friendListView)
        emptyStateText = findViewById(R.id.emptyStateText)
        toolbarText = findViewById(R.id.toolbar_user)

        // Get logged-in user email
        userEmail = auth.currentUser?.email

        if (userEmail != null) {
            fetchUserName(userEmail!!) // Fetch and display user's name
            listenForFriendsUpdates(userEmail!!)
        } else {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show()
        }

        // Handle back button press correctly
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@FriendActivity, ListManagementActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                finish()
            }
        })
    }

    /**
     * Fetch the user's name from Firestore where document ID = user email
     */
    private fun fetchUserName(email: String) {
        db.collection("Users").document(email)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val fullName = document.getString("name") ?: email
                    val firstName = fullName.split(" ").firstOrNull() ?: fullName // Extract first word
                    toolbarText.text = getString(R.string.welcome_message, firstName) // Display first name
                } else {
                    toolbarText.text = getString(R.string.welcome_message, email)
                }
            }
            .addOnFailureListener {
                toolbarText.text = getString(R.string.welcome_message, email)
                Log.e("Firestore", "Error fetching user name", it)
            }
    }



    /**
     * Listen for changes in Firestore and update ListView in real-time
     */
    private fun listenForFriendsUpdates(email: String) {
        listenerRegistration = db.collection("Users")
            .document(email)
            .addSnapshotListener { snapshot, error ->
            if (error != null) {
                    Log.e("Firestore", "Error fetching data", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val friendsMap = snapshot.get("friendsMap") as? Map<*, *>
                    val validFriendsMap: Map<String, String> = friendsMap
                        ?.filterKeys { it is String }  // Ensure keys are Strings
                        ?.mapNotNull { (key, value) ->
                            if (key is String && value is String) key to value else null
                        } // Ensure values are also Strings
                        ?.toMap()
                        ?: emptyMap()

                    if (validFriendsMap.isEmpty()) {
                        showEmptyState()
                    } else {
                        updateListView(validFriendsMap) // Pass the correctly typed map
                    }
                } else {
                    showEmptyState()
                }
            }
    }


    /**
     * Update ListView with friends list using custom adapter
     */
    private fun updateListView(friendsMap: Map<String, String>) {
        friendsList.clear()
        for ((email, name) in friendsMap) {
            friendsList.add(Friend(name, email)) // Convert Map to Friend objects
        }

        // Initialize adapter if not already done
        if (!::adapter.isInitialized) {
            adapter = FriendSelectionAdapter(this, friendsList, selectedEmails, { _, _ -> }, false) // 🔹 Pass `false` to hide checkboxes
            listView.adapter = adapter
        } else {
            adapter.notifyDataSetChanged()
        }


        listView.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE
    }

    /**
     * Show empty state when there are no friends
     */
    private fun showEmptyState() {
        friendsList.clear()
        if (::adapter.isInitialized) { // Prevent crash if adapter is not initialized
            adapter.notifyDataSetChanged()
        }
        listView.visibility = View.GONE
        emptyStateText.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove() // Clean up Firestore listener
    }

}
