package com.mana.syncmart

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mana.syncmart.databinding.FragmentFinishedItemsBinding

class FinishedItemsFragment : Fragment() {

    private lateinit var binding: FragmentFinishedItemsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: ItemAdapter
    private var finishedItems = mutableListOf<Pair<String, Boolean>>()  // Pair of (itemName, isImportant)
    private var listId: String? = null
    private var finishedItemsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentFinishedItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        listId = arguments?.getString("LIST_ID")

        setupRecyclerView()
        listId?.let { fetchFinishedItems(it) }
    }

    private fun setupRecyclerView() {
        adapter = ItemAdapter(
            listId = listId ?: "",
            items = finishedItems,
            onItemChecked = {}, // No-op for finished items
            showButtons = false,
        )

        binding.recyclerViewFinished.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FinishedItemsFragment.adapter
        }
    }

    private fun fetchFinishedItems(listId: String) {
        finishedItemsListener?.remove()

        finishedItemsListener = db.collection("shopping_lists").document(listId)
            .addSnapshotListener { document, error ->
                if (error != null) {
                    showToast("Error loading finished items: ${error.message}")
                    return@addSnapshotListener
                }

                if (document != null && document.exists()) {
                    val finishedItemsList = (document.get("finishedItems") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    updateItemList(finishedItemsList)
                }
            }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateItemList(newItems: List<String>) {
        finishedItems.clear()
        finishedItems.addAll(newItems.map { it to false }) // Default isImportant = false
        adapter.notifyDataSetChanged()
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        finishedItemsListener?.remove()
    }
}
