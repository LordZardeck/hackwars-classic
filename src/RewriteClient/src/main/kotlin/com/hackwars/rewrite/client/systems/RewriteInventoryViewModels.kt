package com.hackwars.rewrite.client.systems

import com.hackwars.rewrite.client.mvc.RewriteViewModel
import com.hackwars.rewrite.protocol.ClientEquipmentSlot
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientInstalledEquipment
import com.hackwars.rewrite.protocol.ClientInstalledFirewall
import com.hackwars.rewrite.protocol.ClientPortState
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind

private const val INSTALL_EQUIPMENT_PROMPT: String = "Click Here To Install Equipment"
private const val INSTALL_FIREWALL_PROMPT: String = "Click Here To Install Firewall"

internal data class RewriteEquipmentManagerRow(
    val slot: ClientEquipmentSlot,
    val equipmentLabel: String,
    val maker: String,
    val durability: Int,
    val cpuBoost: Double,
    val memoryBoost: Int,
    val storageBoost: Int,
    val watchCapacityBoost: Int,
    val healCostMultiplier: Double,
    val healModifierDelta: Int,
    val freezeImmune: Boolean,
    val destroyWatchesImmune: Boolean,
    val installedEquipment: ClientInstalledEquipment? = null,
) {
    val cpuBoostDisplay: String = formatSystemMetric(cpuBoost)
    val healCostMultiplierDisplay: String = formatSystemMetric(healCostMultiplier)
}

internal data class RewriteFirewallManagerRow(
    val portNumber: Int,
    val firewallLabel: String,
    val kind: String,
    val maker: String,
    val strength: Int,
    val cpuCost: Double,
    val enabled: Boolean,
    val defaultPort: Boolean,
    val dummy: Boolean,
    val note: String,
    val installedFirewall: ClientInstalledFirewall? = null,
) {
    val cpuCostDisplay: String = formatSystemMetric(cpuCost)
}

internal data class RewriteEquipmentManagerViewModel(
    val rows: List<RewriteEquipmentManagerRow>,
    val selectedSlotLabel: String,
    val statusText: String,
    val errorText: String,
    val tableEnabled: Boolean,
    val installButtonEnabled: Boolean,
) : RewriteViewModel

internal data class RewriteFirewallManagerViewModel(
    val rows: List<RewriteFirewallManagerRow>,
    val selectedPortLabel: String,
    val statusText: String,
    val errorText: String,
    val tableEnabled: Boolean,
    val installButtonEnabled: Boolean,
) : RewriteViewModel

internal fun buildEquipmentManagerRows(snapshot: ClientGameSnapshot): List<RewriteEquipmentManagerRow> {
    return ClientEquipmentSlot.values().map { slot ->
        val installed = snapshot.hardware.equipmentSlots[slot.name]
        RewriteEquipmentManagerRow(
            slot = slot,
            equipmentLabel = installed?.name ?: INSTALL_EQUIPMENT_PROMPT,
            maker = installed?.maker?.ifBlank { "-" } ?: "-",
            durability = installed?.durability ?: 0,
            cpuBoost = installed?.cpuBoost ?: 0.0,
            memoryBoost = installed?.memoryBoost ?: 0,
            storageBoost = installed?.storageBoost ?: 0,
            watchCapacityBoost = installed?.watchCapacityBoost ?: 0,
            healCostMultiplier = installed?.healCostMultiplier ?: 1.0,
            healModifierDelta = installed?.healModifierDelta ?: 0,
            freezeImmune = installed?.freezeImmune ?: false,
            destroyWatchesImmune = installed?.destroyWatchesImmune ?: false,
            installedEquipment = installed,
        )
    }
}

internal fun buildFirewallManagerRows(snapshot: ClientGameSnapshot): List<RewriteFirewallManagerRow> {
    return snapshot.ports
        .sortedBy(ClientPortState::number)
        .map { port ->
            val installed = port.installedFirewall
            RewriteFirewallManagerRow(
                portNumber = port.number,
                firewallLabel = installed?.name ?: INSTALL_FIREWALL_PROMPT,
                kind = installed?.kind?.ifBlank { "-" } ?: "-",
                maker = installed?.maker?.ifBlank { "-" } ?: "-",
                strength = installed?.strength ?: 0,
                cpuCost = installed?.cpuCost ?: 0.0,
                enabled = port.enabled,
                defaultPort = port.defaultPort,
                dummy = port.dummy,
                note = port.note,
                installedFirewall = installed,
            )
        }
}

internal fun buildEquipmentManagerViewModel(
    rows: List<RewriteEquipmentManagerRow>,
    selectedSlot: ClientEquipmentSlot?,
    requestInFlight: Boolean,
    statusText: String,
    errorText: String,
): RewriteEquipmentManagerViewModel {
    val selected = rows.firstOrNull { it.slot == selectedSlot }
    return RewriteEquipmentManagerViewModel(
        rows = rows,
        selectedSlotLabel = selected?.let { "Selected Slot: ${it.slot.name}" } ?: "No slot selected",
        statusText = when {
            requestInFlight -> "Installing equipment..."
            rows.isEmpty() -> "No equipment slots available."
            statusText.isNotBlank() && statusText != " " -> statusText
            else -> " "
        },
        errorText = errorText.takeUnless { it.isBlank() } ?: " ",
        tableEnabled = !requestInFlight,
        installButtonEnabled = !requestInFlight && selected != null,
    )
}

internal fun buildFirewallManagerViewModel(
    rows: List<RewriteFirewallManagerRow>,
    selectedPortNumber: Int?,
    requestInFlight: Boolean,
    statusText: String,
    errorText: String,
): RewriteFirewallManagerViewModel {
    val selected = rows.firstOrNull { it.portNumber == selectedPortNumber }
    return RewriteFirewallManagerViewModel(
        rows = rows,
        selectedPortLabel = selected?.let { "Selected Port: ${it.portNumber}" } ?: "No port selected",
        statusText = when {
            requestInFlight -> "Installing firewall..."
            rows.isEmpty() -> "No firewall ports available."
            statusText.isNotBlank() && statusText != " " -> statusText
            else -> " "
        },
        errorText = errorText.takeUnless { it.isBlank() } ?: " ",
        tableEnabled = !requestInFlight,
        installButtonEnabled = !requestInFlight && selected != null,
    )
}

internal fun allowEquipmentManagerFile(
    file: ClientStoredFile,
    slot: ClientEquipmentSlot,
): Boolean {
    return file.kind == ClientStoredFileKind.EQUIPMENT_BINARY &&
        file.compiledBinary?.equipmentSlot == slot
}

internal fun allowFirewallManagerFile(file: ClientStoredFile): Boolean {
    return allowPortManagementFirewallFile(file)
}

private fun formatSystemMetric(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format("%.1f", value)
    }
}
