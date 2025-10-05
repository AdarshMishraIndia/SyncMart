package com.mana.syncmart.friend

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import androidx.core.content.ContextCompat
import com.mana.syncmart.databinding.FriendsLayoutBinding

class FriendSelectionAdapter(
    context: Context,
    private val friendsList: List<Friend>,
    private val selectedEmails: MutableSet<String>,
    private val onFriendChecked: (Friend, Boolean) -> Unit,
    private val showCheckBox: Boolean
) : ArrayAdapter<Friend>(context, com.mana.syncmart.R.layout.friends_layout, friendsList) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding: FriendsLayoutBinding
        val view: View

        if (convertView == null) {
            binding = FriendsLayoutBinding.inflate(LayoutInflater.from(context), parent, false)
            view = binding.root
            view.tag = binding // Store binding in tag for reuse
        } else {
            binding = convertView.tag as FriendsLayoutBinding
            view = convertView
        }

        val friend = friendsList[position]

        // ✅ Set text with bold name and italic email
        val spannable = SpannableStringBuilder().apply {
            append(friend.name)
            setSpan(StyleSpan(Typeface.BOLD), 0, friend.name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append("\n")
            append(friend.email)
            setSpan(StyleSpan(Typeface.ITALIC), friend.name.length + 1,
                length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.textViewNameEmail.text = spannable

        // ✅ Show or hide checkbox
        binding.checkBox.visibility = if (showCheckBox) View.VISIBLE else View.GONE

        if (showCheckBox) {
            binding.checkBox.setOnCheckedChangeListener(null) // Prevent incorrect state
            binding.checkBox.isChecked = selectedEmails.contains(friend.email)
            updateCheckboxColor(binding.checkBox, binding.checkBox.isChecked)

            binding.checkBox.setOnCheckedChangeListener { _, isChecked ->
                updateCheckboxColor(binding.checkBox, isChecked)
                if (isChecked) selectedEmails.add(friend.email) else selectedEmails.remove(friend.email)
                onFriendChecked(friend, isChecked)
            }
        }

        return view
    }


    private fun updateCheckboxColor(checkBox: CheckBox, isChecked: Boolean) {
        val colorResId = if (isChecked) com.mana.syncmart.R.color.green else com.mana.syncmart.R.color.red
        checkBox.buttonTintList = ColorStateList.valueOf(ContextCompat.getColor(context, colorResId))
    }

}