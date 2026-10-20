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

// Phase 2 (order discount). The columns exist now so no DB migration is needed later.
enum class DiscountType { PERCENT, FIXED }

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
    @ColumnInfo(name = "subtotal_sen") val subtotalSen: Int,
    @ColumnInfo(name = "discount_type") val discountType: DiscountType? = null,
    @ColumnInfo(name = "discount_value") val discountValue: Int = 0,
    @ColumnInfo(name = "total_sen") val totalSen: Int,
    @ColumnInfo(name = "payment_method") val paymentMethod: PaymentMethod,
    val status: OrderStatus = OrderStatus.COMPLETED,
    @ColumnInfo(name = "is_edited") val isEdited: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long? = null,
)
