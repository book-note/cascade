@file:OptIn(ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class)

package me.saket.cascade

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowLeft
import androidx.compose.material.icons.rounded.ArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection.Ltr
import androidx.compose.ui.unit.LayoutDirection.Rtl
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import me.saket.cascade.internal.cascadeTransitionSpec
import me.saket.cascade.internal.clickableWithoutRipple
import me.saket.cascade.internal.clipReveal

/**
 * Material Design dropdown menu with support for nested menus.
 * See [DropdownMenu] for documentation about its parameters.
 *
 * Example usage:
 *
 * ```
 * var expanded by rememberSaveable { mutableStateOf(false) }
 *
 * CascadeDropdownMenu(
 *   expanded = expanded,
 *   onDismissRequest = { expanded = false }
 * ) {
 *   DropdownMenuItem(
 *     text = { Text("Horizon") },
 *     children = {
 *       DropdownMenuItem(
 *         text = { Text("Zero Dawn") },
 *         onClick = { ... }
 *       )
 *       DropdownMenuItem(
 *         text = { Text("Forbidden West") },
 *         onClick = { ... }
 *       )
 *     }
 *   )
 * }
 * ```
 */
@Composable
fun CascadeDropdownMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  offset: DpOffset = DpOffset(0.dp, 0.dp),
  properties: PopupProperties = PopupProperties(focusable = true),
  fixedWidth: Dp = 196.dp,
  state: CascadeState = rememberCascadeState(),
  content: @Composable CascadeColumnScope.() -> Unit
) {
  val expandedStates = remember { MutableTransitionState(false) }
  expandedStates.targetState = expanded

  if (expandedStates.currentState || expandedStates.targetState) {
    val transformOriginState = remember { mutableStateOf(TransformOrigin(1f, 0f)) }
    val onDismissRequest by rememberUpdatedState(onDismissRequest)

    Popup(
      onDismissRequest = onDismissRequest,
      properties = properties,
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .clickableWithoutRipple { onDismissRequest() },
        contentAlignment = Alignment.TopEnd
      ) {
        AnimatedPopupContent(
          expandedStates = expandedStates,
          transformOriginState = transformOriginState,
        ) {
          CascadeDropdownMenuContent(
            modifier = modifier
              .requiredWidth(fixedWidth)
              .wrapContentHeight(),
            state = state,
            content = content
          )
        }
      }
    }
  }
}

@Composable
internal fun AnimatedPopupContent(
  expandedStates: MutableTransitionState<Boolean>,
  transformOriginState: MutableState<TransformOrigin>,
  content: @Composable () -> Unit
) {
  val isExpandedTransition = updateTransition(expandedStates, label = "CascadeDropDownMenu")
  val scale by isExpandedTransition.animateFloat(
    transitionSpec = { tween(if (false isTransitioningTo true) InTransitionDuration else OutTransitionDuration) },
    label = "scale",
    targetValueByState = { if (it) 1f else 0f }
  )
  val alpha by isExpandedTransition.animateFloat(
    transitionSpec = { tween(if (false isTransitioningTo true) InTransitionDuration else OutTransitionDuration) },
    label = "alpha",
    targetValueByState = { if (it) 1f else 0f }
  )
  val reveal = isExpandedTransition.animateFloat(
    transitionSpec = {
      tween((if (false isTransitioningTo true) InTransitionDuration * 1.2 else OutTransitionDuration * 0.8).toInt())
    },
    label = "clip",
    targetValueByState = { if (it) 1f else 0.25f }
  )

  val density = LocalDensity.current
  var contentSize by remember { mutableStateOf(DpSize.Zero) }

  Box(
    Modifier
      .requiredSize(contentSize)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.transformOrigin = transformOriginState.value
      }
      .shadow(60.dp, clip = false),
  )
  Box(
    modifier = Modifier
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
        this.transformOrigin = transformOriginState.value
      }
      .clipReveal(shape = MaterialTheme.shapes.extraSmall, transitionProgress = reveal)
      .onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInParent()
        contentSize = density.run {
          DpSize(width = bounds.width.toDp(), height = bounds.height.toDp())
        }
      },
    content = { content() },
  )
}

internal const val InTransitionDuration = 300
internal const val OutTransitionDuration = 300

