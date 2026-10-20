package com.bobbykong.merchpos

import com.bobbykong.merchpos.data.model.Category
import com.bobbykong.merchpos.data.model.Product
import com.bobbykong.merchpos.data.model.ProductPart
import com.bobbykong.merchpos.domain.Catalog
import com.bobbykong.merchpos.ui.products.ProductForm
import com.bobbykong.merchpos.ui.products.ProductKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogTest {
    private val posterA = Product(id = 1, name = "Poster A", category = "Poster", priceSen = 1500, stockQty = 4)
    private val posterB = Product(id = 2, name = "Poster B", category = "Poster", priceSen = 1500, stockQty = 1)
    private val set = Product(id = 3, name = "Poster Set", category = "Set", priceSen = 2500, stockQty = 0)
    private val film = Product(id = 4, name = "Instax film", category = "Material", priceSen = 0, stockQty = 7, isMaterial = true)
    private val normalCheki = Product(id = 5, name = "Normal cheki", category = "Cheki", priceSen = 1000, stockQty = 0)
    private val serviceCheki = Product(id = 6, name = "Service cheki", category = "Cheki", priceSen = 2500, stockQty = 0)

    private val catalog = Catalog(
        listOf(posterA, posterB, set, film, normalCheki, serviceCheki),
        listOf(
            ProductPart(3, 1, 1), ProductPart(3, 2, 1),
            ProductPart(5, 4, 1), ProductPart(6, 4, 1),
        ),
    )

    private fun stockOf(id: Int) = catalog.products.first { it.id == id }.stockQty

    @Test
    fun setStockIsLimitedByItsScarcestPart() {
        // Poster B has only 1, so only 1 set
        assertEquals(1, stockOf(3))
        val noB = Catalog(listOf(posterA, posterB.copy(stockQty = 0), set), listOf(ProductPart(3, 1, 1), ProductPart(3, 2, 1)))
        assertEquals(0, noB.products.first { it.id == 3 }.stockQty)
        // A deactivated part means the set can't be made
        val inactiveB = Catalog(listOf(posterA, posterB.copy(isActive = false), set), listOf(ProductPart(3, 1, 1), ProductPart(3, 2, 1)))
        assertEquals(0, inactiveB.products.first { it.id == 3 }.stockQty)
    }

    @Test
    fun chekiTypesShareTheFilm() {
        assertEquals(7, stockOf(5))
        assertEquals(7, stockOf(6))
        assertEquals(mapOf(4 to 5), catalog.needs(mapOf(5 to 2, 6 to 3)))
        // 4 normal + 3 service in the cart = all 7 film: no more of either
        val left = catalog.leftAfter(mapOf(5 to 4, 6 to 3))
        assertEquals(0, catalog.canMake(5, left))
        assertEquals(0, catalog.canMake(6, left))
        // Materials aren't sold, sets are
        assertEquals(listOf(5, 6, 1, 2, 3), catalog.sellable.map { it.id }) // Cheki, Poster, Set (A→Z, no order saved)
    }

    @Test
    fun sellingPartsAloneLowersTheSetCount() {
        // Poster B sold on its own: no set left
        assertEquals(0, catalog.canMake(3, catalog.leftAfter(mapOf(2 to 1))))
    }

    @Test
    fun partsRulesKeepOneLevel() {
        assertNull(catalog.partsError(0, isMaterial = false, newParts = mapOf(1 to 1, 4 to 2)))
        assertNotNull(catalog.partsError(0, isMaterial = true, newParts = mapOf(1 to 1)))
        assertNotNull(catalog.partsError(3, false, mapOf(3 to 1))) // itself
        assertNotNull(catalog.partsError(0, false, mapOf(3 to 1))) // a set can't be a part
        assertNotNull(catalog.partsError(4, false, mapOf(1 to 1))) // film is a part of cheki, can't get parts
        assertNotNull(catalog.partsError(1, false, mapOf(2 to 1))) // Poster A has stock 4 of its own (and is a part)
        assertNull(catalog.partsError(3, false, mapOf(1 to 2))) // editing the set is fine
    }

    @Test
    fun productFormKindsAndParts() {
        val form = ProductForm.from(set, catalog)
        assertEquals(ProductKind.USES_PARTS, form.kind)
        assertEquals(mapOf(1 to 1, 2 to 1), form.parts)
        assertNull(form.copy(stock = "").validate()) // no own stock needed
        assertNotNull(form.copy(parts = emptyMap()).validate())
        assertEquals(mapOf(1 to 2, 2 to 1), form.addPart(1).parts)
        assertEquals(mapOf(1 to 1), form.removeOnePart(2).parts)
        // Switching to Normal drops the parts when saving
        assertEquals(emptyMap<Int, Int>(), form.copy(kind = ProductKind.NORMAL).partsToSave)

        assertEquals(ProductKind.MATERIAL, ProductForm.from(film, catalog).kind)
        val material = ProductForm(name = "Film", category = "Material", stock = "10", kind = ProductKind.MATERIAL)
        assertNull(material.validate()) // price optional
    }

    @Test
    fun productsFollowCategoryOrderThenDraggedOrder() {
        val c = Catalog(
            listOf(
                Product(id = 1, name = "Zed", category = "Badge", priceSen = 1, stockQty = 1, sortOrder = 0),
                Product(id = 2, name = "Amy", category = "Badge", priceSen = 1, stockQty = 1, sortOrder = 1),
                Product(id = 3, name = "Poster", category = "Print", priceSen = 1, stockQty = 1),
                Product(id = 4, name = "Film", category = "Material", priceSen = 1, stockQty = 1),
            ),
            categoryOrder = listOf(Category("Print", 0), Category("Badge", 1)),
        )
        // Print first (dragged to top), Badge in dragged order (Zed before Amy), Material has no place → last
        assertEquals(listOf(3, 1, 2, 4), c.products.map { it.id })
        assertEquals(listOf("Print", "Badge", "Material"), c.categoriesOf())
    }
}
