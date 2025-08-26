package com.mana.syncmart

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mana.syncmart.databinding.FragmentPendingItemsBinding
import com.mana.syncmart.databinding.LayoutMetadataBinding
import com.mana.syncmart.databinding.DialogConfirmBinding
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.auth.FirebaseAuth

@Suppress("DEPRECATION")
class PendingItemsFragment : Fragment() {

    private var _binding: FragmentPendingItemsBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private var listId: String? = null
    private var firestoreListener: ListenerRegistration? = null
    private lateinit var adapter: ItemAdapter
    private lateinit var selectionManager: SelectionManager
    private lateinit var upArrow: LottieAnimationView
    private lateinit var downArrow: LottieAnimationView

    private var upArrowVisible = false
    private var downArrowVisible = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPendingItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listId = arguments?.getString("LIST_ID") ?: return

        upArrow = binding.upArrowAnimation
        downArrow = binding.downArrowAnimation
        upArrow.visibility = View.VISIBLE; downArrow.visibility = View.VISIBLE
        upArrow.alpha = 0f; downArrow.alpha = 0f

        selectionManager = SelectionManager()
        setupRecyclerView()
        startFirestoreListener(listId!!)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (selectionManager.isSelectionActive()) {
                // Get the currently selected item positions
                val selectedPositions = selectionManager.getSelectedPositions(adapter.items)

                // Clear selection in manager
                selectionManager.clearSelection()

                // Exit selection mode in the activity toolbar
                (requireActivity() as? ListActivity)?.exitSelectionMode()

                // Notify only the affected items to refresh
                selectedPositions.forEach { pos ->
                    adapter.notifyItemChanged(pos)
                }
            } else {
                // Let the default back behavior happen
                isEnabled = false
                requireActivity().onBackPressed()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ItemAdapter(
            items = mutableListOf(),
            onStarClick = { pos -> toggleImportant(pos) },
            onTickClick = { itemName -> markItemAsFinished(itemName) },
            onInfoClick = { item, name -> showItemInfoDialog(item, name) },
            selectionManager = selectionManager,
            onSelectionChanged = { selectedCount ->
                val activity = requireActivity() as? ListActivity
                if (selectedCount == 1) {
                    activity?.enterSelectionMode()  // first item selected
                }
                activity?.updateSelectionCount(selectedCount)  // update count dynamically
            },
            onSelectionCleared = {
                val activity = requireActivity() as? ListActivity
                activity?.exitSelectionMode()  // selection cleared
            }
        )

        binding.recyclerViewPending.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewPending.adapter = adapter

        setupArrowIndicators()
    }

