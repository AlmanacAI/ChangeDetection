package com.bernaferrari.changedetection.groupie

import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import com.bernaferrari.changedetection.R
import com.bernaferrari.changedetection.extensions.itemAnimatorWithoutChangeAnimations
import com.bernaferrari.changedetection.extensions.toDp
import com.bernaferrari.changedetection.repo.ColorGroup
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.Section
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import com.xwray.groupie.kotlinandroidextensions.Item
import kotlinx.android.synthetic.main.recyclerview.*

/**
 * Creates a ColorPicker RecyclerView. This will be used on create/edit dialogs.
 *
 * @param currentColors the current selected gradientColor for the item
 * @param gradientList the gradient list with all the color pairs
 * @param listener callback for transmitting back the results
 */
class ColorPickerRecyclerViewItem(
    private val currentColors: ColorGroup,
    private val gradientList: List<ColorGroup>,
    private val listener: (ColorGroup) -> (Unit)
) : Item() {

    override fun getLayout(): Int = R.layout.colorpicker_recyclerview

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {

        viewHolder.containerView.background = ContextCompat.getDrawable(
                viewHolder.containerView.context,
                R.drawable.darker_transparent_background
        )
        viewHolder.containerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            val dp2 = 2.toDp(viewHolder.containerView.resources)
            val dp8 = 8.toDp(viewHolder.containerView.resources)
            this.setMargins(dp8, dp2, dp8, dp2)
        }

        val selectorList = mutableListOf<ColorPickerItem>()

        // Create each color picker item, checking for the first (because it needs extra margin)
        // and checking for the one which is selected (so it becomes selected)
        gradientList.mapIndexedTo(selectorList) { index, it ->

            ColorPickerItem(index == 0, currentColors == it, it) { itemClicked ->

                // When a value is selected, all others must be unselected.
                selectorList.forEach { listItem ->
                    if (listItem != itemClicked && listItem.isSwitchOn) {
                        listItem.deselectAndNotify()
                    }
                }

                listener.invoke(itemClicked.gradientColor)
            }
        }

        viewHolder.recycler.apply {
            this.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            this.adapter = GroupAdapter<GroupieViewHolder>().apply {
                add(Section(selectorList))
            }
            this.itemAnimator = itemAnimatorWithoutChangeAnimations()
        }
    }
}
