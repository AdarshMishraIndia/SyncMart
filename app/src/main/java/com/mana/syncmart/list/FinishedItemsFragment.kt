package com.mana.syncmart.list

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mana.syncmart.dataclass.ShoppingItem
import com.mana.syncmart.databinding.FragmentFinishedItemsBinding
import com.mana.syncmart.databinding.LayoutMetadataBinding

class FinishedItemsFragment : Fragment() {

    private var _binding: FragmentFinishedItemsBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: ItemAdapter
    private var listId: String? = null
    private var firestoreListener: ListenerRegistration? = null

    // Track which item IDs have been backfilled this session
    private val backfilledItemIds = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFinishedItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        listId = arguments?.getString("LIST_ID") ?: return

        setupRecyclerView()
        startFirestoreListener(listId!!)
    }

    private fun setupRecyclerView() {
        adapter = ItemAdapter(
            items = mutableListOf(),
            isFinishedMode = true,
            onItemClick = { itemId -> toggleItemBackToPending(itemId) },
            onInfoClick = { item, _ -> showItemInfoDialog(item) }
        )

        binding.recyclerViewFinished.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FinishedItemsFragment.adapter
        }
    }

    private fun startFirestoreListener(id: String) {
        firestoreListener?.remove()

        firestoreListener = db.collection("shopping_lists").document(id)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    showToast("Error loading finished items: ${e.message}")
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val rawItemsMap = snapshot.get("items") as? Map<*, *> ?: emptyMap<Any, Any>()
                val itemsNeedingBackfill = mutableMapOf<String, Map<String, Any>>()

                val finishedList = rawItemsMap.mapNotNull { entry ->
                    val itemId = entry.key as? String ?: return@mapNotNull null
                    val rawValue = entry.value as? Map<*, *> ?: return@mapNotNull null

                    val active = rawValue["active"] as? Boolean ?: true
                    val pending = rawValue["pending"] as? Boolean ?: true
                    val lastModified = rawValue["lastModified"] as? Timestamp

                    // Only show active, finished items
                    if (!active || pending) return@mapNotNull null

                    val name = rawValue["name"] as? String ?: ""
                    val addedBy = rawValue["addedBy"] as? String ?: ""
                    val addedAt = rawValue["addedAt"] as? String ?: ""
                    val important = rawValue["important"] as? Boolean == true

                    // Flag items missing active or lastModified for backfill
                    if (itemId !in backfilledItemIds) {
                        val missingFields = mutableMapOf<String, Any>()
                        if (rawValue["active"] == null) missingFields["items.$itemId.active"] = true
                        if (lastModified == null) missingFields["items.$itemId.lastModified"] = Timestamp.now()
                        if (missingFields.isNotEmpty()) itemsNeedingBackfill[itemId] = missingFields
                    }

                    itemId to ShoppingItem(
                        name = name,
                        addedBy = addedBy,
                        addedAt = addedAt,
                        important = important,
                        pending = false,
                        active = true,
                        lastModified = lastModified
                    )
                }.sortedByDescending { it.second.addedAt }

                updateItemList(finishedList)

                if (itemsNeedingBackfill.isNotEmpty()) {
                    backfillItemFields(id, itemsNeedingBackfill)
                }
            }
    }

    /**
     * Writes missing active/lastModified fields to legacy finished items in one update call.
     */
    private fun backfillItemFields(listId: String, itemsMap: Map<String, Map<String, Any>>) {
        val flatUpdates = hashMapOf<String, Any>()
        itemsMap.forEach { (itemId, fields) ->
            flatUpdates.putAll(fields)
            backfilledItemIds.add(itemId)
        }
        flatUpdates["lastModified"] = Timestamp.now()

        db.collection("shopping_lists").document(listId)
            .update(flatUpdates)
            .addOnFailureListener { e ->
                Log.w("FinishedItemsFragment", "Item backfill failed: ${e.message}")
                itemsMap.keys.forEach { backfilledItemIds.remove(it) }
            }
    }

    /**
     * Restores a finished item to pending.
     * Verifies item is still active before restoring.
     * Stamps item-level and list-level lastModified.
     */
    private fun toggleItemBackToPending(itemId: String) {
        listId?.let { id ->
            db.collection("shopping_lists").document(id).get()
                .addOnSuccessListener { doc ->
                    val items = doc.get("items") as? Map<*, *>
                    val itemData = items?.get(itemId) as? Map<*, *>
                    val active = itemData?.get("active") as? Boolean ?: true

                    if (!active) {
                        showToast("Item has been deleted and cannot be restored")
                        return@addOnSuccessListener
                    }

                    val now = Timestamp.now()
                    val updates = hashMapOf<String, Any>(
                        "items.$itemId.pending" to true,
                        "items.$itemId.lastModified" to now,
                        "lastModified" to now
                    )
                    db.collection("shopping_lists").document(id)
                        .update(updates)
                        .addOnFailureListener { e -> showToast("Failed to restore item: ${e.message}") }
                }
                .addOnFailureListener { e -> showToast("Failed to restore item: ${e.message}") }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateItemList(newItems: List<Pair<String, ShoppingItem>>) {
        adapter.updateList(newItems)
    }

    private fun showItemInfoDialog(item: ShoppingItem) {
        val dialogBinding = LayoutMetadataBinding.inflate(layoutInflater)

        dialogBinding.nameValue.text = item.name
        dialogBinding.addedByValue.text = item.addedBy
        dialogBinding.addedAtValue.text = item.formattedDate

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        firestoreListener?.remove()
        _binding = null
    }
}