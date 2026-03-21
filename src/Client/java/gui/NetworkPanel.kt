package gui

import assignments.PacketNetwork
import java.awt.*
import java.awt.event.AdjustmentEvent
import java.awt.event.AdjustmentListener
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane

class NetworkPanel(private val hacker: Hacker?) : JPanel(), AdjustmentListener {
    private var name = "Unable to connect to UGoPNet"

    var questNpcCount = 0
        private set
    var shippingNpcCount = 0
        private set
    var regularNpcCount = 0
        private set
    var storeNpcCount = 0
        private set

    private var back = runCatching { ImageLoader.getImage("images/UGOPNet.png") }.getOrNull()

    private var networkInfoPanel = NetworkInfoPanel(this)
    private var innerScroll = JPanel().apply {
        layout = GridBagLayout()
        background = MapPanel.NETWORK_INFO_BACKGROUND
    }

    init {
        layout = GridBagLayout()
        populate()
    }

    private fun populate() {
        val c = GridBagConstraints().apply {
            fill = GridBagConstraints.BOTH
            weightx = 1.0
            weighty = 1.0
            anchor = GridBagConstraints.FIRST_LINE_START
        }
        add(networkInfoPanel, c)

        val cp = GridBagConstraints().apply {
            anchor = GridBagConstraints.NORTHWEST
            weightx = 0.2
            weighty = 0.0
            insets = Insets(0, 5, 0, 0)
            fill = GridBagConstraints.NONE
            gridy = 0
        }

        innerScroll.add(JLabel("NPC List").apply {
            background = MapPanel.HEADER_COLOR
            foreground = MapPanel.NETWORK_NAME_COLOR
            font = MapPanel.NETWORK_NPC_FONT
        }, cp)

        cp.weighty = 0.0
        cp.gridheight = 1

        innerScroll.add(JLabel(""), cp.apply { gridy = 2; weighty = 1.0 })
        innerScroll.add(JLabel(""), cp.apply { gridx = 1; weightx = 1.0 })

        add(
            JScrollPane(innerScroll).apply {
                viewport.isOpaque = false
                isOpaque = false
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
                verticalScrollBar.addAdjustmentListener(this@NetworkPanel)
            },
            c.apply {
                gridx = 1
                gridy = 0
                gridheight = 2
                weightx = 1.0
                weighty = 1.0
                fill = GridBagConstraints.BOTH
            }
        )
    }

    override fun getName(): String {
        return name
    }

    fun setNetwork(network: PacketNetwork) {
        name = network.name

        val cp = GridBagConstraints().apply {
            anchor = GridBagConstraints.FIRST_LINE_START
            weightx = 0.0
            weighty = 0.0
            gridheight = 1
            gridy = 1
            insets = Insets(0, 5, 0, 0)
            fill = GridBagConstraints.NONE
        }

        val regular = (network.regularNPCs as? ArrayList<HashMap<*, *>>) ?: ArrayList()
        val quest = (network.questNPCs as? ArrayList<HashMap<*, *>>) ?: ArrayList()
        val shipping = (network.miningNPCs as? ArrayList<HashMap<*, *>>) ?: ArrayList()
        val store = (network.storeNPCs as? ArrayList<HashMap<*, *>>) ?: ArrayList()
        questNpcCount = quest.size
        shippingNpcCount = shipping.size
        regularNpcCount = regular.size
        storeNpcCount = store.size

        addNpcLabel(store, NPCLabel.STORE, cp)
        addNpcLabel(quest, NPCLabel.QUEST, cp)
        addNpcLabel(shipping, NPCLabel.SHIPPING, cp)
        addNpcLabel(regular, NPCLabel.REGULAR, cp)

        // Remove any left over labels
        for (i in innerScroll.components.size - 1 downTo 1 + questNpcCount + shippingNpcCount + regularNpcCount + storeNpcCount) {
            innerScroll.remove(i)
        }

        innerScroll.add(JLabel(""), cp.apply {
            gridy += 1
            weighty = 1.0
        })
        innerScroll.revalidate()
        innerScroll.repaint()

        back = ImageLoader.getImage("images/UGOPNet.png")
        networkInfoPanel.setNetwork(name)
    }

    private fun addNpcLabel(npcList: ArrayList<HashMap<*, *>>, type: Int, constraints: GridBagConstraints) {
        npcList.forEach { npc ->
            val currentY = constraints.gridy
            innerScroll.takeIf { it.components.size < currentY }?.remove(currentY)
            innerScroll.add(
                NPCLabel(
                    npc["name"] as? String,
                    npc["ip"] as? String,
                    "",
                    npc["title"] as? String,
                    type,
                    hacker,
                    this
                ),
                constraints.apply { gridy += 1 },
                currentY
            )
        }
    }


    public override fun paintComponent(g: Graphics) {
        g.color = Color(0, 0, 0)
        g.fillRect(0, 0, getWidth(), getHeight())
        g.drawImage(back, 0, 0, null)
    }

    override fun adjustmentValueChanged(e: AdjustmentEvent?) {
        repaint()
    }
}
