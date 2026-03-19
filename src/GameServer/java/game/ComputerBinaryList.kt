package game

import com.plink.dolphinnet.util.BinaryList

/**
 * UserBinaryList.java<br />
 * (c) Vulgate 2007<br />
 *
 * This implementation of a binary list holds users currently logged into the game.
 */
open class ComputerBinaryList : BinaryList() {
    /** Get the key used in comparison.*/
    override fun getKey(o: Any?): Any? {
        val temp = o as Computer
        return temp.getIP()
    }

    /** Compare one object to another.*/
    override fun compare(o1: Any?, o2: Any?): Int {
        val s1 = o1 as String
        val s2 = o2 as String
        return s1.compareTo(s2)
    }

    /**
     * Return a string representation of the data.
     */
    override fun toString(): String {
        val returnMe = ""
        return returnMe
    }
}
