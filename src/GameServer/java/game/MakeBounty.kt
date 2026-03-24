package game

import com.hackwars.rpc.DeleteFile
import game.payload.BountyHttpPayload
import game.payload.CheckBountyPayload
import java.util.HashMap

/**
 * By Alexander Morrison
 */
open class MakeBounty(private val MyFileSystem: FileSystem) {
    /**
     * Check to see whether a condition has been met for a given clue,
     */
    fun checkBounty(
        MyComputer: Computer?,
        InstallFile: HackerFile?,
        BountyType: Int,
        target: String?,
        npc: Boolean,
        setIP: String
    ) {
        if (!npc) {
            val RootFiles = MyFileSystem.getWebDirectory("")
            for (i in RootFiles!!.indices) {
                if (RootFiles[i] is HackerFile) {
                    val HF = RootFiles[i] as HackerFile
                    if (HF.type == HackerFile.BOUNTY) {
                        if (checkFile(HF, MyComputer, InstallFile, BountyType, target, setIP)) {
                            return
                        }
                    }
                }
            }
        }
    }

    /**
     * Check a specific file for the given result.
     */
    fun checkFile(
        HF: HackerFile?,
        MyComputer: Computer?,
        InstallFile: HackerFile?,
        BountyType: Int,
        target: String?,
        setIP: String
    ): Boolean {
        val content = HF?.content as? BountyContent ?: return false

        if (content != null) {
            var count = content.count.toInt()
            val CheckBountyType = content.targetType.toInt()
            val reward = java.lang.Float.valueOf(content.reward)
            val checkTarget = content.target
            val scriptName = content.script
            val maker = content.maker
            val bountyip = content.bountyIp
            var success = true

            try {
                if (HF!!.type == HackerFile.BOUNTY) {
                    if (BountyType == SCAN && BountyType == CheckBountyType) {
                        if (checkTarget != target && checkTarget != "*") {
                            success = false
                        }
                    } else if (BountyType == KILL && BountyType == CheckBountyType) {
                        if (checkTarget != target && checkTarget != "*") {
                            success = false
                        }
                    } else if (BountyType == INSTALL && BountyType == CheckBountyType) {
                        if (checkTarget != target && checkTarget != "*") {
                            success = false
                        }
                        if (InstallFile != null) {
                            if (scriptName != InstallFile.name || maker != InstallFile.maker) {
                                success = false
                            }
                        }
                    } else if (BountyType == VOTE && BountyType == CheckBountyType) {
                        if (checkTarget != target && checkTarget != "*") {
                            success = false
                        }
                    } else if (BountyType == CHANGE && BountyType == CheckBountyType && bountyip == setIP) {
                        if (checkTarget != target && checkTarget != "*") {
                            success = false
                        } else {
                            MyComputer!!.computerHandler.addData(
                                ApplicationData(BountyHttpPayload(MyComputer.getIP()), 0, MyComputer.getIP()),
                                target
                            )
                        }
                    } else if (BountyType == DESTROY_WATCH && BountyType == CheckBountyType) {
                        if (checkTarget != target && checkTarget != "*") {
                            success = false
                        }
                    } else {
                        success = false
                    }
                }

                if (success) {
                    MyComputer!!.addMessage(MessageHandler.BOUNTY_STEP_COMPLETED)

                    count -= 1
                    HF.content = content.copy(count = count.toString())
                    if (count <= 0) {
                        MyFileSystem.deleteFile(HF.location, HF.name)
                        MyComputer.computerHandler
                            .addData(
                                ApplicationData(CheckBountyPayload(HF.name.orEmpty()), 0, MyComputer.getIP()),
                                MyComputer.storeIP
                            )
                        MyComputer.computerHandler
                            .addData(
                                ApplicationData(
                                    DeleteFile(MyComputer.storeIP ?: return true, "Store/", HF.name.orEmpty()),
                                    0,
                                    MyComputer.getIP()
                                ),
                                MyComputer.storeIP
                            )
                    }

                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return false
    }

    companion object {
        //Events that can trigger a clue.
        const val SCAN = 0
        const val KILL = 1
        const val INSTALL = 2
        const val VOTE = 3
        const val CHANGE = 4
        const val DESTROY_WATCH = 5

        private val TypeNames = arrayOf("Scan", "Kill", "Install", "Vote", "Change HTTP", "Destroy Watch")

        /**
         * Get the name associated with the bounty type.
         */
        @JvmStatic
        fun getTypeName(type: Int): String {
            return TypeNames[type]
        }
    }
}
