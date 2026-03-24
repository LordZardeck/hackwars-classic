package game

import java.util.ArrayList
import java.util.HashMap
import java.util.Iterator
import java.util.Map

/**
 * FileSystem.java<br />
 * (c) Vulgate 2007
 *
 * The file system for the hacker game.
 */
open class FileSystem(private val MyComputer: Computer) {
    private val root = HashMap<Any?, Any?>()
    private val FlatDirectory = ArrayList<Any?>()
    private var HDType = 0
    private var quota = 0

    /**
     Set the type of HD installed.
     */
    fun setHDType(HDType: Int) {
        this.HDType = HDType
    }

    /**
     Check whether the current type is better than the current type.
     */
    fun checkType(type: Int): Boolean {
        if (HD_CHART[type] > HD_CHART[HDType]) {
            return true
        }
        return false
    }

    /**
     Get the type of HD installed.
     */
    fun getHDType(): Int {
        return HDType
    }

    /**
     Get the amount of files currently in the File System.
     */
    fun getQuantity(): Int {
        return quota
    }

    /**
     Return the maximum hard-drive space of this hard-drive.
     */
    fun getMaximumSpace(): Int {
        return HD_CHART[HDType] + MyComputer.equipmentSheet.driveBonus
    }

    /**
     Returns the amount of freespace without bonuses.
     */
    fun getSpaceMinusBonus(): Int {
        return HD_CHART[HDType] - quota + 2
    }

    /**
     Get the space left on the HD.
     */
    fun getSpaceLeft(): Int {
        return getMaximumSpace() - quota + 2
    }

    /**
     Check whether the directory provided exists.
     */
    fun updateFlatDirectory(directory: String) {
        var add = true
        val I = FlatDirectory.iterator()
        while (I.hasNext()) {
            val s = I.next() as String
            if (s.indexOf(directory) >= 0) {
                add = false
                break
            } else if (directory.indexOf(s) >= 0) {
                I.remove()
            }
        }
        if (add) {
            FlatDirectory.add(directory)
        }
    }

    /**
     Remove from flat.
     */
    fun removeFlatDirectory(directory: String) {
        var directoryVar = directory
        var fixedDirectory = ""
        var data = directoryVar.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (i in data.indices) {
            if (data[i] != "") {
                fixedDirectory += data[i] + "/"
            }
        }
        directoryVar = fixedDirectory

        val I = FlatDirectory.iterator()
        var addDirectory = ""
        while (I.hasNext()) {
            val s = I.next() as String
            if (s == directoryVar) {
                I.remove()
                data = s.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                for (i in 0..<data.size - 1) {
                    if (data[i] != "") {
                        addDirectory += data[i] + "/"
                    }
                }
            }
        }
        if (addDirectory != "") {
            updateFlatDirectory(addDirectory)
        }
    }

    /**
     Add a directory to the file system.
     */
    fun addDirectory(directory: String?): Boolean {
        var directoryVar = directory
        if (directoryVar == null || directoryVar === "") {
            return false
        }

        if (directoryVar[0] == '/' && directoryVar.length > 1) {
            directoryVar = directoryVar.substring(1, directoryVar.length)
        }

        for (i in FlatDirectory.indices) {
            if (FlatDirectory[i] as String == directoryVar) {
                return false
            }
        }

        if (!(quota - 2 < HD_CHART[HDType] + MyComputer.equipmentSheet.driveBonus)) {
            return false
        }
        quota++

        val locationPath = directoryVar.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var currentHashMap = root
        var directoryString = ""

        for (i in locationPath.indices) {
            if (locationPath[i] != "") {
                directoryString += locationPath[i]
                directoryString += "/"
            }

            if (locationPath[i] != "") {
                if (currentHashMap[locationPath[i]] == null) {
                    if (i != locationPath.size - 1) {
                        if (!(quota - 2 < HD_CHART[HDType] + MyComputer.equipmentSheet.driveBonus)) {
                            return false
                        }
                        quota++
                    }

                    val NewMap = HashMap<Any?, Any?>()
                    currentHashMap[locationPath[i]] = NewMap
                    currentHashMap = NewMap
                } else {
                    if (currentHashMap[locationPath[i]] is HashMap<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        val next = currentHashMap[locationPath[i]] as HashMap<Any?, Any?>
                        currentHashMap = next
                    }
                }
            }
        }

        updateFlatDirectory(directoryString)
        return true
    }

