package com.viajerentable.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferButtonLocatorTest {
    @Test
    fun locatesOneCardAndItsCloseButton() {
        val result = OfferButtonLocator.locate(card(1803.33, 0, "Aceptar"))

        assertEquals(1, result.cards.size)
        assertTrue(result.ambiguous.isEmpty())
        val target = result.cards.single()
        assertEquals(1803.33, target.fare, 0.01)
        assertEquals("Aceptar", target.buttonType)
        assertEquals(ScreenBounds(300, 100, 390, 150), target.buttonBoundsInScreen)
        assertEquals(ScreenBounds(350, 10, 395, 55), target.xBoundsInScreen)
        assertTrue(target.isEnabled)
        assertTrue(target.isVisibleToUser)
        assertTrue(target.cardText.contains("Viaje:"))
    }

    @Test
    fun locatesTwoVisibleCardsWithoutMixingTariffs() {
        val root = OfferTreeNode(
            className = "android.widget.FrameLayout",
            children = listOf(
                card(1803.33, 0, "Aceptar"),
                card(2400.50, 300, "Me interesa")
            )
        )

        val result = OfferButtonLocator.locate(root)

        assertEquals(2, result.cards.size)
        assertEquals(listOf(1803.33, 2400.50), result.cards.map { it.fare })
        assertEquals(listOf("Aceptar", "Me interesa"), result.cards.map { it.buttonType })
        assertTrue(result.ambiguous.isEmpty())
    }

    @Test
    fun ignoresDialogAcceptButtonWithoutFare() {
        val dialog = OfferTreeNode(
            className = "android.widget.FrameLayout",
            children = listOf(
                OfferTreeNode(
                    className = "android.widget.Button",
                    text = "Aceptar",
                    boundsInScreen = ScreenBounds(80, 500, 260, 560)
                )
            )
        )

        val result = OfferButtonLocator.locate(dialog)

        assertTrue(result.cards.isEmpty())
        assertTrue(result.ambiguous.isEmpty())
    }

    @Test
    fun marksCardWithTwoFaresAsAmbiguous() {
        val original = card(1803.33, 0, "Aceptar")
        val children = original.children.toMutableList()
        children.add(
            2,
            OfferTreeNode(className = "android.widget.TextView", text = "CRC2,000.00")
        )
        val ambiguousCard = original.copy(children = children)

        val result = OfferButtonLocator.locate(ambiguousCard)

        assertTrue(result.cards.isEmpty())
        assertEquals(1, result.ambiguous.size)
        assertTrue(result.ambiguous.single().reason.contains("tarifas=2"))
    }

    @Test
    fun ignoresWindowFromAnotherPackage() {
        val root = card(1803.33, 0, "Aceptar").copy(packageName = "com.other.app")
        val result = OfferButtonLocator.locate(root)

        assertTrue(result.cards.isEmpty())
        assertTrue(result.ambiguous.isEmpty())
    }

    private fun card(fare: Double, offset: Int, buttonType: String): OfferTreeNode {
        val cardBounds = ScreenBounds(0, offset, 400, offset + 280)
        val close = OfferTreeNode(
            className = "android.widget.Button",
            isClickable = true,
            boundsInScreen = ScreenBounds(350, offset + 10, 395, offset + 55)
        )
        val scroll = OfferTreeNode(
            className = "android.widget.ScrollView",
            children = listOf(
                text("UberX"),
                text("Exclusivo"),
                text("CRC" + String.format(java.util.Locale.US, "%,.2f", fare)),
                text("4.93 (1547)"),
                text("A 6 min (2.0 km)"),
                text("Calle de recogida"),
                text("Viaje: 15 min (4.9 km)"),
                text("Ruta de destino")
            )
        )
        val button = OfferTreeNode(
            className = "android.widget.Button",
            text = buttonType,
            isEnabled = true,
            isVisibleToUser = true,
            boundsInScreen = ScreenBounds(300, offset + 100, 390, offset + 150)
        )
        return OfferTreeNode(
            className = "androidx.cardview.widget.CardView",
            isClickable = true,
            boundsInScreen = cardBounds,
            children = listOf(close, scroll, button)
        )
    }

    private fun text(value: String): OfferTreeNode {
        return OfferTreeNode(
            className = "android.widget.TextView",
            text = value
        )
    }
}