@Composable
internal fun CascadeDropdownMenuContent(
  state: CascadeState,
  modifier: Modifier = Modifier,
  content: @Composable CascadeColumnScope.() -> Unit
) {
  DisposableEffect(Unit) {
    onDispose {
      state.resetBackStack()
    }
  }

  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.extraSmall,
    color = MaterialTheme.colorScheme.surface,
    contentColor = contentColorFor(MaterialTheme.colorScheme.surface),
    tonalElevation = 3.dp,
  ) {
    val layoutDirection = LocalLayoutDirection.current
    AnimatedContent(
      targetState = state.backStackSnapshot(),
      transitionSpec = { cascadeTransitionSpec(layoutDirection) }
    ) { backStackSnapshot ->
      Column(
        Modifier
          // Provide a solid background color to prevent the
          // content of sub-menus from leaking into each other.
          .background(MaterialTheme.colorScheme.surface)
          // Block navigation while a transition is already playing because the
          // current transitionSpec isn't great at handling another navigation
          // while one is already running.
          .pointerInteropFilter { transition.isRunning }
      ) {
        val currentContent = backStackSnapshot.topMostEntry?.childrenContent ?: content
        backStackSnapshot.topMostEntry?.header?.invoke()

        val contentScope = remember { CascadeColumnScope(state) }
        contentScope.currentContent()
      }
    }
  }
}

@Immutable
@LayoutScopeMarker
interface CascadeColumnScope : ColumnScope {
  val cascadeState: CascadeState

  /**
   * Material Design dropdown menu item that navigates to a sub-menu on click.
   * See [androidx.compose.material3.DropdownMenuItem] for documentation about its parameters.
   *
   * For sub-menus, cascade will automatically navigate to their parent menu when their
   * header is clicked. For manual navigation, [CascadeState.navigateBack] can be used.
   *
   * ```
   * val state = rememberCascadeState()
   *
   * CascadeDropdownMenu(state = state, ...) {
   *   DropdownMenuItem(
   *     text = { Text("Are you sure?"),
   *     children = {
   *       DropdownMenuItem(
   *          text = { Text("Not really") },
   *          onClick = { state.navigateBack() }
   *        )
   *     }
   *   )
   * }
   * ```
   */
  @Composable
  fun DropdownMenuItem(
    text: @Composable () -> Unit,
    children: @Composable CascadeColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    childrenHeader: @Composable () -> Unit = { DropdownMenuHeader(text = text) },
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  ) {
    DropdownMenuItem(
      text = text,
      onClick = {
        cascadeState.navigateTo(
          CascadeBackStackEntry(
            header = childrenHeader,
            childrenContent = children
          )
        )
      },
      modifier = modifier,
      leadingIcon = leadingIcon,
      trailingIcon = {
        Row(verticalAlignment = CenterVertically) {
          trailingIcon?.invoke()

          val requiredGapWithEdge = 4.dp
          val iconOffset = contentPadding.calculateEndPadding(LocalLayoutDirection.current) - requiredGapWithEdge
          Icon(
            modifier = Modifier.offset(x = iconOffset),
            imageVector = when (LocalLayoutDirection.current) {
              Ltr -> Icons.Rounded.ArrowRight
              Rtl -> Icons.Rounded.ArrowLeft
            },
            contentDescription = null
          )
        }
      },
      enabled = enabled,
      colors = colors,
      contentPadding = contentPadding,
      interactionSource = interactionSource,
    )
  }
}

/**
 * Displays `text` with a back icon. Navigates to its parent menu when clicked.
 */
// FYI making this function non-inline causes a strange bug where
// `text` stops changing when navigating deeper into a sub-menu.
@Composable
inline fun CascadeColumnScope.DropdownMenuHeader(
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(vertical = 4.dp),
  crossinline text: @Composable () -> Unit,
) {
  Row(
    modifier = modifier
      .clickable { cascadeState.navigateBack() }
      .fillMaxWidth()
      .padding(contentPadding),
    verticalAlignment = CenterVertically
  ) {
    val headerColor = LocalContentColor.current.copy(alpha = 0.6f)
    val headerStyle = MaterialTheme.typography.labelLarge.run { // labelLarge is also used by DropdownMenuItem().
      copy(
        fontSize = fontSize * 0.9f,
        letterSpacing = letterSpacing * 0.9f
      )
    }
    CompositionLocalProvider(
      LocalContentColor provides headerColor,
      LocalTextStyle provides headerStyle
    ) {
      Icon(
        modifier = Modifier.requiredSize(32.dp),
        imageVector = when (LocalLayoutDirection.current) {
          Ltr -> Icons.Rounded.ArrowLeft
          Rtl -> Icons.Rounded.ArrowRight
        },
        contentDescription = null
      )
      Box(Modifier.weight(1f)) {
        text()
      }
    }
  }
}

private fun ColumnScope.CascadeColumnScope(state: CascadeState): CascadeColumnScope =
  object : CascadeColumnScope, ColumnScope by this {
    override val cascadeState get() = state
  }
