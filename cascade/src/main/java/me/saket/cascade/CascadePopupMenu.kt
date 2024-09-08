@file:SuppressLint("RestrictedApi")
@file:Suppress("DeprecatedCallableAddReplaceWith")

package me.saket.cascade

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.View.SCROLLBARS_INSIDE_OVERLAY
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.annotation.MenuRes
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.view.menu.SubMenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import me.saket.cascade.internal.OverScrollIfContentScrolls
import me.saket.cascade.internal.dip
import me.saket.cascade.internal.setCallback
import java.lang.reflect.Method
import java.util.Stack
import kotlin.DeprecationLevel.ERROR
import kotlin.math.ceil

open class CascadePopupMenu @JvmOverloads constructor(
  private val context: Context,
  private val anchor: View,
  private var gravity: Int = Gravity.NO_GRAVITY,
  private val styler: Styler = Styler(),
  private val defStyleAttr: Int = android.R.style.Widget_Material_PopupMenu,
  private val backNavigator: CascadeBackNavigator = CascadeBackNavigator()
) {
  val menu: Menu get() = menuBuilder
  val popup = CascadePopupWindow(context, styler.popupElevation, defStyleAttr)

  internal var menuBuilder = MenuBuilder(context)
  private val backstack = Stack<Menu>()
  private val themeAttrs get() = popup.themeAttrs
  private val sharedViewPool = RecycledViewPool()
  private val popupMaxWidth: Int by lazy {
    context.dip(280)
  }
  private val popupMinWidth: Int by lazy {
    context.dip(112)
  }
  private val popupWidthUnit: Int by lazy {
    context.dip(68)
  }
  private var popupWidth = 0

  private var menuListRecyclerView: RecyclerView? = null
  private var cascadeMenuAdapter: Adapter<ViewHolder>? = null


  companion object {
    private var getMaxAvailableHeightMethod: Method? = null
    private const val TAG = "CascadePopupMenu"

    init {
      try {
        getMaxAvailableHeightMethod = PopupWindow::class.java.getDeclaredMethod(
          "getMaxAvailableHeight",
          View::class.java,
          Int::class.javaPrimitiveType,
          Boolean::class.javaPrimitiveType
        )
      } catch (e: NoSuchMethodException) {
        Log.i(
          TAG,
          "Could not find method getMaxAvailableHeight(View, int, boolean)" + " on PopupWindow. Oh well."
        )
      }
    }
  }


  class Styler(
    /**
     * Popup's background drawable. Also used on sub-menus as an opaque background
     * to avoid cross-drawing of menus during their entry/exit transition. Return
     * `null` to use the background set in XML theme.
     */
    val background: () -> Drawable? = { null },
    val menuList: (RecyclerView) -> Unit = {},
    val menuTitle: (MenuHeaderViewHolder) -> Unit = {},
    val menuItem: (MenuItemViewHolder) -> Unit = {},
    val popupElevation: Float = 0f,
    val iconSize: Int = 0,
  )

  init {
    backNavigator.onBackNavigate = {
      if (backstack.isNotEmpty() && backstack.peek() is SubMenu) {
        val currentMenu = backstack.pop() as SubMenuBuilder
        showMenu(currentMenu.parentMenu as MenuBuilder, goingForward = false)
      }
    }
  }

  fun show() {
    popup.setMargins(
      start = context.dip(4),
      end = context.dip(4),
      bottom = context.dip(4)
    )
    styler.background()?.let {
      popup.contentView.background = it
    }

    showMenu(menuBuilder, goingForward = true)

    // Max height available on the screen for a popupMenu.
    val ignoreBottomDecorations = popup.inputMethodMode == PopupWindow.INPUT_METHOD_NOT_NEEDED
    val maxHeight = getMaxAvailableHeight(
      anchor, 0,
      ignoreBottomDecorations
    )

    // PopupWindow moves the popup to align with the anchor if a fixed width
    // is known before hand. Note to self: If fixedWidth ever needs to be
    // removed, copy over MenuPopup.measureIndividualMenuWidth().
    popup.width = measureMenuSizeAndGetWidth(cascadeMenuAdapter)
    popup.height = measureHeightOfChildrenCompat(maxHeight) + context.dip(4) // Doesn't work on API 21 without this.

    popup.showAsDropDown(anchor, 0, 0, gravity)
  }

  private fun measureMenuSizeAndGetWidth(adapter: Adapter<ViewHolder>?): Int {
    popupWidth = popupMinWidth
    if (adapter == null) {
      return popupWidth
    }
    val parent = FrameLayout(context)
    val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    val count = adapter.itemCount
    for (i in 0 until count) {
      val positionType = adapter.getItemViewType(i)

      val vh = adapter.createViewHolder(parent, positionType)
      adapter.bindViewHolder(vh, i)
      val itemView = vh.itemView
      itemView.measure(widthMeasureSpec, heightMeasureSpec)

      val itemWidth = itemView.measuredWidth
      if (itemWidth >= popupMaxWidth) {
        return popupMaxWidth
      } else if (itemWidth > popupWidth) {
        popupWidth = itemWidth
      }
    }
    popupWidth = ceil(popupWidth.toDouble() / popupWidthUnit).toInt() * popupWidthUnit
    return popupWidth
  }

  private fun measureHeightOfChildrenCompat(maxHeight: Int): Int {
    val parent = FrameLayout(context)
    val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY)

    // Include the padding of the list
    var returnedHeight = 0

    val count = cascadeMenuAdapter?.itemCount ?: 0
    for (i in 0 until count) {
      val positionType = cascadeMenuAdapter!!.getItemViewType(i)

      val vh = cascadeMenuAdapter!!.createViewHolder(parent, positionType)
      cascadeMenuAdapter!!.bindViewHolder(vh, i)
      val itemView = vh.itemView

      // Compute child height spec
      val heightMeasureSpec: Int
      var childLp: ViewGroup.LayoutParams? = itemView.layoutParams

      if (childLp == null) {
        childLp = generateDefaultLayoutParams()
        itemView.layoutParams = childLp
      }

      heightMeasureSpec = if (childLp.height > 0) {
        View.MeasureSpec.makeMeasureSpec(
          childLp.height,
          View.MeasureSpec.EXACTLY
        )
      } else {
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
      }
      itemView.measure(widthMeasureSpec, heightMeasureSpec)
      itemView.forceLayout()

      val marginLayoutParams = childLp as? ViewGroup.MarginLayoutParams
      val topMargin = marginLayoutParams?.topMargin ?: 0
      val bottomMargin = marginLayoutParams?.bottomMargin ?: 0
      val verticalMargin = topMargin + bottomMargin

      returnedHeight += itemView.measuredHeight + verticalMargin
      if (returnedHeight >= maxHeight) {
        return maxHeight
      }
    }
    return returnedHeight
  }

  private fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
    return RecyclerView.LayoutParams(
      MATCH_PARENT,
      WRAP_CONTENT
    )
  }

  private fun getMaxAvailableHeight(anchor: View, yOffset: Int, ignoreBottomDecorations: Boolean): Int {
    getMaxAvailableHeightMethod?.let {
      try {
        return it.invoke(
          popup, anchor, yOffset,
          ignoreBottomDecorations
        ) as Int
      } catch (e: Exception) {
        Log.i(
          TAG,
          "Could not call getMaxAvailableHeightMethod(View, int, boolean)" + " on PopupWindow. Using the public version."
        )
      }
    }
    return popup.getMaxAvailableHeight(anchor, yOffset)
  }


  /**
   * Navigate to the last menu. Also see [CascadeBackNavigator].
   *
   * FYI jumping over multiple back-stack entries isn't supported
   * very well, so avoid navigating multiple menus on a single click.
   */
  fun navigateBack(): Boolean {
    return backNavigator.navigateBack()
  }

  private fun showMenu(menu: MenuBuilder, goingForward: Boolean) {
    menuListRecyclerView = RecyclerView(context).apply {
      layoutManager = LinearLayoutManager(context).also {
        it.recycleChildrenOnDetach = true
        setRecycledViewPool(sharedViewPool)
      }
      isVerticalScrollBarEnabled = true
      scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
      styler.menuList(this)

      addOnScrollListener(OverScrollIfContentScrolls())
      cascadeMenuAdapter = CascadeMenuAdapter(
        items = buildModels(menu, canNavigateBack = backstack.isNotEmpty()),
        styler = styler,
        themeAttrs = themeAttrs,
        onTitleClick = { navigateBack() },
        onItemClick = { handleItemClick(it) }
      )
      adapter = cascadeMenuAdapter
      layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    backstack.push(menu)
    popup.contentView.show(menuListRecyclerView!!, goingForward)
  }

  protected open fun handleItemClick(item: MenuItem) {
    if (item.hasSubMenu()) {
      showMenu(item.subMenu as MenuBuilder, goingForward = true)
      return
    }

    val backstackBefore = backstack.peek()
    (item as MenuItemImpl).invoke()

    if (backstack.peek() === backstackBefore) {
      // Back wasn't called. Item click wasn't handled either.
      // Dismiss the popup because there's nothing else to do.
      popup.dismiss()
    }
  }

// === APIs to maintain compatibility with PopupMenu === //

  fun inflate(@MenuRes menuRes: Int) =
    SupportMenuInflater(context).inflate(menuRes, menuBuilder)

  fun setOnMenuItemClickListener(listener: PopupMenu.OnMenuItemClickListener?) =
    menuBuilder.setCallback(listener)

  fun dismiss() =
    popup.dismiss()

  @get:JvmName("getDragToOpenListener")
  @Deprecated("CascadeMenu doesn't support drag-to-open.", level = ERROR)
  val dragToOpenListener: View.OnTouchListener
    get() = error("can't")
}
