package com.hackwars.rewrite.client.shell

import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingConstants

class RewriteShellStatsRail : JPanel(GridBagLayout()) {
    private val playerIpLabel = JLabel("").apply {
        name = "rewrite-shell-stats-ip"
        foreground = Color.WHITE
        font = Font(Font.MONOSPACED, Font.BOLD, 20)
        horizontalAlignment = SwingConstants.CENTER
    }
    private val cpuProgressBar = JProgressBar(0, 100).apply {
        name = "rewrite-shell-stats-cpu-bar"
        isStringPainted = true
        foreground = Color(0x00, 0xAA, 0x66)
        background = Color(0x1A, 0x1A, 0x1A)
    }
    private val pettyCashValueLabel = JLabel().apply { name = "rewrite-shell-stats-petty-cash" }
    private val bankValueLabel = JLabel().apply { name = "rewrite-shell-stats-bank" }
    private val hackOMeterValueLabel = JLabel().apply { name = "rewrite-shell-stats-hackometer" }
    private val voteOMeterValueLabel = JLabel().apply { name = "rewrite-shell-stats-voteometer" }
    private val commodityValueLabels = mutableMapOf<String, JLabel>()
    private val skillValueLabels = mutableMapOf<String, JLabel>()

    init {
        name = "rewrite-shell-stats-rail"
        isOpaque = true
        background = Color(0x32, 0x32, 0x32)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0x14, 0x14, 0x14)),
            BorderFactory.createEmptyBorder(8, 10, 8, 10),
        )
        preferredSize = Dimension(308, 470)

        val constraints = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 4, 4, 4)
            weightx = 1.0
            gridx = 0
            gridy = 0
        }

        add(cpuPanel(), constraints)
        constraints.gridy++
        add(playerIpLabel, constraints)
        constraints.gridy++
        add(twoColumnPanel(
            iconValuePanel("Petty Cash", "pettycash.png", pettyCashValueLabel),
            iconValuePanel("Bank", "bank.png", bankValueLabel),
        ), constraints)
        constraints.gridy++
        add(twoColumnPanel(
            iconValuePanel("Hack-O-Meter", "hackometer.png", hackOMeterValueLabel),
            iconValuePanel("Vote-O-Meter", "voteometer.png", voteOMeterValueLabel),
        ), constraints)
        constraints.gridy++
        add(commoditiesPanel(), constraints)
        constraints.gridy++
        add(skillsPanel(), constraints)
        constraints.gridy++
        constraints.weighty = 1.0
        add(Box.createVerticalGlue(), constraints)
    }

    fun render(state: RewriteShellStatsRailState) {
        playerIpLabel.text = state.playerIp
        pettyCashValueLabel.text = state.pettyCashText
        bankValueLabel.text = state.bankMoneyText
        hackOMeterValueLabel.text = state.hackOMeterText
        voteOMeterValueLabel.text = state.voteOMeterText
        cpuProgressBar.value = state.cpuPercent.coerceAtMost(100)
        cpuProgressBar.string = state.cpuLoadText
        state.commodities.forEach { commodity ->
            commodityValueLabels[commodity.label]?.text = commodity.valueText
        }
        state.skills.forEach { skill ->
            skillValueLabels[skill.label]?.text = "Lv ${skill.level}"
        }
    }

    private fun cpuPanel(): JPanel {
        return JPanel().apply {
            name = "rewrite-shell-stats-cpu-panel"
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(iconLabel("cpuLoad.png", "CPU"))
            add(Box.createHorizontalStrut(8))
            add(cpuProgressBar)
        }
    }

    private fun commoditiesPanel(): JPanel {
        return JPanel(GridBagLayout()).apply {
            name = "rewrite-shell-stats-commodities"
            isOpaque = false
            val constraints = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(2, 0, 2, 0)
                weightx = 1.0
                gridx = 0
            }
            RewriteShellStatsRailState.defaultCommodityValues().forEachIndexed { index, commodity ->
                constraints.gridy = index
                add(
                    iconValuePanel(
                        commodity.label,
                        commodity.iconName,
                        JLabel("0").also {
                            it.name = "rewrite-shell-stats-commodity-${componentNameSuffix(commodity.label)}"
                            commodityValueLabels[commodity.label] = it
                        },
                    ),
                    constraints,
                )
            }
        }
    }

    private fun skillsPanel(): JPanel {
        return JPanel(GridBagLayout()).apply {
            name = "rewrite-shell-stats-skills"
            isOpaque = false
            val constraints = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(2, 0, 2, 0)
                weightx = 1.0
                gridx = 0
            }
            RewriteShellStatsRailState.defaultSkillValues().forEachIndexed { index, skill ->
                constraints.gridy = index
                add(
                    iconValuePanel(
                        skill.label,
                        skill.iconName,
                        JLabel("Lv 0").also {
                            it.name = "rewrite-shell-stats-skill-${componentNameSuffix(skill.label)}"
                            skillValueLabels[skill.label] = it
                        },
                    ),
                    constraints,
                )
            }
        }
    }

    private fun twoColumnPanel(left: JPanel, right: JPanel): JPanel {
        return JPanel(GridBagLayout()).apply {
            isOpaque = false
            val constraints = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
                gridy = 0
                insets = Insets(0, 0, 0, 4)
            }
            constraints.gridx = 0
            add(left, constraints)
            constraints.gridx = 1
            constraints.insets = Insets(0, 4, 0, 0)
            add(right, constraints)
        }
    }

    private fun iconValuePanel(label: String, iconName: String, valueLabel: JLabel): JPanel {
        valueLabel.foreground = Color.WHITE
        valueLabel.font = Font(Font.MONOSPACED, Font.BOLD, 12)
        return JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(iconLabel(iconName, label))
            add(Box.createHorizontalStrut(6))
            add(JLabel(label).apply {
                foreground = Color.WHITE
                font = Font(Font.DIALOG, Font.BOLD, 12)
            })
            add(Box.createHorizontalGlue())
            add(valueLabel)
        }
    }

    private fun iconLabel(iconName: String, description: String): JLabel {
        return JLabel().apply {
            foreground = Color.WHITE
            icon = icon(iconName)
            toolTipText = description
        }
    }

    private fun icon(iconName: String): Icon? = shellScaledImage(iconName, 16, 16)

    private fun componentNameSuffix(label: String): String {
        return label
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }
}

class RewriteShellCountdownLabel : JLabel("") {
    init {
        name = "rewrite-shell-countdown"
        foreground = Color.WHITE
        font = Font(Font.DIALOG, Font.BOLD, 12)
        isOpaque = false
    }

    fun render(state: RewriteShellCountdownState) {
        text = state.text
    }
}
