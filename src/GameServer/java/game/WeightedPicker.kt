package game

class WeightedPicker<T>(vararg items: Pair<T, Int>) {
    private val values: List<T>
    private val cumulativeWeights: List<Double>
    private val totalWeight: Double

    init {
        var sum = 0.0
        values = items.map { it.first }
        cumulativeWeights = items.map {
            sum += it.second
            sum
        }
        totalWeight = sum
    }

    fun next(): T {
        val r = Math.random() * totalWeight
        val index = cumulativeWeights.indexOfFirst { r < it }
        return values[index]
    }
}