    /**
     Delete a directory from the file system.
     */
    fun deleteDirectory(directory: String?): Boolean {
        val directoryValue = directory!!
        if (!deleteDirectoryRecursive(directoryValue)) {
            return false
        }
        removeFlatDirectory(directoryValue)

        val locationPath = directoryValue.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var currentDirectory = root
        for (i in 0..<locationPath.size - 1) {
            if (locationPath[i] != "") {
                @Suppress("UNCHECKED_CAST")
                val next = currentDirectory[locationPath[i]] as HashMap<Any?, Any?>
                currentDirectory = next
            }
        }
        if (locationPath.isNotEmpty()) {
            currentDirectory.remove(locationPath[locationPath.size - 1])
        }

        quota--
        if (quota < 0) {
            quota = 0
        }
        return true
    }

    private fun deleteDirectoryRecursive(directory: String): Boolean {
        val locationPath = directory.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var currentDirectory = root
        for (i in 0..<locationPath.size - 1) {
            if (locationPath[i] != "") {
                @Suppress("UNCHECKED_CAST")
                val next = currentDirectory[locationPath[i]] as HashMap<Any?, Any?>
                currentDirectory = next
            }
        }
        if (locationPath.isNotEmpty()) {
            val O = currentDirectory[locationPath[locationPath.size - 1]]
            if (O is HashMap<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val directoryToDelete = O as HashMap<Any?, Any?>
                val it = directoryToDelete.entries.iterator()
                while (it.hasNext()) {
                    val mapEntry = it.next() as Map.Entry<Any?, Any?>
                    val o = mapEntry.key
                    if (mapEntry.value is HashMap<*, *>) {
                        deleteDirectoryRecursive(directory + o as String)
                        removeFlatDirectory(directory + o)
                    }

                    it.remove()
                    quota--
                    if (quota < 0) {
                        quota = 0
                    }
                }
            }
        }
        return true
    }

    /**
     Add a file to the file system.
     */
    fun addFile(HF: HackerFile, checkSpace: Boolean): Boolean {
        if (HF.getType() == HackerFile.BOUNTY || HF.getType() == HackerFile.PCI || HF.getType() == HackerFile.AGP) {
            val STimeOut = HF.getContent().get("timeout") as String?
            var timeOut = MyComputer.currentTime

            if (STimeOut == null || STimeOut == "") {
                HF.getContent()["timeout"] = "" + timeOut
            } else {
                timeOut = java.lang.Long.valueOf(STimeOut)
            }

            if (MyComputer.currentTime - timeOut > 86400000L && (HF.getType() == HackerFile.AGP || HF.getType() == HackerFile.PCI)) {
                if (MyComputer.getIP() == "900.800.7.006") {
                    return true
                }
            }

            if (MyComputer.currentTime - timeOut > 604800000L) {
                if (MyComputer.getIP() == "900.800.7.006") {
                    return true
                }
            }
        }

        val locationPath = HF.getLocation().split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var currentHashMap = root
        for (i in locationPath.indices) {
            if (locationPath[i] != "") {
                if (currentHashMap[locationPath[i]] != null) {
                    @Suppress("UNCHECKED_CAST")
                    val next = currentHashMap[locationPath[i]] as HashMap<Any?, Any?>
                    currentHashMap = next
                } else {
                    return false
                }
            }
        }
        var Exists = false
        if (currentHashMap[HF.getName()] != null) {
            Exists = true
        }

        if (!Exists && checkSpace) {
            if (!(quota - 2 < HD_CHART[HDType] + MyComputer.equipmentSheet.driveBonus)) {
                return false
            }
        }

        currentHashMap[HF.getName()] = HF
        if (!Exists) {
            quota++
        }

        return true
    }

    /**
     Get a file from the file system.
     */
    fun getFile(path: String?, name: String?): HackerFile? {
        val locationPath = path!!.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var currentHashMap = root
        for (i in locationPath.indices) {
            if (locationPath[i] != "") {
                @Suppress("UNCHECKED_CAST")
                val next = currentHashMap[locationPath[i]] as HashMap<Any?, Any?>?
                currentHashMap = next ?: return null
            }
        }

        return currentHashMap[name] as HackerFile?
    }

