package game

import com.hackwars.rpc.SaveFile
import game.payload.ChallengeResultsPayload
import game.payload.RequestNetworkHopPayload
import java.util.ArrayList
import java.util.HashMap

/**
 * By Alexander Morrison
 */
open class MakeClue(private val MyFileSystem: FileSystem, private val MyComputer: Computer, clueLevel: Int) {
    //Used to keep track of the differnt possible clue tasks.
    private val DataClue = ArrayList<Any?>()
    private val AttackClue = ArrayList<Any?>()
    private val ChallengeClue = ArrayList<Any?>()
    private val ScanClue = ArrayList<Any?>()

    init {
        AttackClue.add("" + KILL + "+Attack this source of human knowledge.+192.168.2.300")
        AttackClue.add("" + KILL + "+They can put a man on the moon, but they can't protect their website from attack.+192.168.2.747")

        ScanClue.add("" + SCAN + "+They have a shiny cubic building, and nearly half the world's computing power -- scan them.+171.016.5.036")
        ScanClue.add("" + SCAN + "+Scan the team, if you know what I mean.+192.168.2.400")
        ScanClue.add("" + SCAN + "+What they lack in offensive power, they make up for in cold weather -- scan them.+192.168.3.666")
        ScanClue.add("" + SCAN + "+Scan the first of the great detective's.+478.254.8.001")
        ScanClue.add("" + SCAN + "+Scan this impressive source of Star Wars information.+192.168.2.300")
        ScanClue.add("" + SCAN + "+Scan this source of scientific doodads.+192.168.2.747")

        ChallengeClue.add("" + CHALLENGE + "+Do the 'Draw Triangle' challenge.+7")
        ChallengeClue.add("" + CHALLENGE + "+Do the 'Reverse Sentence' challenge.+8")
        ChallengeClue.add("" + CHALLENGE + "+Do the 'Combine Strings' challenge.+9")
        ChallengeClue.add("" + CHALLENGE + "+Do the 'Prime Number' challenge.+10")
        ChallengeClue.add("" + CHALLENGE + "+Do the 'Search' challenge.+11")
        ChallengeClue.add("" + CHALLENGE + "+Do the 'Volume of a Cube' challenge.+12")

        DataClue.add("" + READ + "+Find where dorothy met the Scarecrow.+222.345.7.003")
        DataClue.add("" + READ + "+Find the conclusion to this victorian mystery.+478.254.8.055")
        DataClue.add("" + READ + "+Find the ghost of Marley (not Bob).+569.234.1.002")
        DataClue.add("" + READ + "+Where the maid visits Patty.+765.432.1.022")
        DataClue.add("" + READ + "+Find the deadly poppy field.+222.345.7.008")
        DataClue.add("" + READ + "+Find where dorothy met the Tin Woodman.+222.345.7.005")

        if (clueLevel == 1) {
            DataClue.add("" + READ + "+Read a natural conclusion.+221.445.3.070")
            AttackClue.add("" + KILL + "+Kill the origin of an origin.+221.445.3.001")
            AttackClue.add("" + KILL + "+Reduce the first health of nations.+324.564.5.001")
            DataClue.add("" + READ + "+Find the start of this slice of Americana.+453.211.5.001")
            DataClue.add("" + READ + "+Find something more beautiful than an example of geometrical symmetry.+458.663.4.014")
            ChallengeClue.add("" + CHALLENGE + "+Do the equation of a line challenge.+15")
        }

        if (clueLevel == 2) {
            AttackClue.add("" + KILL + "+Beat the beginning of the book by the bard.+577.341.8.001")
            DataClue.add("" + READ + "+Read this section regarding heat transfer.+652.876.9.009")
            DataClue.add("" + READ + "+Where Aufidius loses his rage.+577.341.8.026")
            ChallengeClue.add("" + CHALLENGE + "+Do the projectile challenge.+16")
        }
    }

    fun generateClue(): String {
        val drop = Math.random().toFloat()
        if (drop < 0.25f) {
            return getData()
        } else if (drop < 0.50f) {
            return getAttack()
        } else if (drop < 0.75f) {
            return getChallenge()
        } else if (drop < 1.0f) {
            return getScan()
        }
        return ""
    }

    fun getData(): String {
        val drop = Math.random().toFloat()
        val classSize = 1.0f / DataClue.size
        val idrop = (drop / classSize).toInt()
        return DataClue[idrop] as String
    }

    fun getAttack(): String {
        val drop = Math.random().toFloat()
        val classSize = 1.0f / AttackClue.size
        val idrop = (drop / classSize).toInt()
        return AttackClue[idrop] as String
    }

    fun getChallenge(): String {
        val drop = Math.random().toFloat()
        val classSize = 1.0f / ChallengeClue.size
        val idrop = (drop / classSize).toInt()
        return ChallengeClue[idrop] as String
    }

    fun getScan(): String {
        val drop = Math.random().toFloat()
        val classSize = 1.0f / ScanClue.size
        val idrop = (drop / classSize).toInt()
        return ScanClue[idrop] as String
    }

    /**
     * Check to see whether a condition has been met for a given clue,
     */
    fun checkClue(MyComputer: Computer, Result: String, ClueType: Int) {
        val RootFiles = MyFileSystem.getScanDirectory("")
        for (i in RootFiles.indices) {
            if (RootFiles[i] is HackerFile) {
                val HF = RootFiles[i] as HackerFile
                checkFile(HF, MyComputer, Result, ClueType)
            }
        }
    }

