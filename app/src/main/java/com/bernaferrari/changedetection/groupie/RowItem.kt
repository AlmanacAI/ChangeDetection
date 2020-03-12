package com.bernaferrari.changedetection.groupie

import android.view.View
import androidx.core.content.ContextCompat
import com.bernaferrari.changedetection.R
import com.bernaferrari.changedetection.extensions.convertTimestampToDate
import com.bernaferrari.changedetection.extensions.getColorFromAttr
import com.bernaferrari.changedetection.extensions.readableFileSize
import com.bernaferrari.changedetection.repo.Snap
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import com.xwray.groupie.kotlinandroidextensions.Item
import kotlinx.android.synthetic.main.item_row.*

class RowItem(
    val snap: Snap,
    var isSelected: Boolean = false
) : Item() {

    override fun getLayout() = R.layout.item_row

    override fun unbind(holder: GroupieViewHolder) {
        holder.counter.visibility = View.GONE
        super.unbind(holder)
    }

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {

        viewHolder.apply {
            title.text = snap.timestamp.convertTimestampToDate()
            subtitle.text = snap.contentSize.readableFileSize()

            if (isSelected) {
                subtitle.setTextColor(
                        ContextCompat.getColor(
                                viewHolder.containerView.context,
                                R.color.md_blue_A200
                    )
                )

                title.setTextColor(
                    ContextCompat.getColor(
                        viewHolder.containerView.context,
                        R.color.md_blue_A200
                    )
                )

                counter.visibility = View.VISIBLE
            } else {
                subtitle.setTextColor(
                    ContextCompat.getColor(
                        viewHolder.containerView.context,
                        R.color.FontWeak
                    )
                )

                title.setTextColor(
                    viewHolder.containerView.context.getColorFromAttr(R.attr.strongColor)
                )
            }
        }
    }
}
