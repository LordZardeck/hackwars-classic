package com.hackwars.rewrite.client.systems

import com.hackwars.rewrite.client.mvc.RewriteViewModel
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientInstalledApplication
import com.hackwars.rewrite.protocol.ClientInstalledFirewall
import com.hackwars.rewrite.protocol.ClientPortState
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind

private const val MAX_PORT_HEALS: Int = 10
private const val INSTALL_PROGRAM_PROMPT: String = "Click Here To Install Script"
private const val INSTALL_FIREWALL_PROMPT: String = "Click Here To Install Firewall"

internal data class RewritePortManagementRow(
    val number: Int,
    val programLabel: String,
    val firewallLabel: String,
    val currentCpuCost: Double,
    val maxCpuCost: Double,
    val health: Double,
    val healsLeft: Int,
    val defaultPort: Boolean,
    val dummy: Boolean,
    val enabled: Boolean,
    val note: String,
    val installedApplication: ClientInstalledApplication? = null,
    val installedFirewall: ClientInstalledFirewall? = null,
) {
    val cpuDisplay: String = "${formatPortMetric(currentCpuCost)}/${formatPortMetric(maxCpuCost)}"
    val healthDisplay: String = formatPortMetric(health)
}

internal data class RewritePortManagementViewModel(
    val rows: List<RewritePortManagementRow>,
    val selectedPortLabel: String,
    val selectedEnabled: Boolean,
    val selectedDefault: Boolean,
    val selectedDummy: Boolean,
    val selectedNote: String,
    val statusText: String,
    val errorText: String,
    val tableEnabled: Boolean,
    val healButtonEnabled: Boolean,
    val installProgramButtonEnabled: Boolean,
    val installFirewallButtonEnabled: Boolean,
) : RewriteViewModel

internal fun buildPortManagementRows(snapshot: ClientGameSnapshot): List<RewritePortManagementRow> {
    return snapshot.ports
        .sortedBy(ClientPortState::number)
        .map { port ->
            val program = port.installedApplication
            val firewall = port.installedFirewall
            RewritePortManagementRow(
                number = port.number,
                programLabel = program?.name ?: INSTALL_PROGRAM_PROMPT,
                firewallLabel = firewall?.name ?: INSTALL_FIREWALL_PROMPT,
                currentCpuCost = (program?.cpuCost ?: 0.0) + (firewall?.cpuCost ?: 0.0),
                maxCpuCost = port.maxCpuCost,
                health = port.health,
                healsLeft = (MAX_PORT_HEALS - port.healCount).coerceAtLeast(0),
                defaultPort = port.defaultPort,
                dummy = port.dummy,
                enabled = port.enabled,
                note = port.note,
                installedApplication = program,
                installedFirewall = firewall,
            )
        }
}

internal fun buildPortManagementViewModel(
    rows: List<RewritePortManagementRow>,
    selectedPortNumber: Int?,
    requestInFlight: Boolean,
    statusText: String,
    errorText: String,
): RewritePortManagementViewModel {
    val selected = rows.firstOrNull { it.number == selectedPortNumber }
    return RewritePortManagementViewModel(
        rows = rows,
        selectedPortLabel = selected?.let { "Selected Port: ${it.number}" } ?: "No port selected",
        selectedEnabled = selected?.enabled == true,
        selectedDefault = selected?.defaultPort == true,
        selectedDummy = selected?.dummy == true,
        selectedNote = selected?.note.orEmpty(),
        statusText = statusText,
        errorText = errorText,
        tableEnabled = !requestInFlight,
        healButtonEnabled = !requestInFlight && selected != null && selected.enabled && !selected.dummy,
        installProgramButtonEnabled = !requestInFlight && selected != null,
        installFirewallButtonEnabled = !requestInFlight && selected != null,
    )
}

internal fun allowPortManagementApplicationFile(file: ClientStoredFile): Boolean {
    return file.kind == ClientStoredFileKind.APPLICATION_BINARY && file.compiledBinary?.applicationKind != null
}

internal fun allowPortManagementFirewallFile(file: ClientStoredFile): Boolean {
    return file.kind == ClientStoredFileKind.FIREWALL_BINARY && file.compiledBinary != null
}

private fun formatPortMetric(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format("%.1f", value)
    }
}
