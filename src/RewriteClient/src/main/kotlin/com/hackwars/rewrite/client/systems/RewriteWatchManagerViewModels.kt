package com.hackwars.rewrite.client.systems

import com.hackwars.rewrite.client.mvc.RewriteViewModel
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientInstalledWatch
import com.hackwars.rewrite.protocol.ClientScriptFamily
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import com.hackwars.rewrite.protocol.ClientWatchKind

internal val WATCH_TYPE_LABELS: List<String> = listOf("Health", "Petty Cash", "Scan")
internal val WATCH_FIREWALL_LABELS: List<String> = listOf(
    "None",
    "PortProtector",
    "PwnPreventer",
    "DataShield",
    "PacketBuster",
    "TrafficTender",
    "DigitalFortress",
    "ForceField",
    "RubyGuardian",
    "DiamondDefender",
    "ADNArmour",
)

internal data class RewriteWatchManagerRow(
    val index: Int,
    val type: ClientWatchKind,
    val enabled: Boolean,
    val portChoices: List<Int>,
    val installPort: Int,
    val cpuCost: Double,
    val note: String,
    val observedPorts: List<Int>,
    val searchFirewallType: Int,
    val quantityThreshold: Double,
    val installedWatch: ClientInstalledWatch,
) {
    val typeLabel: String = watchTypeLabel(type)
    val cpuCostDisplay: String = formatWatchMetric(cpuCost)
    val scanType: Boolean = type == ClientWatchKind.SCAN
}

internal data class RewriteWatchManagerViewModel(
    val rows: List<RewriteWatchManagerRow>,
    val statusText: String,
    val errorText: String,
    val installMenuEnabled: Boolean,
) : RewriteViewModel

internal fun buildWatchManagerRows(snapshot: ClientGameSnapshot): List<RewriteWatchManagerRow> {
    val availablePorts = snapshot.ports.map { it.number }.sorted()
    return snapshot.watches.watches.mapIndexed { index, watch ->
        val portChoices = when (watch.kind) {
            ClientWatchKind.SCAN -> emptyList()
            else -> (availablePorts + watch.installPort).distinct().sorted()
        }
        RewriteWatchManagerRow(
            index = index,
            type = watch.kind,
            enabled = watch.enabled,
            portChoices = portChoices,
            installPort = watch.installPort,
            cpuCost = watch.cpuCost,
            note = watch.note,
            observedPorts = watch.observedPorts.sorted(),
            searchFirewallType = watch.searchFirewallType,
            quantityThreshold = watch.quantityThreshold,
            installedWatch = watch,
        )
    }
}

internal fun allowWatchManagerFile(file: ClientStoredFile): Boolean {
    return file.kind == ClientStoredFileKind.APPLICATION_BINARY &&
        file.compiledBinary?.scriptFamily == ClientScriptFamily.WATCH
}

internal fun watchTypeLabel(type: ClientWatchKind): String {
    return when (type) {
        ClientWatchKind.HEALTH -> "Health"
        ClientWatchKind.PETTY_CASH -> "Petty Cash"
        ClientWatchKind.SCAN -> "Scan"
    }
}

internal fun formatWatchMetric(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format("%.1f", value)
    }
}
