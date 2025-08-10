package com.mana.syncmart

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mana.syncmart.databinding.DialogConfirmBinding
import com.mana.syncmart.databinding.FragmentPendingItemsBinding

@Suppress("DEPRECATION")
class PendingItemsFragment : Fragment(), ItemAdapter.SelectionListener {

    private lateinit var binding: FragmentPendingItemsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: ItemAdapter
    private var listId: String? = null
    private var firestoreListener: ListenerRegistration? = null
    private val scrollHandler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentPendingItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        listId = arguments?.getString("LIST_ID") ?: return

        setupRecyclerView()
        startFirestoreListener(listId!!)

        // Handle back press: clear selection if active
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (::adapter.isInitialized && adapter.getSelectedItems().isNotEmpty()) {
                adapter.clearSelection()
            } else {
                isEnabled = false
                requireActivity().onBackPressed()
            }
        }

        // Background click clears selection mode
        binding.root.setOnClickListener {
            if (::adapter.isInitialized && adapter.getSelectedItems().isNotEmpty()) {
                adapter.clearSelection()
            }
        }

        // Arrow click listeners
        binding.upArrowAnimation.setOnClickListener {
            binding.recyclerViewPending.smoothScrollToPosition(0)
            hideArrows()
            postEvaluateArrows()
        }

        binding.downArrowAnimation.setOnClickListener {
            binding.recyclerViewPending.smoothScrollToPosition(adapter.itemCount - 1)
            hideArrows()
            postEvaluateArrows()
        }
    }

    private fun setupRecyclerView() {
        adapter = ItemAdapter(
            listId = listId!!,
            items = mutableListOf(),
            onItemChecked = { itemName -> markItemAsFinished(itemName) },
            showButtons = true,
            selectionListener = this
        )

        val recyclerView = binding.recyclerViewPending
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // Initial arrow evaluation
        recyclerView.viewTreeObserver.addOnGlobalLayoutListener {
            evaluateArrowsVisibility()
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                hideArrows() // Hide while scrolling
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    postEvaluateArrows()
                }
            }
        })
    }

    private fun postEvaluateArrows() {
        scrollHandler.postDelayed({
            evaluateArrowsVisibility()
        }, 200)
    }

    private fun evaluateArrowsVisibility() {
        val recyclerView = binding.recyclerViewPending
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return

        val firstVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
        val lastVisible = layoutManager.findLastCompletelyVisibleItemPosition()
        val totalItems = adapter.itemCount

        val showUp = firstVisible > 0
        val showDown = lastVisible < totalItems - 1 && totalItems > 0

        binding.upArrowAnimation.visibility = if (showUp) View.VISIBLE else View.GONE
        binding.downArrowAnimation.visibility = if (showDown) View.VISIBLE else View.GONE
    }

    private fun hideArrows() {
        binding.upArrowAnimation.visibility = View.GONE
        binding.downArrowAnimation.visibility = View.GONE
    }

    private fun startFirestoreListener(id: String) {
        firestoreListener = db.collection("shopping_lists").document(id)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirestoreListener", "Error fetching updates", e)
                    showToast("Error fetching updates")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val rawMap =
                        snapshot.get("pendingItems") as? Map<*, *> ?: emptyMap<Any?, Any?>()
                    val parsedMap = rawMap.mapNotNull { entry ->
                        val itemName = entry.key as? String ?: return@mapNotNull null
                        val rawValue = entry.value
                        val isImportant = when (rawValue) {
                            is Boolean -> rawValue
                            is String -> rawValue.equals("true", ignoreCase = true)
                            else -> false
                        }
                        itemName to isImportant
                    }.toMap()

                    adapter.updateList(parsedMap)
                    evaluateArrowsVisibility()
                }
            }
    }

    private fun markItemAsFinished(itemName: String) {
        listId?.let { id ->
            val docRef = db.collection("shopping_lists").document(id)

            docRef.update(FieldPath.of("pendingItems", itemName), FieldValue.delete())
                .addOnSuccessListener {
                    docRef.update("finishedItems", FieldValue.arrayUnion(itemName))
                        .addOnFailureListener {
                            showToast("Failed to move item to finished")
                        }
                }
                .addOnFailureListener {
                    showToast("Failed to update pending list")
                }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    // --- SelectionListener interface implementation ---

    override fun onSelectionStarted() {
        (activity as? ListActivity)?.enterSelectionMode()
    }

    override fun onSelectionChanged(selectedCount: Int) {
        (activity as? ListActivity)?.updateSelectionCount(selectedCount)
    }

    override fun onSelectionCleared() {
        (activity as? ListActivity)?.exitSelectionMode()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        firestoreListener?.remove()
    }

    fun showConfirmDeleteDialog() {
        val selectedItems = adapter.getSelectedItems()
        if (selectedItems.isEmpty()) return

        val dialogBinding = DialogConfirmBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnDelete.setOnClickListener {
            deleteSelectedItems(selectedItems)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun deleteSelectedItems(selectedItems: List<String>) {
        val batch = db.batch()
        val listRef = db.collection("shopping_lists").document(listId!!)

        for (item in selectedItems) {
            batch.update(listRef, "pendingItems.$item", FieldValue.delete())
        }

        batch.commit()
            .addOnSuccessListener {
                adapter.clearSelection()
                showToast("${selectedItems.size} item(s) deleted")
            }
            .addOnFailureListener {
                showToast("Failed to delete items")
            }
    }
}