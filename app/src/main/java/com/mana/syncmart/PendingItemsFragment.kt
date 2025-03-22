package com.mana.syncmart

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.mana.syncmart.databinding.FragmentPendingItemsBinding

class PendingItemsFragment : Fragment() {

    private lateinit var binding: FragmentPendingItemsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: ItemAdapter
    private var pendingItems = mutableListOf<String>()
    private var listId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentPendingItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        listId = activity?.intent?.getStringExtra("LIST_ID")

        setupRecyclerView()
        listId?.let { listenForChanges(it) }
    }

    private fun setupRecyclerView() {
        adapter = ItemAdapter(
            items = pendingItems,
            onDeleteClicked = { itemName -> deleteItem(itemName) },
            onItemChecked = { itemName -> markItemAsFinished(itemName) },
            showButtons = true
        )

        binding.recyclerViewPending.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PendingItemsFragment.adapter
        }
    }

    private fun listenForChanges(listId: String) {
        db.collection("shopping_lists").document(listId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    showToast("Error fetching updates")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val items = (snapshot.get("pendingItems") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    updateItemList(items)
                }
            }
    }

    private fun deleteItem(itemName: String) {
        listId?.let { id ->
            db.collection("shopping_lists").document(id)
                .update("pendingItems", FieldValue.arrayRemove(itemName))
                .addOnFailureListener { showToast("Failed to delete item") }
        }
    }

    private fun markItemAsFinished(itemName: String) {
        listId?.let { id ->
            db.collection("shopping_lists").document(id)
                .update("pendingItems", FieldValue.arrayRemove(itemName))
                .addOnSuccessListener {
                    db.collection("shopping_lists").document(id)
                        .update("finishedItems", FieldValue.arrayUnion(itemName))
                        .addOnFailureListener { showToast("Failed to move item to finished") }
                }
                .addOnFailureListener { showToast("Failed to update pending list") }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateItemList(newItems: List<String>) {
        pendingItems.clear()
        pendingItems.addAll(newItems)
        adapter.notifyDataSetChanged()
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
