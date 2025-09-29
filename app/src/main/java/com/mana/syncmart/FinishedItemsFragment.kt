package com.mana.syncmart

// Timestamp is now handled as String
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mana.syncmart.databinding.FragmentFinishedItemsBinding
import com.mana.syncmart.databinding.LayoutMetadataBinding

class FinishedItemsFragment : Fragment() {

    private lateinit var binding: FragmentFinishedItemsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: ItemAdapter
    private var listId: String? = null
    private var firestoreListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentFinishedItemsBinding.inflate(inflater, container, false)
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
            onItemClick = {}, // Clicking finished items does nothing
            onInfoClick = { item, name -> showItemInfoDialog(item) }
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

                if (snapshot != null && snapshot.exists()) {
                    val itemsMap = snapshot.get("items") as? Map<*, *> ?: emptyMap<Any?, Any?>()

                    val finishedList = itemsMap.mapNotNull { entry ->
                        val itemId = entry.key as? String ?: return@mapNotNull null
                        val rawValue = entry.value as? Map<*, *> ?: return@mapNotNull null

                        val name = rawValue["name"] as? String ?: ""
                        val addedBy = rawValue["addedBy"] as? String ?: ""
                        val addedAt = rawValue["addedAt"] as? String ?: ""
                        val important = rawValue["important"] as? Boolean == true
                        val pending = rawValue["pending"] as? Boolean != false

                        if (!pending) {
                            itemId to ShoppingItem(
                                name = name,
                                addedBy = addedBy,
                                addedAt = addedAt,
                                important = important,
                                pending = false
                            )
                        } else null
                    }.sortedBy { it.second.addedAt }

                    updateItemList(finishedList)
                }
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
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        firestoreListener?.remove()
    }
}
