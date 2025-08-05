package com.mana.syncmart

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import android.content.Intent
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.firebase.firestore.*
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth


@Suppress("DEPRECATION")
class FriendActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: FriendSelectionAdapter
    private lateinit var emptyStateText: TextView
    private lateinit var toolbarText: TextView
    private lateinit var btnAdd: ImageButton

    private val friendsList = mutableListOf<Friend>()

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend)

        // Setup Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "SyncMart"
            setDisplayHomeAsUpEnabled(true)
            setHomeButtonEnabled(true)
        }

        // Initialize DrawerLayout and NavigationView
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navigationView: NavigationView = findViewById(R.id.navigation_view)

        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val navMenu = navigationView.menu
        val toggleItem = navMenu.findItem(R.id.nav_friends_and_lists)
        toggleItem.title = getString(R.string.lists)
        toggleItem.setIcon(R.drawable.ic_lists)

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_friends_and_lists -> {
                    val intent = Intent(this, ListManagementActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                R.id.nav_logout -> {
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, AuthActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Get references
        listView = findViewById(R.id.friendListView)
        emptyStateText = findViewById(R.id.emptyStateText)
        toolbarText = findViewById(R.id.toolbar_user)
        btnAdd = findViewById(R.id.addFriendButton)

        // ✅ Get the currently logged-in user
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val userEmail = user.email
            if (userEmail != null) {
                fetchUserName(userEmail)
                listenForFriendsUpdates(userEmail)

                // Handle Add Friend Button Click
                btnAdd.setOnClickListener {
                    val dialogView = layoutInflater.inflate(R.layout.dialog_add_friend, null)

                    val etFriendEmail = dialogView.findViewById<EditText>(R.id.editTextFriendEmail)
                    val etFriendName = dialogView.findViewById<EditText>(R.id.editTextFriendName)
                    val btnDialogAdd = dialogView.findViewById<Button>(R.id.btnAdd) // Get button from XML

                    val dialog = AlertDialog.Builder(this)
                        .setView(dialogView)
                        .create()

                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

                    btnDialogAdd.setOnClickListener {
                        val friendEmail = etFriendEmail.text.toString().trim()
                        val friendName = etFriendName.text.toString().trim()

                        if (friendEmail.isEmpty() || friendName.isEmpty()) {
                            showCustomToast("Both fields are required!")
                        } else {
                            val user = FirebaseAuth.getInstance().currentUser
                            if (user != null && user.email != null) {
                                addFriendToFirestore(user.email!!, friendEmail, friendName)
                                dialog.dismiss() // Dismiss dialog after adding friend
                            } else {
                                showCustomToast("User not logged in!")
                            }
                        }
                    }

                    dialog.show()
                }


            } else {
                Toast.makeText(this, "Failed to retrieve email!", Toast.LENGTH_SHORT).show()
            }
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
                    val firstName = fullName.split(" ").firstOrNull() ?: fullName
                    toolbarText.text = getString(R.string.welcome_message, firstName)
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
                        ?.filterKeys { it is String }
                        ?.mapNotNull { (key, value) ->
                            if (key is String && value is String) key to value else null
                        }
                        ?.toMap()
                        ?: emptyMap()

                    if (validFriendsMap.isEmpty()) {
                        showEmptyState()
                    } else {
                        updateListView(validFriendsMap)
                    }
                } else {
                    showEmptyState()
                }
            }
    }

    /**
     * Add friend to Firestore database
     */
    private fun addFriendToFirestore(userEmail: String, friendEmail: String, friendName: String) {
        val userDocRef = db.collection("Users").document(userEmail)

        userDocRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val rawMap = document.get("friendsMap") as? Map<*, *>
                val friendsMap = rawMap
                    ?.filterKeys { it is String } // Ensure keys are Strings
                    ?.filterValues { it is String } // Ensure values are Strings
                    ?.mapKeys { it.key as String } // Cast keys to String
                    ?.mapValues { it.value as String } // Cast values to String
                    ?.toMutableMap() ?: mutableMapOf()

                friendsMap[friendEmail] = friendName

                userDocRef.update("friendsMap", friendsMap)
                    .addOnFailureListener {
                        showCustomToast("Failed to add friend!")
                    }
            }


        }.addOnFailureListener {
            showCustomToast("Error retrieving user data!")
        }
    }

    /**
     * Show custom toast message
     */
    private fun showCustomToast(message: String) {
        val inflater = layoutInflater
        val layout: View = inflater.inflate(R.layout.custom_toast_layout, findViewById(R.id.toast_text))
        val text: TextView = layout.findViewById(R.id.toast_text)
        text.text = message

        val toast = Toast(applicationContext)
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.duration = Toast.LENGTH_SHORT
        toast.view = layout
        toast.show()
    }

    /**
     * Show empty state when there are no friends
     */
    private fun showEmptyState() {
        emptyStateText.visibility = View.VISIBLE
        listView.visibility = View.GONE
    }

    /**
     * Update the ListView with the latest friends data
     */
    private fun updateListView(friendsMap: Map<String, String>) {
        friendsList.clear()

        for ((email, name) in friendsMap) {
            friendsList.add(Friend(name, email)) // Assuming you have a Friend data class
        }

        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        } else {
            adapter = FriendSelectionAdapter(
                this,
                friendsList,
                selectedEmails = mutableSetOf(), // ✅ Removed selection tracking
                showCheckBox = false,
                onFriendChecked = { _, _ -> } // ✅ No checkbox interaction needed
            )


            listView.adapter = adapter
        }

        // Show or hide the empty state
        if (friendsList.isEmpty()) {
            showEmptyState()
        } else {
            emptyStateText.visibility = View.GONE
            listView.visibility = View.VISIBLE
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove()
    }
}
