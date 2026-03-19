/*
 * structInfo.java
 *
 * Created on March 10, 2007, 10:40 AM
 *
 * By Alexander T Morrsion
 *
 */

package hackerlogin

import com.hackwars.game.program.Program
import com.hackwars.game.program.ToyProgram
import game.ComputerHandler
import hackscript.model.Linker
import hackscript.model.TypeArray
import hackscript.model.TypeBoolean
import hackscript.model.TypeFloat
import hackscript.model.TypeInteger
import hackscript.model.TypeString
import hackscript.model.Variable
import java.util.ArrayList

/**
 * By Alexander Morrison
 */
class ToyLinker(MyProgram: Program?, MyComputerHandler: ComputerHandler?) : Linker() {
    @JvmField
    var codeCounter = 0
    private var MyComputerHandler: ComputerHandler? = MyComputerHandler
    private var MyProgram: Program? = MyProgram
    private var theLinker: Any? = null

    init {
        this.theLinker = theLinker
        codeCounter = 0
    }

    /**
     * Escape regex.
     */
    fun regexEscape(data: String): String {
        var returnMe = data
        returnMe = returnMe.replace("\\\\".toRegex(), "\\\\\\\\")
        returnMe = returnMe.replace("\\.".toRegex(), "\\\\.")
        returnMe = returnMe.replace("\\$".toRegex(), "\\\\\\$")
        returnMe = returnMe.replace("\\^".toRegex(), "\\\\^")
        returnMe = returnMe.replace("\\{".toRegex(), "\\\\{")
        returnMe = returnMe.replace("\\[".toRegex(), "\\\\[")
        returnMe = returnMe.replace("\\(".toRegex(), "\\\\(")
        returnMe = returnMe.replace("\\|".toRegex(), "\\\\|")
        returnMe = returnMe.replace("\\)".toRegex(), "\\\\)")
        returnMe = returnMe.replace("\\*".toRegex(), "\\\\*")
        returnMe = returnMe.replace("\\+".toRegex(), "\\\\+")
        returnMe = returnMe.replace("\\?".toRegex(), "\\\\?")
        return returnMe
    }

