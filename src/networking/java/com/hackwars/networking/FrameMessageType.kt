package com.hackwars.networking

/**
 * An enumeration representing types of frame messages used in the networking protocol.
 *
 * @property wireValue The byte value used to represent the message type in the wire protocol.
 * @property maxPayloadSize The default maximum size, in bytes, for the payload associated with the message type.
 */
enum class FrameMessageType(val wireValue: Byte, private val maxPayloadSize: Int) {
    AUTH(1.toByte(), 2048),
    SERVICE(2.toByte(), 8 * 1024 * 1024);

    /**
     * Retrieves the maximum allowed payload size for the frame message type.
     * For the `SERVICE` message type, the maximum size can be overridden by specifying a
     * custom `maxServicePayloadSize`. For the `AUTH` message type, the predefined maximum
     * size is always returned.
     *
     * @param maxServicePayloadSize An optional override for the maximum payload size of the `SERVICE` message type.
     *                              If not provided, the default `SERVICE` payload size is used.
     * @return The maximum payload size in bytes for the frame message type.
     */
    fun maxPayloadSize(maxServicePayloadSize: Int? = null): Int {
        return when (this) {
            AUTH -> this.maxPayloadSize
            SERVICE -> maxServicePayloadSize ?: maxPayloadSize
        }
    }

    companion object {
        /**
         * Maps an integer wire value to the corresponding FrameMessageType enumeration value.
         *
         * @param wireValue The integer value representing a frame message type in the wire protocol.
         *                  Valid values are 1 for AUTH and 2 for SERVICE.
         * @return The FrameMessageType corresponding to the provided wire value, or null if the value
         *         does not correspond to a valid FrameMessageType.
         */
        fun fromWireValue(wireValue: Int): FrameMessageType? {
            return when (wireValue) {
                1 -> AUTH
                2 -> SERVICE
                else -> null
            }
        }
    }
}