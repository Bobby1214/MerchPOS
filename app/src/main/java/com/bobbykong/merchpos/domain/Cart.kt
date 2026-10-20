package com.bobbykong.merchpos.domain

import com.bobbykong.merchpos.data.CartItem

/** One cart line = one product, paid or gift. The same product can be in the cart twice: 3 paid + 1 gift. */
data class CartKey(val productId: Int, val isGift: Boolean)

// The cart is an immutable Map<CartKey, qty>. Each change returns a NEW map, so StateFlow sees the change.

fun Map<CartKey, Int>.qtyOf(productId: Int): Int = entries.filter { it.key.productId == productId }.sumOf { it.value }

fun Map<CartKey, Int>.plusOne(key: CartKey): Map<CartKey, Int> = this + (key to (this[key] ?: 0) + 1)

fun Map<CartKey, Int>.minusOne(key: CartKey): Map<CartKey, Int> {
    val qty = (this[key] ?: return this) - 1
    return if (qty <= 0) this - key else this + (key to qty)
}

/** Moves the whole line between paid and gift, merging with a line that's already there. */
fun Map<CartKey, Int>.toggleGift(key: CartKey): Map<CartKey, Int> {
    val qty = this[key] ?: return this
    val other = key.copy(isGift = !key.isGift)
    return (this - key) + (other to (this[other] ?: 0) + qty)
}

fun Map<CartKey, Int>.toCartItems(): List<CartItem> = map { (key, qty) -> CartItem(key.productId, qty, key.isGift) }
