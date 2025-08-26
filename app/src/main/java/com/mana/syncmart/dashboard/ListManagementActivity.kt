package com.mana.syncmart.dashboard

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mana.syncmart.databinding.ActivityListManagementBinding
import com.mana.syncmart.databinding.DialogConfirmBinding
import com.mana.syncmart.databinding.DialogModifyListBinding
import android.widget.FrameLayout
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import com.mana.syncmart.Friend
import com.mana.syncmart.FriendActivity
import com.mana.syncmart.FriendSelectionAdapter
import com.mana.syncmart.ListActivity
import com.mana.syncmart.ListAdapter
import com.mana.syncmart.R
import com.mana.syncmart.RegisterActivity
import com.mana.syncmart.ShoppingList
import com.mana.syncmart.auth.AuthActivity

class ListManagementActivity : AppCompatActivity() {
    private lateinit var binding: ActivityListManagementBinding
    private lateinit var listAdapter: ListAdapter
    private var loadingDialog: AlertDialog? = null
    private var noInternetSnackbar: Snackbar? = null
    private val viewModel: ListManagementViewModel by viewModels { ListManagementViewModelFactory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu)
            title = "SyncMart"
        }

        // ✅ Register back press handler
        onBackPressedDispatcher.addCallback(this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (listAdapter.isSelectionModeActive()) {
                        listAdapter.clearSelection()
                        toggleSelectionMode(false)
                    } else {
                        // Temporarily disable this callback and let system handle it
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            })

        setupRecyclerView()
        setupDragAndDrop()
        setupNavigationDrawer()
        setupObservers()

        binding.addListButton.setOnClickListener { showModifyDialog(false) }

        binding.textViewSendWapp.setOnClickListener {
            if (!it.isActivated) {
                it.isActivated = true
                binding.textViewSendWapp.background = ContextCompat.getDrawable(this, R.drawable.red_pink_bg)
                viewModel.sendWhatsAppNotification { success ->
                    it.isActivated = false
                    binding.textViewSendWapp.setBackgroundResource(R.drawable.green_blue_bg)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupObservers() {
        viewModel.uiState.observe(this) { state ->
            binding.toolbarUser.text = "Welcome, ${state.userName}"
            listAdapter.updateListPreserveSelection(state.shoppingLists.toMutableList())
            binding.recyclerView.visibility = if (state.isConnected && state.shoppingLists.isNotEmpty()) View.VISIBLE else View.GONE
            binding.emptyStateText.visibility = if (!state.isLoading && state.isConnected && state.shoppingLists.isEmpty()) View.VISIBLE else View.GONE
            if (state.isLoading) showLoadingDialog() else hideLoadingDialog()
            if (!state.isConnected) showNoInternetSnackbar() else dismissNoInternetSnackbar()
            if (state.navigateToAuth) {
                startActivity(Intent(this, AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
                viewModel.resetNavigateToAuth()
            }
            if (state.navigateToFriendActivity) {
                startActivity(Intent(this, FriendActivity::class.java))
                viewModel.resetNavigateToFriendActivity()
            }
            if (state.navigateToRegister) {
                val intent = Intent(this, RegisterActivity::class.java)
                intent.putExtra("isEditingProfile", true)
                startActivity(intent)
                viewModel.resetNavigateToRegister()
            }
        }
        viewModel.toastMessage.observe(this) { message ->
            if (message != null) showCustomToast(message)
        }
    }

    private fun showNoInternetSnackbar() {
        if (noInternetSnackbar == null) {
            noInternetSnackbar = Snackbar.make(binding.root, "⚠️ No internet connection 📡", Snackbar.LENGTH_INDEFINITE)
            val snackbarView = noInternetSnackbar!!.view
            val params = snackbarView.layoutParams as FrameLayout.LayoutParams
            params.gravity = Gravity.CENTER
            snackbarView.layoutParams = params
        }
        noInternetSnackbar?.show()
    }

    private fun dismissNoInternetSnackbar() {
        noInternetSnackbar?.dismiss()
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
                    viewModel.navigateToFriendActivity()
                    true
                }
                R.id.nav_logout -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    viewModel.logout()
                    true
                }
                R.id.nav_delete_account -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    confirmDeleteAccount()
                    true
                }
                R.id.nav_edit_profile -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    viewModel.navigateToEditProfile()
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun confirmDeleteAccount() {
        val dialogBinding = DialogConfirmBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.listName.text = "Are you sure you want to delete your account?"
        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnDelete.setOnClickListener {
            dialog.dismiss()
            viewModel.deleteAccount()
        }
        dialog.show()
    }

    private fun setupRecyclerView() {
        listAdapter = ListAdapter(
            onSelectionChanged = { isActive, count -> toggleSelectionMode(isActive, count) },
            onListClicked = { list ->
                if (!listAdapter.isSelectionModeActive()) {
                    startActivity(
                        Intent(this, ListActivity::class.java).putExtra(
                            "LIST_ID",
                            list.id
                        )
                    )
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
            binding.toolbarUser.visibility = View.GONE
            binding.toolbar.title = "$selectedCount Selected"
            binding.toolbar.menu.clear()
            binding.toolbar.inflateMenu(R.menu.selection_menu)

            val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: return
            val allOwned = listAdapter.getSelectedItems().all { viewModel.uiState.value?.shoppingLists?.find { sl -> sl.id == it }?.owner == userEmail }

            binding.toolbar.menu.findItem(R.id.action_delete_selection)?.isVisible = allOwned
            binding.toolbar.menu.findItem(R.id.action_edit_selection)?.isVisible = allOwned && selectedCount == 1

            binding.toolbar.invalidate()
        } else {
            binding.toolbarUser.visibility = View.VISIBLE
            binding.toolbar.title = "SyncMart"
            binding.toolbar.menu.clear()
        }
    }

    private fun showModifyDialog(isEditing: Boolean, existingList: ShoppingList? = null) {
        val dialogBinding = DialogModifyListBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        val selectedEmails = existingList?.accessEmails?.toMutableSet() ?: mutableSetOf()
        dialogBinding.editTextListName.setText(existingList?.listName ?: "")

        val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: return
        FirebaseFirestore.getInstance().collection("Users").document(userEmail).addSnapshotListener { snapshot, _ ->
            if (snapshot == null || !snapshot.exists()) {
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
            if (name.isEmpty()) return@setOnClickListener
            if (isEditing) existingList?.id?.let { viewModel.updateExistingList(it, name, selectedEmails.toList()) }
            else viewModel.createNewList(name, selectedEmails.toList())
            dialog.dismiss()
        }
    }

    private fun updateFriendsUI(dialogBinding: DialogModifyListBinding, friendsMap: Map<String, String>, selectedEmails: MutableSet<String>) {
        toggleNoFriendsMessage(dialogBinding, friendsMap.isEmpty())
        val friendsList = friendsMap.map { Friend(it.value, it.key) }
        val adapter =
            FriendSelectionAdapter(this, friendsList, selectedEmails, { friend, isChecked ->
                if (isChecked) selectedEmails.add(friend.email) else selectedEmails.remove(friend.email)
            }, showCheckBox = true)
        dialogBinding.listViewMembers.adapter = adapter
        adapter.notifyDataSetChanged()
    }

    private fun toggleNoFriendsMessage(dialogBinding: DialogModifyListBinding, isEmpty: Boolean) {
        dialogBinding.textViewNoFriends.visibility = if (isEmpty) View.VISIBLE else View.GONE
        dialogBinding.listViewMembers.visibility = if (isEmpty) View.GONE else View.VISIBLE
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
            viewModel.deleteSelectedItems(selectedIds)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun editSelectedItem() {
        val selectedId = listAdapter.getSelectedItems().singleOrNull() ?: return
        viewModel.uiState.value?.shoppingLists?.find { it.id == selectedId }?.let { showModifyDialog(true, it) }
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

    private fun showLoadingDialog() {
        if (loadingDialog?.isShowing == true) return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.layout_loading, null)
        val animationView = dialogView.findViewById<LottieAnimationView>(R.id.loading_animation)
        animationView.playAnimation()
        loadingDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()
        val widthPx = resources.getDimensionPixelSize(R.dimen.loading_dialog_width)
        loadingDialog?.window?.setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    @Suppress("DEPRECATION")
    private fun showCustomToast(message: String) {
        val inflater = LayoutInflater.from(this)
        val layout = inflater.inflate(R.layout.custom_toast_layout, findViewById(android.R.id.content), false)
        val toastText = layout.findViewById<TextView>(R.id.toast_text)
        toastText.text = message
        val toast = Toast(this)
        toast.duration = Toast.LENGTH_LONG
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.view = layout
        toast.show()
    }
}