    /**
     Delete a file from the file system.
     */
    fun deleteFile(path: String?, name: String?): HackerFile? {
        val locationPath = path!!.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var currentHashMap = root
        for (i in locationPath.indices) {
            if (locationPath[i] != "") {
                @Suppress("UNCHECKED_CAST")
                val next = currentHashMap[locationPath[i]] as HashMap<Any?, Any?>?
                currentHashMap = next ?: return null
            }
        }

        if (currentHashMap[name] == null) {
            return null
        }

        quota--
        if (quota < 0) {
            quota = 0
        }
        return currentHashMap.remove(name) as HackerFile?
    }

    /**
     Return an array representing a single directory in the file system.
     */
    fun getDirectory(path: String): Array<Any?> {
        val locationPath = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var currentHashMap = root
        for (i in locationPath.indices) {
            if (locationPath[i] != "") {
                @Suppress("UNCHECKED_CAST")
                val next = currentHashMap[locationPath[i]] as HashMap<Any?, Any?>?
                currentHashMap = next ?: return emptyArray()
            }
        }

        val ReturnMe = arrayOfNulls<Any>(currentHashMap.size)
        val DirectoryIterator = currentHashMap.entries.iterator()
        var i = 0
        while (DirectoryIterator.hasNext()) {
            val ME = DirectoryIterator.next() as Map.Entry<Any?, Any?>
            val o: Any? = if (ME.value is HashMap<*, *>) {
                ME.key
            } else {
                val HF = ME.value as HackerFile
                val O = arrayOfNulls<Any>(9)
                O[0] = HF.getName()
                O[1] = Integer.valueOf(HF.getType())
                O[2] = Integer.valueOf(HF.getQuantity())
                O[3] = java.lang.Float.valueOf(HF.getPrice())
                O[4] = HF.getMaker()
                O[5] = HF.getCPUCost()
                O[6] = HF.getDescription()
                if (HF.getType() == HackerFile.NEW_FIREWALL) {
                    val content = HF.getContent()
                    var priceObject = content["store_price"]
                    if (priceObject == null) {
                        O[7] = 0.0f
                    } else {
                        if ("" + priceObject == "" || "" + priceObject == "null") {
                            priceObject = "0.0"
                        }
                        val price = java.lang.Float.valueOf("" + priceObject)
                        O[7] = price
                    }
                    O[8] = HF.getContent()
                } else if (Computer.makers.containsKey(HF.getMaker())) {
                    val price = Computer.makers[HF.getMaker()] as Float
                    O[7] = price
                    O[8] = null
                } else {
                    O[7] = 0.0f
                    O[8] = null
                }
                O
            }
            ReturnMe[i] = o
            i++
        }
        return ReturnMe
    }

    /**
     Return an array representing a single directory in the file system.
     */
    fun getWebDirectory(path: String?): Array<Any?>? {
        val locationPath = path!!.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var currentHashMap = root
        for (i in locationPath.indices) {
            if (locationPath[i] != "") {
                @Suppress("UNCHECKED_CAST")
                val next = currentHashMap[locationPath[i]] as HashMap<Any?, Any?>?
                currentHashMap = next ?: return null
            }
        }

        val ReturnMe = arrayOfNulls<Any>(currentHashMap.size)
        val DirectoryIterator = currentHashMap.entries.iterator()
        var i = 0
        while (DirectoryIterator.hasNext()) {
            val ME = DirectoryIterator.next() as Map.Entry<Any?, Any?>
            val o: Any? = if (ME.value is HashMap<*, *>) {
                ME.key
            } else {
                var HF = ME.value as HackerFile?
                if (HF != null) {
                    if (HF.getType() != HackerFile.BOUNTY && HF.getType() != HackerFile.AGP && HF.getType() != HackerFile.PCI && HF.getType() != HackerFile.HD && HF.getType() != HackerFile.MEMORY && HF.getType() != HackerFile.CPU && HF.getType() != HackerFile.NEW_FIREWALL) {
                        HF = HF.clone()
                        HF.setContent(null)
                    }
                }
                HF
            }
            ReturnMe[i] = o
            i++
        }
        return ReturnMe
    }

