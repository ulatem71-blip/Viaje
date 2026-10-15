package com.viajerentable.app

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class ScreenBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    fun toShortString(): String {
        return "[$left,$top][$right,$bottom]"
    }
}

data class CardTarget(
    val fare: Double,
    val cardText: String,
    val buttonType: String,
    val buttonBoundsInScreen: ScreenBounds,
    val xBoundsInScreen: ScreenBounds?,
    val isEnabled: Boolean,
    val isVisibleToUser: Boolean
)

data class AmbiguousCard(
    val buttonType: String,
    val reason: String,
    val cardText: String
)

data class OfferButtonLocationResult(
    val cards: List<CardTarget>,
    val ambiguous: List<AmbiguousCard>
)

internal data class OfferTreeNode(
    val packageName: String = "com.ubercab.driver",
    val className: String = "",
    val text: String? = null,
    val contentDescription: String? = null,
    val isClickable: Boolean = false,
    val isEnabled: Boolean = true,
    val isVisibleToUser: Boolean = true,
    val boundsInScreen: ScreenBounds = ScreenBounds(0, 0, 0, 0),
    val children: List<OfferTreeNode> = emptyList()
)

/** Locates offer buttons without interacting with Uber controls. */
object OfferButtonLocator {
    private val farePattern = Regex("""CRC\s*[0-9][0-9,.]*""", RegexOption.IGNORE_CASE)
    private val buttonTypes = setOf("Aceptar", "Me interesa")

    fun locate(windowRoot: AccessibilityNodeInfo): OfferButtonLocationResult {
        if (windowRoot.packageName?.toString() != UBER_DRIVER_PACKAGE) {
            return OfferButtonLocationResult(emptyList(), emptyList())
        }
        return locate(copyTree(windowRoot))
    }

    internal fun locate(root: OfferTreeNode): OfferButtonLocationResult {
        if (root.packageName != UBER_DRIVER_PACKAGE) {
            return OfferButtonLocationResult(emptyList(), emptyList())
        }

        val cards = mutableListOf<CardTarget>()
        val ambiguous = mutableListOf<AmbiguousCard>()
        val seenButtons = mutableSetOf<String>()
        visit(root, emptyList()) { node, path ->
            val buttonType = node.text?.trim()
            if (node.className.substringAfterLast('.').equals("Button", true) &&
                buttonType != null && buttonType in buttonTypes
            ) {
                val buttonKey = "${buttonType}:${node.boundsInScreen.toShortString()}"
                if (seenButtons.add(buttonKey)) {
                    locateForButton(node, path, buttonType, cards, ambiguous)
                }
            }
        }
        return OfferButtonLocationResult(cards, ambiguous)
    }

    private fun locateForButton(
        button: OfferTreeNode,
        path: List<OfferTreeNode>,
        buttonType: String,
        cards: MutableList<CardTarget>,
        ambiguous: MutableList<AmbiguousCard>
    ) {
        var selected: OfferTreeNode? = null
        var selectedText = ""
        var selectedFareMatches = 0
        var selectedAcceptCount = 0

        for (index in path.lastIndex - 1 downTo 0) {
            val ancestor = path[index]
            val text = subtreeText(ancestor)
            val fares = farePattern.findAll(text).count()
            val hasTrip = text.contains("Viaje:", ignoreCase = true)
            if (hasTrip) {
                selected = ancestor
                selectedText = text
                selectedFareMatches = fares
                selectedAcceptCount = countButtons(ancestor, "Aceptar")
                break
            }
        }

        if (selected == null) {
            // A dialog or notification with an isolated Aceptar button is intentionally ignored.
            return
        }

        if (selectedFareMatches != 1 || selectedAcceptCount > 1) {
            val reason = if (selectedFareMatches != 1) {
                "tarifas=$selectedFareMatches"
            } else {
                "más de un botón Aceptar"
            }
            ambiguous.add(AmbiguousCard(buttonType, reason, selectedText))
            return
        }

        val parsed = OfferParser.parseAll(appendButton(selectedText, buttonType))
        val parsedForCard = parsed.filter { it.fare > 0.0 }
        if (parsedForCard.size != 1 || !parsedForCard.single().hasRouteDetails) {
            ambiguous.add(AmbiguousCard(buttonType, "tarjeta no parseable de forma única", selectedText))
            return
        }

        val target = parsedForCard.single()
        val xButton = findXButton(selected, selected.boundsInScreen)
        cards.add(
            CardTarget(
                fare = target.fare,
                cardText = appendButton(selectedText, buttonType),
                buttonType = buttonType,
                buttonBoundsInScreen = button.boundsInScreen,
                xBoundsInScreen = xButton?.boundsInScreen,
                isEnabled = button.isEnabled,
                isVisibleToUser = button.isVisibleToUser
            )
        )
    }

    private fun findXButton(card: OfferTreeNode, cardBounds: ScreenBounds): OfferTreeNode? {
        var found: OfferTreeNode? = null
        visit(card, emptyList()) { node, _ ->
            if (found != null || node === card) {
                return@visit
            }
            val isButton = node.className.substringAfterLast('.').equals("Button", true)
            val hasNoLabel = node.text.isNullOrBlank() && node.contentDescription.isNullOrBlank()
            val bounds = node.boundsInScreen
            val rightNear = kotlin.math.abs(cardBounds.right - bounds.right) < X_RIGHT_TOLERANCE_PX
            val topNear = kotlin.math.abs(cardBounds.top - bounds.top) < X_TOP_TOLERANCE_PX
            if (isButton && hasNoLabel && node.isClickable && rightNear && topNear) {
                found = node
            }
        }
        return found
    }

    private fun countButtons(root: OfferTreeNode, label: String): Int {
        var count = 0
        visit(root, emptyList()) { node, _ ->
            if (node.className.substringAfterLast('.').equals("Button", true) &&
                node.text?.trim().equals(label, ignoreCase = false)
            ) {
                count++
            }
        }
        return count
    }

    private fun subtreeText(root: OfferTreeNode): String {
        val parts = mutableListOf<String>()
        visit(root, emptyList()) { node, _ ->
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(parts::add)
            if (node.text.isNullOrBlank()) {
                node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(parts::add)
            }
        }
        return parts.joinToString(" | ")
    }

    private fun appendButton(text: String, buttonType: String): String {
        return if (text.contains(buttonType, ignoreCase = true)) text else "$text | $buttonType"
    }

    private fun visit(
        node: OfferTreeNode,
        parentPath: List<OfferTreeNode>,
        action: (OfferTreeNode, List<OfferTreeNode>) -> Unit
    ) {
        val path = parentPath + node
        action(node, path)
        node.children.forEach { child -> visit(child, path, action) }
    }

    private fun copyTree(node: AccessibilityNodeInfo): OfferTreeNode {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val children = mutableListOf<OfferTreeNode>()
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                children.add(copyTree(child))
            } finally {
                child.recycle()
            }
        }
        return OfferTreeNode(
            packageName = node.packageName?.toString().orEmpty(),
            className = node.className?.toString().orEmpty(),
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            isClickable = node.isClickable,
            isEnabled = node.isEnabled,
            isVisibleToUser = node.isVisibleToUser,
            boundsInScreen = bounds,
            children = children
        )
    }

    private const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"
    private const val X_RIGHT_TOLERANCE_PX = 80
    private const val X_TOP_TOLERANCE_PX = 120
}
