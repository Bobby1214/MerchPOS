package com.bobbykong.merchpos.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PaymentMethod(val label: String) {
    CASH("Cash"),
    DUITNOW_QR("DuitNow QR"),
    TOUCH_N_GO("Touch 'n Go");

    val isEWallet: Boolean get() = this != CASH
}

enum class OrderStatus { COMPLETED, CANCELLED }

enum class DiscountType { PERCENT, FIXED }

/** PERCENT: [value] is 1..100. FIXED: [value] is sen. */
data class OrderDiscount(val type: DiscountType, val value: Int)

@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("event_id"), Index("created_at")],
)
data class Order(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "event_id") val eventId: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    // Paid items only. Gifts are not included.
    @ColumnInfo(name = "subtotal_sen") val subtotalSen: Int,
    @ColumnInfo(name = "bundle_discount_sen", defaultValue = "0") val bundleDiscountSen: Int = 0,
    @ColumnInfo(name = "discount_type") val discountType: DiscountType? = null,
    @ColumnInfo(name = "discount_value") val discountValue: Int = 0,
    // The money the order discount actually took off
    @ColumnInfo(name = "discount_sen", defaultValue = "0") val discountSen: Int = 0,
    @ColumnInfo(name = "total_sen") val totalSen: Int,
    @ColumnInfo(name = "payment_method") val paymentMethod: PaymentMethod,
    val status: OrderStatus = OrderStatus.COMPLETED,
    @ColumnInfo(name = "is_edited") val isEdited: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long? = null,
) {
    val discount: OrderDiscount? get() = discountType?.let { OrderDiscount(it, discountValue) }
}