    /**
     Return an array representing a single directory in the file system.
     */
    fun getScanDirectory(path: String): Array<Any?> {
        val locationPath = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var currentHashMap = root
        for (i in locationPath.indices) {
            if (locationPath[i] != "") {
                @Suppress("UNCHECKED_CAST")
                val next = currentHashMap[locationPath[i]] as HashMap<Any?, Any?>?
                currentHashMap = next ?: return emptyArray()
            }
        }

        val ReturnMe = arrayOfNulls<Any>(currentHashMap.size)
        val DirectoryIterator = currentHashMap.entries.iterator()
        var i = 0
        while (DirectoryIterator.hasNext()) {
            val ME = DirectoryIterator.next() as Map.Entry<Any?, Any?>
            val o: Any? = if (ME.value is HashMap<*, *>) ME.key else ME.value as HackerFile
            ReturnMe[i] = o
            i++
        }
        return ReturnMe
    }

    /**
     Returns an array of equipment files.
     */
    fun getEquipment(path: String): Array<Any?> {
        val locationPath = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var currentHashMap = root
        for (i in locationPath.indices) {
            if (locationPath[i] != "") {
                @Suppress("UNCHECKED_CAST")
                val next = currentHashMap[locationPath[i]] as HashMap<Any?, Any?>?
                currentHashMap = next ?: return emptyArray()
            }
        }

        val ReturnMe = arrayOfNulls<Any>(currentHashMap.size)
        val DirectoryIterator = currentHashMap.entries.iterator()
        var i = 0
        while (DirectoryIterator.hasNext()) {
            val ME = DirectoryIterator.next() as Map.Entry<Any?, Any?>
            val o: Any? = if (ME.value is HashMap<*, *>) ME.key else ME.value as HackerFile
            if (o is HackerFile) {
                if (o.getType() == HackerFile.AGP || o.getType() == HackerFile.PCI) {
                    ReturnMe[i] = o
                } else {
                    ReturnMe[i] = null
                }
            }
            i++
        }
        return ReturnMe
    }

    /**
     Output the file system in XML format.
     */
    fun outputXML(): String {
        var returnMe = "<files>\n"
        val TempArrayList = ArrayList<Any?>()
        var WorkingHashMap: HashMap<Any?, Any?>
        TempArrayList.add(root)

        for (i in FlatDirectory.indices) {
            if (FlatDirectory[i] as String? != null) {
                returnMe += "<directory><![CDATA[" + (FlatDirectory[i] as String).replace("]]>", "]]&gt;") + "]]></directory>\n"
            } else {
                returnMe += "<directory><![CDATA[" + FlatDirectory[i] as String + "]]></directory>\n"
            }
        }

        do {
            WorkingHashMap = TempArrayList[0] as HashMap<Any?, Any?>
            TempArrayList.removeAt(0)

            val DirectoryIterator = WorkingHashMap.entries.iterator()
            while (DirectoryIterator.hasNext()) {
                val o = (DirectoryIterator.next() as Map.Entry<Any?, Any?>).value
                if (o is HashMap<*, *>) {
                    TempArrayList.add(o)
                } else {
                    returnMe += (o as HackerFile).outputXML()
                }
            }
        } while (TempArrayList.size > 0)

        returnMe += "</files>\n"
        return returnMe
    }

    /**
     Returns all the files in the root directory of a given type.
     */
    fun getFilesOfType(FileType: Int): ArrayList<Any?> {
        val returnMe = ArrayList<Any?>()
        val rootFiles = this.getScanDirectory("")
        for (i in rootFiles.indices) {
            if (rootFiles[i] is HackerFile) {
                val HF = rootFiles[i] as HackerFile
                if (HF.getType() == FileType) {
                    returnMe.add(HF)
                }
            }
        }
        return returnMe
    }

    /**
     Returns all the files in the root directory of a given type.
     */
    fun getFiles(): ArrayList<Any?> {
        val returnMe = ArrayList<Any?>()
        val rootFiles = this.getScanDirectory("")
        for (i in rootFiles.indices) {
            if (rootFiles[i] is HackerFile) {
                val HF = rootFiles[i] as HackerFile
                returnMe.add(HF)
            }
        }
        return returnMe
    }

    companion object {
        @JvmField
        val HD_CHART = intArrayOf(20, 40, 80, 100, 150, 30, 1000)
    }
}
