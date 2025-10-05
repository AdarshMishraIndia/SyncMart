package com.mana.syncmart.list

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ViewPagerAdapter(
    activity: AppCompatActivity,
    private val listId: String,
    private val listName: String
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        val fragment = when (position) {
            0 -> PendingItemsFragment()
            1 -> FinishedItemsFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
        fragment.arguments = Bundle().apply {
            putString("LIST_ID", listId)
            putString("LIST_NAME", listName)
        }
        return fragment
    }
}

