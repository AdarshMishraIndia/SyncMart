package com.mana.syncmart

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mana.syncmart.databinding.FragmentFinishedItemsBinding
import com.mana.syncmart.databinding.LayoutMetadataBinding

class FinishedItemsFragment : Fragment() {

    private lateinit var binding: FragmentFinishedItemsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: ItemAdapter
    private var finishedItems = mutableListOf<Pair<String, ShoppingItem>>()
    private var listId: String? = null
    private var itemsListener: ListenerRegistration? = null

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
            onItemChecked = {}, // No-op for finished
            showButtons = false,
            onInfoClick = { shoppingItem, name ->
                showItemInfoDialog(shoppingItem, name)
            }
        )

        binding.recyclerViewFinished.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FinishedItemsFragment.adapter
        }
    }

    private fun fetchFinishedItems(listId: String) {
        itemsListener?.remove()

        itemsListener = db.collection("shopping_lists").document(listId)
            .addSnapshotListener { document, error ->
                if (error != null) {
                    showToast("Error loading finished items: ${error.message}")
                    return@addSnapshotListener
                }

                if (document != null && document.exists()) {
                    val itemsMap = document.get("items") as? Map<*, *> ?: emptyMap<Any?, Any?>()

                    val parsedItems = itemsMap.mapNotNull { entry ->
                        val itemName = entry.key as? String ?: return@mapNotNull null
                        val rawValue = entry.value as? Map<*, *> ?: return@mapNotNull null

                        val addedBy = rawValue["addedBy"] as? String ?: ""
                        val addedAt = rawValue["addedAt"] as? Timestamp
                        val important = rawValue["important"] as? Boolean == true
                        val pending = rawValue["pending"] as? Boolean != false

                        if (!pending) {
                            itemName to ShoppingItem(
                                addedBy = addedBy,
                                addedAt = addedAt,
                                important = important,
                                pending = false
                            )
                        } else null
                    }

                    updateItemList(parsedItems)
                }
            }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateItemList(newItems: List<Pair<String, ShoppingItem>>) {
        finishedItems.clear()
        finishedItems.addAll(newItems.sortedByDescending { it.second.addedAt ?: Timestamp.now() })
        adapter.notifyDataSetChanged()
    }

    private fun showItemInfoDialog(item: ShoppingItem, itemName: String) {
        val dialogBinding = LayoutMetadataBinding.inflate(layoutInflater)

        dialogBinding.nameValue.text = itemName
        dialogBinding.addedByValue.text = item.addedBy
        dialogBinding.addedAtValue.text = item.addedAt?.toDate()?.toString() ?: "Unknown"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        itemsListener?.remove()
    }
}
