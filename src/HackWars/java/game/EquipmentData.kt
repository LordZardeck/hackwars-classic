/*
 * EquipmentSheet.java
 *
 * Created on March 10, 2007, 10:40 AM
 *
 * Keeps track of hardware installed on computers.
 *
 */
package game

/**
 * By Alexander Morrison
 */

enum class EquipmentBonusType(val id: Int, val description: String) {
    HEAL_RATE(0, "Self-Healing"),
    DAMAGE_BONUS(1, "Segmenting"),
    WATCH_BONUS(2, "RAM Optimizing"),
    HD_BONUS(3, "RAID Controlling"),
    BANKING_BONUS(4, "Reimbursing"),
    HEAL_COST_BONUS(5, "System Monitoring"),
    CPU_BONUS(6, "Hyper-Threading"),
    MINING_BONUS(7, "Redirecting"),
    FREEZE_IMMUNE(8, "Non-Blocking Operations"),
    DESTROY_WATCH_IMMUNE(9, "Parity Checking");

    companion object {
        private val byId = entries.associateBy(EquipmentBonusType::id)
        fun fromId(id: Int): EquipmentBonusType? = byId[id]
    }
}

sealed class EquipmentDefinition<T>(val type: EquipmentBonusType, private val qualityValues: Array<T>) {
    open fun bonusForQuality(quality: Int): T = qualityValues[quality.coerceIn(0, qualityValues.size - 1)]

    open fun describe(): String = type.description

    object HealingEquipment : EquipmentDefinition<Int>(
        EquipmentBonusType.HEAL_RATE,
        arrayOf(-1, -1, -1, -1, -2, -2, -2, -2, -3, -3)
    )

    object DamageBonusEquipment : EquipmentDefinition<Float>(
        EquipmentBonusType.DAMAGE_BONUS,
        arrayOf(1.0f, 1.0f, 2.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f)
    )

    object BankingBonusEquipment : EquipmentDefinition<Float>(
        EquipmentBonusType.BANKING_BONUS,
        arrayOf(0.01f, 0.01f, 0.015f, 0.02f, 0.02f, 0.025f, 0.03f, 0.03f, 0.035f, 0.04f)
    )

    object HealCostBonusEquipment : EquipmentDefinition<Float>(
        EquipmentBonusType.HEAL_COST_BONUS,
        arrayOf(-0.05f, -0.10f, -0.15f, -0.20f, -0.25f, -0.30f, -0.35f, -0.40f, -0.45f, -0.50f)
    )

    object CpuBonusEquipment : EquipmentDefinition<Float>(
        EquipmentBonusType.CPU_BONUS,
        arrayOf(5.0f, 10.0f, 15.0f, 20.0f, 25.0f, 30.0f, 35.0f, 40.0f, 45.0f, 50.0f)
    )

    object WatchBonusEquipment : EquipmentDefinition<Int>(
        EquipmentBonusType.WATCH_BONUS,
        arrayOf(1, 1, 2, 2, 3, 3, 4, 4, 5, 5)
    )

    object HdBonusEquipment : EquipmentDefinition<Int>(
        EquipmentBonusType.HD_BONUS,
        arrayOf(-10, -5, 5, 5, 10, 15, 15, 20, 20, 30)
    )

    object MiningBonusEquipment : EquipmentDefinition<Float>(
        EquipmentBonusType.MINING_BONUS,
        arrayOf(1.0f, 1.0f, 2.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f)
    )

    object FreezeImmuneEquipment : EquipmentDefinition<Boolean>(
        EquipmentBonusType.FREEZE_IMMUNE, arrayOf()
    ) {
        override fun bonusForQuality(quality: Int) = true
    }

    object DestroyWatchImmuneEquipment : EquipmentDefinition<Boolean>(
        EquipmentBonusType.DESTROY_WATCH_IMMUNE, arrayOf()
    ) {
        override fun bonusForQuality(quality: Int) = true
    }

    companion object {
        val all = listOf<EquipmentDefinition<*>>(
            HealingEquipment,
            DamageBonusEquipment,
            WatchBonusEquipment,
            HdBonusEquipment,
            BankingBonusEquipment,
            HealCostBonusEquipment,
            CpuBonusEquipment,
            MiningBonusEquipment,
            FreezeImmuneEquipment,
            DestroyWatchImmuneEquipment
        )

        private val byType = all.associateBy { it.type }
        private val byId = all.associateBy { it.type.id }

        fun fromType(type: EquipmentBonusType) = byType[type]
        fun fromId(id: Int) = byId[id]
    }
}

class EquipmentData(bonusType: Int, bonusReturn: Int) {
    companion object {
        const val BOOLEAN: Int = 0
        const val FLOAT: Int = 1
        const val INT: Int = 2
    }

    var bonusChart: Any? = null
    var bonusNames = arrayOf<String>()
}
