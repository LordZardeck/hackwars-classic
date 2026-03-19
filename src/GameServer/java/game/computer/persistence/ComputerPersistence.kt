package game.computer.persistence

interface ComputerPersistence {
    fun parse(xml: String): ComputerSnapshot
    fun serialize(snapshot: ComputerSnapshot): String
}
