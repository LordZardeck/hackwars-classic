package game

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
                    if (HF.getType() == HackerFile.BOUNTY) {
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
        var Content: HashMap<Any?, Any?>? = null
        if (HF != null) {
            Content = HF.getContent()
        }

        if (Content != null) {
            var count = Integer.valueOf(Content["count"] as String)
            val CheckBountyType = Integer.valueOf(Content["type"] as String)
            val reward = java.lang.Float.valueOf(Content["reward"] as String)
            val checkTarget = Content["target"] as String
            val scriptName = Content["script"] as String
            val maker = Content["maker"] as String
            val bountyip = Content["bountyip"] as String
            var success = true

            try {
                if (HF!!.getType() == HackerFile.BOUNTY) {
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
                            if (scriptName != InstallFile.getName() || maker != InstallFile.getMaker()) {
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
                            MyComputer!!.computerHandler
                                .addData(ApplicationData("bountyhttp", MyComputer.getIP(), 0, MyComputer.getIP()), target)
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
                    Content["count"] = "" + count
                    if (count <= 0) {
                        val O = arrayOf<Any?>("Store/", HF.getName())
                        MyFileSystem.deleteFile(HF.getLocation(), HF.getName())
                        MyComputer.computerHandler
                            .addData(ApplicationData("checkbounty", HF.getName(), 0, MyComputer.getIP()), MyComputer.storeIP)
                        MyComputer.computerHandler
                            .addData(ApplicationData("deletefile", O, 0, MyComputer.getIP()), MyComputer.storeIP)
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
