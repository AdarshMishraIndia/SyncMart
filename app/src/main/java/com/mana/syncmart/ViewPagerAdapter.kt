package com.mana.syncmart

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(activity: ListActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2 // Two fragments: Pending & Finished

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PendingItemsFragment()  // First fragment (Pending items)
            1 -> FinishedItemsFragment() // Second fragment (Finished items)
            else -> throw IllegalStateException("Invalid fragment position")
        }
    }
}