    private fun startFirestoreListener(listId: String) {
        firestoreListener = db.collection("shopping_lists").document(listId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("PendingItemsFragment", "Firestore error", e)
                    showToast("Error loading items")
                    return@addSnapshotListener
                }

                val items = (snapshot?.get("items") as? Map<*, *>)?.mapNotNull { entry ->
                    val itemId = entry.key as? String ?: return@mapNotNull null
                    val value = entry.value as? Map<*, *> ?: return@mapNotNull null

                    val name = value["name"] as? String ?: ""
                    val addedBy = value["addedBy"] as? String ?: ""
                    val addedAt = value["addedAt"] as? Timestamp
                    val important = value["important"] as? Boolean == true
                    val pending = value["pending"] as? Boolean != false

                    itemId to ShoppingItem(
                        name = name,
                        addedBy = addedBy,
                        addedAt = addedAt,
                        important = important,
                        pending = pending
                    )
                }
                    ?.filter { it.second.pending }
                    ?.sortedByDescending { it.second.addedAt } ?: emptyList()

                adapter.updateList(items)
                updateArrowVisibility()
            }
    }

    private fun toggleImportant(position: Int) {
        val (name, item) = adapter.items[position]
        val updated = item.copy(important = !item.important)
        adapter.items[position] = name to updated
        adapter.notifyItemChanged(position)
        listId?.let { db.collection("shopping_lists").document(it)
            .update("items.$name.important", updated.important) }
    }

    private fun markItemAsFinished(itemName: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            showToast("User not logged in")
            return
        }

        val currentUserEmail = currentUser.email ?: run {
            showToast("Email not available")
            return
        }

        val currentTime = Timestamp.now()

        // Fetch current user name from Firestore
        db.collection("Users").document(currentUserEmail).get()
            .addOnSuccessListener { document ->
                val currentUserName = document.getString("name") ?: "Unknown"

                // Update item fields
                listId?.let { listId ->
                    db.collection("shopping_lists").document(listId)
                        .update(
                            mapOf(
                                "items.$itemName.pending" to false,
                                "items.$itemName.addedAt" to currentTime,
                                "items.$itemName.addedBy" to currentUserName
                            )
                        )
                        .addOnSuccessListener {
                            adapter.removeItem(itemName)
                        }
                        .addOnFailureListener {
                            showToast("Failed to mark item finished")
                        }
                }
            }
            .addOnFailureListener {
                showToast("Failed to fetch user info")
            }
    }



    @SuppressLint("SetTextI18n")
    fun showConfirmDeleteDialog() {
        val activity = activity as? ListActivity ?: return

        // Inflate your custom layout
        val dialogBinding = DialogConfirmBinding.inflate(activity.layoutInflater)

        // Create the dialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root) // use dialogBinding.root
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        // Set the title
        dialogBinding.listName.text = "Delete selected items?"

        // Cancel button
        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        // Delete button
        dialogBinding.btnDelete.setOnClickListener {
            adapter.deleteSelectedItems { itemName ->
                listId?.let {
                    db.collection("shopping_lists").document(it)
                        .update(
                            "items.$itemName",
                            com.google.firebase.firestore.FieldValue.delete()
                        )
                        .addOnFailureListener { e ->
                            showToast("Failed to delete $itemName: ${e.message}")
                        }
                }
            }
            dialog.dismiss()
        }
    }

    private fun showToast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    private fun setupArrowIndicators() {
        binding.recyclerViewPending.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateArrowVisibility()
            }
        })
        upArrow.setOnClickListener { binding.recyclerViewPending.smoothScrollToPosition(0) }
        downArrow.setOnClickListener { binding.recyclerViewPending.smoothScrollToPosition(adapter.itemCount - 1) }
    }

    private fun updateArrowVisibility() {
        val layoutManager = binding.recyclerViewPending.layoutManager as LinearLayoutManager
        val firstVisible = layoutManager.findViewByPosition(layoutManager.findFirstVisibleItemPosition())
        val lastVisible = layoutManager.findViewByPosition(layoutManager.findLastVisibleItemPosition())

        val shouldShowUp = firstVisible != null && firstVisible.top < 0
        if (shouldShowUp != upArrowVisible) {
            upArrowVisible = shouldShowUp
            if (shouldShowUp) {
                upArrow.visibility = View.VISIBLE
                upArrow.animate().alpha(1f).setDuration(150).start()
            } else {
                upArrow.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .withEndAction { upArrow.visibility = View.GONE }
                    .start()
            }
        }

        val shouldShowDown = lastVisible != null && lastVisible.bottom > binding.recyclerViewPending.height
        if (shouldShowDown != downArrowVisible) {
            downArrowVisible = shouldShowDown
            if (shouldShowDown) {
                downArrow.visibility = View.VISIBLE
                downArrow.animate().alpha(1f).setDuration(150).start()
            } else {
                downArrow.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .withEndAction { downArrow.visibility = View.GONE }
                    .start()
            }
        }
    }


    private fun showItemInfoDialog(item: ShoppingItem, itemName: String) {
        val dialogBinding = LayoutMetadataBinding.inflate(layoutInflater)
        dialogBinding.nameValue.text = itemName
        dialogBinding.addedByValue.text = item.addedBy
        dialogBinding.addedAtValue.text = item.addedAt?.let { formatTimestamp(it) } ?: "Unknown"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun formatTimestamp(timestamp: Timestamp): String {
        val date = timestamp.toDate()
        return SimpleDateFormat("h:mm a, d MMM yyyy", Locale.getDefault()).format(date)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        firestoreListener?.remove()
        _binding = null
    }
}
