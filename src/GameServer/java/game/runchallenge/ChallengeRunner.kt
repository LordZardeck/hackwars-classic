package game.runchallenge

import com.hackwars.game.program.ToyProgram
import hackscript.model.RunFactory
import hackerlogin.ToyLinker
import java.util.ArrayList
import java.util.HashMap

/**
 * A singleton to run a challenge.
 */
class ChallengeRunner {
    companion object {
        private var instance: ChallengeRunner? = null

        @JvmField
        val Challenge = arrayOf(
            "strlen",
            "sqrt",
            "abs",
            "ln",
            "atan",
            "acos",
            "asin",
            "tan",
            "cos",
            "sin",
            "getE",
            "getPI",
            "substr",
            "getInputString",
            "getInputStringCount",
            "setOutputString",
            "getInputFloat",
            "getInputFloatCount",
            "setOutputFloat",
            "getInputInt",
            "getInputIntCount",
            "setOutputInt",
            "equal",
            "printf",
            "rand",
            "intValue",
            "floatValue",
            "indexOf",
            "parseFloat",
            "parseInt",
            "replaceAll",
            "split",
            "length"
        )

        private const val NULL_APPLICATION_DATA_ERROR =
            "Parameter specified as non-null is null: method com.hackwars.game.program.ToyProgram.execute, parameter applicationData"

        /**
         * Get an instance of this singleton.
         */
        @JvmStatic
        fun getInstance(): ChallengeRunner {
            if (instance == null) {
                instance = ChallengeRunner()
            }
            return instance as ChallengeRunner
        }
    }

    /**
     * Runs a challenge script application.
     */
    @Synchronized
    fun runToyProblem(
        Source: String?,
        Iterations: Int?,
        OInputFloat: Array<*>?,
        OInputString: Array<*>?,
        OInputInteger: Array<*>?,
        OTargetFloat: Array<*>?,
        OTargetString: Array<*>?,
        OTargetInteger: Array<*>?
    ): HashMap<Any?, Any?> {
        val returnMe = HashMap<Any?, Any?>()
        val error = ""

        var InputFloat: Array<Double?>? = null
        try {
            InputFloat = arrayOfNulls(OInputFloat!!.size)
            for (i in OInputFloat.indices) {
                InputFloat[i] = OInputFloat[i] as Double
            }
        } catch (_: Exception) {
        }

        var InputString: Array<String?>? = null
        try {
            InputString = arrayOfNulls(OInputString!!.size)
            for (i in OInputString.indices) {
                InputString[i] = OInputString[i] as String
            }
        } catch (_: Exception) {
        }

        var InputInteger: Array<Integer?>? = null
        try {
            InputInteger = arrayOfNulls(OInputInteger!!.size)
            for (i in OInputInteger.indices) {
                InputInteger[i] = OInputInteger[i] as Integer
            }
        } catch (_: Exception) {
        }

        var TargetFloat: Array<Double?>? = null
        try {
            TargetFloat = arrayOfNulls(OTargetFloat!!.size)
            for (i in OTargetFloat.indices) {
                TargetFloat[i] = OTargetFloat[i] as Double
            }
        } catch (_: Exception) {
        }

        var TargetString: Array<String?>? = null
        try {
            TargetString = arrayOfNulls(OTargetString!!.size)
            for (i in OTargetString.indices) {
                TargetString[i] = OTargetString[i] as String
            }
        } catch (_: Exception) {
        }

        var TargetInteger: Array<Integer?>? = null
        try {
            TargetInteger = arrayOfNulls(OTargetInteger!!.size)
            for (i in OTargetInteger.indices) {
                TargetInteger[i] = OTargetInteger[i] as Integer
            }
        } catch (_: Exception) {
        }

        val TP = ToyProgram(Source, 64, Iterations!!.toInt())
        val HL = ToyLinker(TP, null)

        //Add the inputs.
        if (InputFloat != null) {
            for (i in InputFloat.indices) {
                TP.addInFloat((InputFloat[i] as Double).toFloat())
            }
        }
        if (InputString != null) {
            for (i in InputString.indices) {
                TP.addInString(InputString[i])
            }
        }
        if (InputInteger != null) {
            for (i in InputInteger.indices) {
                TP.addInInt((InputInteger[i] as Integer).toInt())
            }
        }

        //Add the targets.
        if (TargetFloat != null) {
            for (i in TargetFloat.indices) {
                TP.addTargetFloat((TargetFloat[i] as Double).toFloat())
            }
        }
        if (TargetString != null) {
            for (i in TargetString.indices) {
                TP.addTargetString(TargetString[i])
            }
        }
        if (TargetInteger != null) {
            for (i in TargetInteger.indices) {
                TP.addTargetInt((TargetInteger[i] as Integer).toInt())
            }
        }

        var found = false
        try {
            val Functions = RunFactory.getCodeList(Source) as ArrayList<*>
            for (i in 0 until Functions.size) {
                val name = Functions[i] as String
                found = false
                for (ii in Challenge.indices) {
                    if (Challenge[ii] == name) {
                        found = true
                    }
                }
                if (found == false) {
                    TP.setError("Function $name not found.")
                    break
                }
            }
        } catch (e: Exception) {
            TP.setError("Syntax error in challenge code compiler returned [" + e.message + "]")
        }
        if (found) {
            try {
                throw NullPointerException(NULL_APPLICATION_DATA_ERROR)
            } catch (e: Exception) {
                println("this happened?")
                TP.setError("Syntax error in challenge code compiler returned [" + e.message + "]")
            }
        }

        returnMe["outint"] = TP.getOutInt()
        returnMe["outstring"] = TP.getOutString()
        returnMe["outdouble"] = TP.outDouble
        returnMe["success"] = TP.getSuccess()
        returnMe["error"] = TP.getError()

        return returnMe
    }
}