    /**
     * Check a specific file for the given result.
     */
    fun checkFile(HF: HackerFile, MyComputer: Computer, Result: String, ClueType: Int) {
        try {
            if (HF.type == HackerFile.CLUE) {
                val Content = HF.content as? MutableMap<Any?, Any?> ?: return
                var currentStep = Integer.valueOf(Content["currentstep"] as String)
                var data = Content["step$currentStep"] as String
                var ClueData = data.split("\\+".toRegex()).toTypedArray()
                var DataType = Integer.valueOf(ClueData[0])
                val Output = ClueData[2]
                HF.setDescription(ClueData[1])

                if (DataType == ClueType && Output == Result) {
                    MyComputer.addMessage(MessageHandler.SECRET_DOCUMENT_TASK_COMPLETED)
                    currentStep += 1
                    Content["currentstep"] = "" + currentStep
                    data = Content["step$currentStep"] as String
                    ClueData = data.split("\\+".toRegex()).toTypedArray()
                    HF.setDescription(ClueData[1])
                    DataType = Integer.valueOf(ClueData[0])
                    if (DataType == FINISH) {
                        if (HF.name.orEmpty().indexOf("Gateway Document") == -1) {
                            val MyDropTable = DropTable(0, MyComputer)
                            val clueLevel = Integer.valueOf(Content["cluelevel"] as String).toInt()

                            MyComputer.fileSystem.deleteFile("", HF.name)
                            MyComputer.addMessage("Congratulations! You have completed all the tasks assigned in a secret document.")
                            MyComputer.addMessage("")
                            MyComputer.addMessage("Rewards:")
                            MyComputer.addMessage("${XP_TABLE[clueLevel]} XP in all skills.")
                            MyComputer.addMessage("$" + MONEY_TABLE[clueLevel] + " rewarded.")
                            MyComputer.computerHandler.addData(
                                ApplicationData(
                                    ChallengeResultsPayload(MONEY_TABLE[clueLevel], XP_TABLE[clueLevel]),
                                    0,
                                    MyComputer.getIP()
                                ),
                                MyComputer.getIP()
                            )
                            var H1: HackerFile? = null
                            var H2: HackerFile? = null
                            if (clueLevel == 0) {
                                H1 = MyDropTable.generateDrop()
                                H2 = MyDropTable.generateDrop()
                            } else if (clueLevel == 1) {
                                H1 = MyDropTable.generateDrop()
                                H2 = MyDropTable.generateDrop()
                            } else {
                                H1 = MyDropTable.generateDrop()
                                H2 = MyDropTable.generateDrop()
                            }
                            MyComputer.addMessage("Rewarded File " + H1!!.name)
                            MyComputer.addMessage("Rewarded File " + H2!!.name)

                            MyComputer.computerHandler.addData(
                                ApplicationData(SaveFile(MyComputer.getIP(), "", H1!!), 0, MyComputer.getIP()),
                                MyComputer.getIP()
                            )
                            MyComputer.computerHandler.addData(
                                ApplicationData(SaveFile(MyComputer.getIP(), "", H2!!), 0, MyComputer.getIP()),
                                MyComputer.getIP()
                            )
                        } else {
                            MyComputer.fileSystem.deleteFile("", HF.name)
                            MyComputer.computerHandler.addData(
                                ApplicationData(
                                    RequestNetworkHopPayload(MyComputer.getIP()),
                                    0,
                                    MyComputer.getIP()
                                ),
                                HF.maker
                            )
                        }
                    }
                    MyComputer.sendPacket()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Return the data parsed from a packet as a Hacker File.
     */
    fun generateClue(clueLevel: Int): HackerFile {
        val HF = HackerFile(HackerFile.CLUE)

        if (!MyComputer.isGateway()) {
            HF.name = DROP_NAME[clueLevel]
        } else {
            HF.name = "Gateway Document"
        }

        HF.quantity = 1
        if (!MyComputer.isGateway()) {
            HF.maker = "Johnny Heart"
        } else {
            HF.maker = MyComputer.getIP()
        }

        val Data = HashMap<Any?, Any?>()
        Data["currentstep"] = "0"
        Data["cluelevel"] = "" + clueLevel
        for (i in 0..5) {
            Data["step$i"] = ""
        }
        var ii = 0
        while (ii < DROP_COUNT[clueLevel]) {
            Data["step$ii"] = generateClue()
            ii++
        }
        Data["step$ii"] = "" + FINISH + "+N/A+N/A"
        val data = Data["step0"] as String
        val ClueData = data.split("\\+".toRegex()).toTypedArray()
        HF.setDescription(ClueData[1])

        HF.content = Data
        return HF
    }

    companion object {
        //Events that can trigger a clue.
        const val SCAN = 0
        const val KILL = 1
        const val READ = 2
        const val CHALLENGE = 3
        const val FINISH = 4

        @JvmField
        val DROP_COUNT = intArrayOf(3, 4, 5)

        @JvmField
        val DROP_NAME = arrayOf("Secret Document (Medium)", "Secret Document (High)", "Secret Document (Rare)")

        @JvmField
        val XP_TABLE = floatArrayOf(500.0f, 2000.0f, 10000.0f)

        @JvmField
        val MONEY_TABLE = floatArrayOf(5000.0f, 10000.0f, 25000.0f)
    }
}