    @Suppress("UNCHECKED_CAST")
    override fun runFunction(name: String?, parameters: ArrayList<*>?): Variable? {
        var result: Any? = null
        val TP = MyProgram as ToyProgram?
        val functionName = name!!
        val functionParameters = parameters!!

        if (TP!!.getError()!!.length == 0) {
            try {
                if (functionName == "equal") {
                    if (functionParameters.size != 2) {
                        MyProgram!!.setError("\"equal\" takes two strings and returns whether they are equal.")
                    } else if (functionParameters[0] !is TypeString || functionParameters[1] !is TypeString) {
                        MyProgram!!.setError("\"equal\" takes two strings and returns whether they are equal.")
                    }

                    var s1 = ""
                    var s2 = ""
                    if (functionParameters[0] is TypeString) {
                        s1 = (functionParameters[0] as TypeString).stringValue
                    }
                    if (functionParameters[1] is TypeString) {
                        s2 = (functionParameters[1] as TypeString).stringValue
                    }
                    return TypeBoolean(s1 == s2)
                }

                if (functionName == "strcmp") {
                    if (functionParameters.size != 2) {
                        MyProgram!!.setError("\"strcmp(string,string)\" Returns an integer less than, equal to or greater than zero depending on whether the first string is less than, equal to or greater than the second string.")
                    } else if (functionParameters[0] !is TypeString || functionParameters[1] !is TypeString) {
                        MyProgram!!.setError("\"strcmp(string,string)\" Returns an integer less than, equal to or greater than zero depending on whether the first string is less than, equal to or greater than the second string.")
                    }

                    var s1 = ""
                    var s2 = ""
                    if (functionParameters[0] is TypeString) {
                        s1 = (functionParameters[0] as TypeString).stringValue
                    }
                    if (functionParameters[1] is TypeString) {
                        s2 = (functionParameters[1] as TypeString).stringValue
                    }
                    return TypeInteger(s1.compareTo(s2))
                }

                if (functionName == "strlen") {
                    if (functionParameters.size != 1) {
                        MyProgram!!.setError("\"strlen(string)\" Returns an integer representing the length of a string.")
                    } else if (functionParameters[0] !is TypeString) {
                        MyProgram!!.setError("\"strlen(string)\" Returns an integer representing the length of a string.")
                    }

                    var s1 = ""
                    if (functionParameters[0] is TypeString) {
                        s1 = (functionParameters[0] as TypeString).stringValue
                    }
                    return TypeInteger(s1.length)
                }

                if (functionName == "sqrt") {
                    if (functionParameters.size != 1) {
                        MyProgram!!.setError("\"sqrt(float)\" Returns the square root of the float provided.")
                    } else if (functionParameters[0] !is TypeFloat) {
                        MyProgram!!.setError("\"sqrt(float)\" Returns the square root of the float provided.")
                    }

                    var f1 = 0.0f
                    if (functionParameters[0] is TypeFloat) {
                        f1 = (functionParameters[0] as TypeFloat).rawValue as Float
                    }
                    return TypeFloat(kotlin.math.sqrt(f1.toDouble()).toFloat())
                }

                if (functionName == "abs") {
                    if (functionParameters.size != 1) {
                        MyProgram!!.setError("\"abs(float)\" Returns the absolute value of the float provided.")
                    } else if (functionParameters[0] !is TypeFloat) {
                        MyProgram!!.setError("\"abs(float)\" Returns the absolute value of the float provided.")
                    }

                    var f1 = 0.0f
                    if (functionParameters[0] is TypeFloat) {
                        f1 = (functionParameters[0] as TypeFloat).rawValue as Float
                    }
                    return TypeFloat(kotlin.math.abs(f1))
                }

                if (functionName == "ln") {
                    if (functionParameters.size != 1) {
                        MyProgram!!.setError("\"ln(float)\" Returns the natural logarithm of the float provided.")
                    } else if (functionParameters[0] !is TypeFloat) {
                        MyProgram!!.setError("\"ln(float)\" Returns the natural logarithm of the float provided.")
                    }

                    var f1 = 0.0f
                    if (functionParameters[0] is TypeFloat) {
                        f1 = (functionParameters[0] as TypeFloat).rawValue as Float
                    }
                    return TypeFloat(kotlin.math.ln(f1.toDouble()).toFloat())
                }

                if (functionName == "atan") {
                    if (functionParameters.size != 1) {
                        MyProgram!!.setError("\"atan(float)\" Returns the arc-tangent of the floating point number provided.")
                    } else if (functionParameters[0] !is TypeFloat) {
                        MyProgram!!.setError("\"atan(float)\" Returns the arc-tangent of the floating point number provided.")
                    }

                    var f1 = 0.0f
                    if (functionParameters[0] is TypeFloat) {
                        f1 = (functionParameters[0] as TypeFloat).rawValue as Float
                    }
                    f1 = f1 * Math.PI.toFloat() / 180.0f
                    return TypeFloat(kotlin.math.atan(f1).toFloat())
                }

                if (functionName == "acos") {
                    if (functionParameters.size != 1) {
                        MyProgram!!.setError("\"acos(float)\" Returns the arc-cosine of the floating point number provided.")
                    } else if (functionParameters[0] !is TypeFloat) {
                        MyProgram!!.setError("\"acos(float)\" Returns the arc-cosine of the floating point number provided.")
                    }

                    var f1 = 0.0f
                    if (functionParameters[0] is TypeFloat) {
                        f1 = (functionParameters[0] as TypeFloat).rawValue as Float
                    }
                    f1 = f1 * Math.PI.toFloat() / 180.0f
                    return TypeFloat(kotlin.math.acos(f1).toFloat())
                }

                if (functionName == "asin") {
                    if (functionParameters.size != 1) {
                        MyProgram!!.setError("\"asin(float)\" Returns the arc-sine of the floating point number provided.")
                    } else if (functionParameters[0] !is TypeFloat) {
                        MyProgram!!.setError("\"asin(float)\" Returns the arc-sine of the floating point number provided.")
                    }

                    var f1 = 0.0f
                    if (functionParameters[0] is TypeFloat) {
                        f1 = (functionParameters[0] as TypeFloat).rawValue as Float
                    }
                    f1 = f1 * Math.PI.toFloat() / 180.0f
                    return TypeFloat(kotlin.math.asin(f1).toFloat())
                }

                if (functionName == "tan") {
                    if (functionParameters.size != 1) {
                        MyProgram!!.setError("\"tan(float)\" Returns the tangent of the floating point number provided.")
                    } else if (functionParameters[0] !is TypeFloat) {
                        MyProgram!!.setError("\"tan(float)\" Returns the tangent of the floating point number provided.")
                    }

                    var f1 = 0.0f
                    if (functionParameters[0] is TypeFloat) {
                        f1 = (functionParameters[0] as TypeFloat).rawValue as Float
                    }
                    f1 = f1 * Math.PI.toFloat() / 180.0f
                    return TypeFloat(kotlin.math.tan(f1).toFloat())
                }

                if (functionName == "cos") {
                    if (functionParameters.size != 1) {
                        MyProgram!!.setError("\"cos(float)\" Returns the cosine of the floating point number provided.")
                    } else if (functionParameters[0] !is TypeFloat) {
                        MyProgram!!.setError("\"cos(float)\" Returns the cosine of the floating point number provided.")
                    }

                    var f1 = 0.0f
                    if (functionParameters[0] is TypeFloat) {
                        f1 = (functionParameters[0] as TypeFloat).rawValue as Float
                    }
                    f1 = f1 * Math.PI.toFloat() / 180.0f
                    return TypeFloat(kotlin.math.cos(f1).toFloat())
                }

                if (functionName == "sin") {
                    if (functionParameters.size != 1) {
                        MyProgram!!.setError("\"sin(float)\" Returns the sine of the floating point number provided.")
                    } else if (functionParameters[0] !is TypeFloat) {
                        MyProgram!!.setError("\"sin(float)\" Returns the sine of the floating point number provided.")
                    }

                    var f1 = 0.0f
                    if (functionParameters[0] is TypeFloat) {
                        f1 = (functionParameters[0] as TypeFloat).rawValue as Float
                    }
                    f1 = f1 * Math.PI.toFloat() / 180.0f
                    return TypeFloat(kotlin.math.sin(f1).toFloat())
                }

                if (functionName == "getE") {
                    if (functionParameters.size != 0) {
                        MyProgram!!.setError("\"getE()\" Returns the mathematical constant e.")
                    }
                    return TypeFloat(Math.E.toFloat())
                }

                if (functionName == "getPI") {
                    if (functionParameters.size != 0) {
                        MyProgram!!.setError("\"getPI()\" Returns the mathematical constant PI.")
                    }
                    return TypeFloat(Math.PI.toFloat())
                }

                if (functionName == "substr") {
                    if (functionParameters.size != 3) {
                        MyProgram!!.setError("\"substr(string,int,int)\" returns a substring starting and ending with the two indexes provided.")
                    } else if (functionParameters[0] !is TypeString || functionParameters[1] !is TypeInteger || functionParameters[2] !is TypeInteger) {
                        MyProgram!!.setError("\"substr(string,int,int)\" returns a substring starting and ending with the two indexes provided.")
                    }

                    var s1 = ""
                    var start = 0
                    var finish = 0
                    if (functionParameters[0] is TypeString) {
                        s1 = (functionParameters[0] as TypeString).stringValue
                    }
                    if (functionParameters[1] is TypeInteger) {
                        start = (functionParameters[1] as TypeInteger).intValue
                    }
                    if (functionParameters[2] is TypeInteger) {
                        finish = (functionParameters[2] as TypeInteger).intValue
                    }
                    return TypeString(s1.substring(start, finish))
                }

                if (functionName == "getInputString") {
                    if (functionParameters.size != 0) {
                        MyProgram!!.setError("\"getInputString()\" Returns the next string in the array of strings provided as input.")
                    }
                    return TypeString(TP.getInString())
                }

                if (functionName == "getInputStringCount") {
                    if (functionParameters.size != 0) {
                        MyProgram!!.setError("\"getInputStringCount()\" Returns the number of string parameters that have been provided as input.")
                    }
                    return TypeInteger(TP.inputStringSize)
                }

                if (functionName == "setOutputString") {
                    if (functionParameters.size != 1) {
                        MyProgram!!.setError("\"setOutputString(string)\" add a string to the output array of strings.")
                    } else if (functionParameters[0] !is TypeString) {
                        MyProgram!!.setError("\"setOutputString(string)\" add a string to the output array of strings.")
                    }

                    var s1 = ""
                    if (functionParameters[0] is TypeString) {
                        s1 = (functionParameters[0] as TypeString).stringValue
                    }
                    TP.addOutString(s1)
                }

                if (functionName == "getInputFloat") {
                    if (functionParameters.size != 0) {
                        MyProgram!!.setError("\"getInputFloat()\" Returns the next float in the array of floats provided as input.")
                    }
                    return TypeFloat(TP.getInFloat())
                }

                if (functionName == "getInputFloatCount") {
                    if (functionParameters.size != 0) {
                        MyProgram!!.setError("\"getInputFloatCount()\" Returns the number of float parameters that have been provided as input.")
                    }
                    return TypeInteger(TP.inputFloatSize)
                }

                if (functionName == "setOutputFloat") {
                    if (functionParameters.size != 1) {
                        MyProgram!!.setError("\"setOutputFloat(float)\" add a float to the output array of floats.")
                    } else if (functionParameters[0] !is TypeFloat) {
                        MyProgram!!.setError("\"setOutputFloat(float)\" add a float to the output array of floats.")
                    }

                    var s1 = 0.0f
                    if (functionParameters[0] is TypeFloat) {
                        s1 = (functionParameters[0] as TypeFloat).rawValue as Float
                    }
                    TP.addOutFloat(s1)
                }

                if (functionName == "getInputInt") {
                    if (functionParameters.size != 0) {
                        MyProgram!!.setError("\"getInputInt()\" Returns the next int in the array of ints provided as input.")
                    }
                    return TypeInteger(TP.getInInt())
                }

                if (functionName == "getInputIntCount") {
                    if (functionParameters.size != 0) {
                        MyProgram!!.setError("\"getInputIntCount()\" Returns the number of int parameters that have been provided as input.")
                    }
                    return TypeInteger(TP.inputIntSize)
                }

                if (functionName == "setOutputInt") {
                    if (functionParameters.size != 1) {
                        MyProgram!!.setError("\"setOutputInt(int)\" add an int to the output array of ints.")
                    } else if (functionParameters[0] !is TypeInteger) {
                        MyProgram!!.setError("\"setOutputInt(int)\" add an int to the output array of ints.")
                    }

                    var s1 = 0
                    if (functionParameters[0] is TypeInteger) {
                        s1 = (functionParameters[0] as TypeInteger).intValue
                    }
                    TP.addOutInt(s1)
                }

                if (functionName == "equal") {
                    if (functionParameters.size == 2) {
                        var s1 = ""
                        var s2 = ""
                        if (functionParameters[0] is TypeString) {
                            s1 = (functionParameters[0] as TypeString).stringValue
                        }
                        if (functionParameters[1] is TypeString) {
                            s2 = (functionParameters[1] as TypeString).stringValue
                        }
                        return TypeBoolean(s1 == s2)
                    }
                }

                if (functionName == "rand") {
                    return TypeFloat(Math.random().toFloat())
                }

                if (functionName == "indexOf") {
                    val dataString = (functionParameters[0] as TypeString).stringValue
                    val searchString = (functionParameters[1] as TypeString).stringValue
                    val index = (functionParameters[2] as TypeInteger).intValue
                    return TypeInteger(dataString.indexOf(searchString, index))
                } else if (functionName == "intValue") {
                    val f = (functionParameters[0] as TypeFloat).rawValue as Float
                    return TypeInteger(f.toInt())
                } else if (functionName == "floatValue") {
                    val i = (functionParameters[0] as TypeInteger).intValue
                    return TypeFloat(i.toFloat())
                } else if (functionName == "parseFloat") {
                    try {
                        val s1 = (functionParameters[0] as TypeString).stringValue
                        return TypeFloat(s1.toFloat())
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                } else if (functionName == "replaceAll") {
                    val s1 = (functionParameters[0] as TypeString).stringValue
                    val s2 = (functionParameters[1] as TypeString).stringValue
                    val s3 = (functionParameters[2] as TypeString).stringValue
                    return TypeString(Regex(regexEscape(s2)).replace(s1, regexEscape(s3)))
                } else if (functionName == "parseInt") {
                    try {
                        val s1 = (functionParameters[0] as TypeString).stringValue
                        return TypeInteger(s1.toInt())
                    } catch (_: Exception) {
                    }
                } else if (functionName == "printf") {
                    var Content = ""
                    if (functionParameters[0] is TypeString) {
                        Content = (functionParameters[0] as TypeString).stringValue
                    }

                    Content = " $Content"

                    val data = Content.split("\\%s".toRegex()).toTypedArray()
                    var returnMe = ""
                    for (i in data.indices) {
                        returnMe += data[i]
                        if (i + 1 < functionParameters.size) {
                            returnMe += functionParameters[i + 1].toString()
                        }
                    }

                    returnMe = returnMe.substring(1, returnMe.length)
                    return TypeString(returnMe)
                } else if (functionName == "split") {
                    val SplitData = regexEscape((functionParameters[0] as TypeString).stringValue)
                        .split((functionParameters[1] as TypeString).stringValue.toRegex())
                        .toTypedArray()
                    val AL = ArrayList<Any?>()
                    for (i in SplitData.indices) {
                        AL.add(TypeString(SplitData[i]))
                    }
                    val TA = TypeArray(AL as ArrayList<*>)
                    return TA
                } else if (functionName == "length") {
                    if (functionParameters[0] != null) {
                        if (functionParameters[0] is TypeArray) {
                            return TypeInteger(((functionParameters[0] as TypeArray).rawValue as ArrayList<*>).size)
                        }
                        val Temp = functionParameters[0] as ArrayList<*>
                        return TypeInteger(Temp.size)
                    } else {
                        return TypeInteger(0)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            println(TP.getError())
        }

        return null
    }
}
