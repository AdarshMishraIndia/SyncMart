package com.mana.syncmart

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.mana.syncmart.databinding.FragmentFinishedItemsBinding
import com.google.firebase.firestore.ListenerRegistration


class FinishedItemsFragment : Fragment() {

    private lateinit var binding: FragmentFinishedItemsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: ItemAdapter
    private var finishedItems = mutableListOf<String>()
    private var listId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentFinishedItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        listId = activity?.intent?.getStringExtra("LIST_ID")

        setupRecyclerView()
        listId?.let { fetchFinishedItems(it) }
    }

    private fun setupRecyclerView() {
        adapter = ItemAdapter(
            items = finishedItems,
            onDeleteClicked = {},
            onItemChecked = {},
            showButtons = false
        )

        binding.recyclerViewFinished.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FinishedItemsFragment.adapter
        }
    }

    private var finishedItemsListener: ListenerRegistration? = null

    private fun fetchFinishedItems(listId: String) {
        finishedItemsListener?.remove() // Remove old listener if exists

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
        finishedItems.addAll(newItems)
        adapter.notifyDataSetChanged()
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
