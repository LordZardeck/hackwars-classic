package com.hackwars.networking

import com.google.protobuf.MessageLite
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Specifies the size, in bytes, of the type prefix used in the protocol's message frames.
 * The type prefix is a fixed-length segment at the beginning of a message frame that
 * identifies the type of the frame (e.g., `AUTH`, `SERVICE`). This constant is used
 * to ensure consistent handling of the type prefix across the messaging protocol.
 */
private const val TYPE_PREFIX_BYTES = Byte.SIZE_BYTES

/**
 * Represents the size, in bytes, of the length prefix used in the networking protocol.
 *
 * This constant defines the fixed size of the integer field that precedes the payload of a message,
 * typically used to indicate the length of the payload in serialized communication frames. The value
 * is derived from `Integer.BYTES`, corresponding to the size of a 32-bit integer (4 bytes).
 *
 * Usage of this constant ensures consistent handling of length prefixes across the protocol for
 * encoding and decoding frames.
 */
private const val LENGTH_PREFIX_BYTES = Integer.BYTES

/**
 * Represents the combined size, in bytes, of the header for a frame in the networking protocol.
 *
 * A frame header consists of two components:
 * - A type prefix, which identifies the type of message being transmitted. The size of this
 *   component is denoted by `TYPE_PREFIX_BYTES`.
 * - A length prefix, which specifies the length of the frame payload. The size of this
 *   component is denoted by `LENGTH_PREFIX_BYTES`.
 *
 * By combining these two prefix sizes, `FRAME_HEADER_BYTES` provides the total size of the
 * header portion of a frame. This constant is used to handle frame parsing and construction
 * in the protocol.
 */
private const val FRAME_HEADER_BYTES = TYPE_PREFIX_BYTES + LENGTH_PREFIX_BYTES

/**
 * Represents a decoded frame in the networking protocol, containing the type of message
 * and its associated payload data.
 *
 * @property messageType The type of the frame message, as defined by the `FrameMessageType` enumeration.
 * @property payload The raw byte array containing the payload data of the frame.
 */
@JvmRecord
data class DecodedFrame(val messageType: FrameMessageType, val payload: ByteArray)

/**
 * Represents exceptions that occur during the decoding process of a frame in the networking protocol.
 * This is a sealed class with specific subclasses detailing the type of decoding error.
 */
sealed class FrameDecodingException : IOException {
    @JvmOverloads
    constructor(message: String, cause: Throwable? = null) : super(message, cause)

    /**
     * Exception indicating that a frame being decoded is invalid.
     *
     * This exception is used to represent errors where the frame does not conform
     * to the expected format or structure during decoding in the networking protocol.
     *
     * @constructor Constructs an InvalidFrame exception with a given message and an optional cause.
     * @param message The detail message explaining the reason for the exception.
     * @param cause The throwable cause of the exception, or null if no cause is provided.
     */
    class InvalidFrame @JvmOverloads constructor(message: String, cause: Throwable? = null) :
        FrameDecodingException(message, cause)

    /**
     * Exception indicating that the payload size of a frame exceeds the allowed maximum size
     * for the specified message type during frame decoding in the networking protocol.
     *
     * This exception is used to enforce payload size limits for frames of different message types.
     * It ensures compliance with protocol requirements and prevents processing of oversized payloads.
     * The maximum payload size varies based on the message type and can optionally be configured for
     * certain types.
     *
     * @constructor Creates an IllegalPayloadSize exception.
     * @param messageType The type of frame message associated with the payload.
     * @param payloadSize The actual size of the payload in bytes.
     * @param maxServicePayloadSize The optional maximum payload size in bytes for `SERVICE` message types.
     *                                   If not specified, the default maximum size for the `SERVICE` type is used.
     */
    class IllegalPayloadSize(
        val messageType: FrameMessageType,
        val payloadSize: Int,
        maxServicePayloadSize: Int? = null
    ) : FrameDecodingException(
        "Frame payload exceeds configured max payload size for $messageType: $payloadSize > ${
            messageType.maxPayloadSize(maxServicePayloadSize)
        }"
    )
}


/**
 * Decodes the message type from the provided DataInputStream by reading the wire representation
 * of the frame message type, validating it, and mapping it to a corresponding FrameMessageType.
 *
 * @param dataInputStream The input stream from which the wire value representing the
 *                        frame message type is read. Must not be null.
 * @return The decoded FrameMessageType corresponding to the wire value.
 * @throws FrameDecodingException.InvalidFrame If the frame is missing the type/length header
 *                                             or an unsupported frame type is encountered.
 * @throws IOException If an I/O error occurs during reading from the stream.
 */
private fun getMessageType(dataInputStream: DataInputStream): FrameMessageType {
    val messageTypeWire = runCatching { dataInputStream.readUnsignedByte() }
        .onFailure { e ->
            when (e) {
                is EOFException -> throw FrameDecodingException.InvalidFrame(
                    "Frame is missing the type/length header.", e
                )

                else -> throw e
            }
        }
        .getOrThrow()

    return FrameMessageType.fromWireValue(messageTypeWire)
        ?: throw FrameDecodingException.InvalidFrame("Unsupported frame type: $messageTypeWire. Connection must be closed.")
}

/**
 * Reads and retrieves the message length from a DataInputStream.
 *
 * This method attempts to read an integer from the provided DataInputStream. If an EOFException is encountered,
 * it throws a FrameDecodingException.InvalidFrame, indicating that the frame is missing the type/length header.
 * Any other exceptions encountered during reading are rethrown.
 *
 * @param dataInputStream The DataInputStream from which the message length is to be read.
 * @return The length of the message as an integer.
 * @throws FrameDecodingException.InvalidFrame If the frame is missing the type/length header.
 * @throws IOException If an I/O error occurs while reading from the stream.
 */
private fun getMessageLength(dataInputStream: DataInputStream): Int {
    return runCatching { dataInputStream.readInt() }
        .onFailure { e ->
            when (e) {
                is EOFException -> throw FrameDecodingException.InvalidFrame(
                    "Frame is missing the type/length header.", e
                )

                else -> throw e
            }
        }
        .getOrThrow()
}

/**
 * Decodes a frame from the provided input stream and validates the payload size against
 * the maximum allowed size for the specific message type. Throws exceptions if the frame
 * is invalid or if an error occurs during decoding.
 *
 * @param inputStream The input stream containing the raw frame data to be decoded.
 * @param maxServicePayloadSize An optional override for the maximum payload size of frames
 *                              of the `SERVICE` message type. If not provided, the default maximum
 *                              size for `SERVICE` frames is used.
 * @return A `DecodedFrame` object containing the message type and the decoded payload.
 * @throws FrameDecodingException.IllegalPayloadSize If the payload size exceeds the allowable maximum for
 *                                                   the specified message type.
 * @throws FrameDecodingException.InvalidFrame If the frame is invalid or malformed.
 * @throws IOException If an I/O error occurs while reading from the input stream.
 */
fun decodeFrame(inputStream: InputStream, maxServicePayloadSize: Int? = null): DecodedFrame {
    val dataInputStream = DataInputStream(inputStream)

    val messageType = getMessageType(dataInputStream)
    val messageLength = getMessageLength(dataInputStream)

    if (messageLength > messageType.maxPayloadSize(maxServicePayloadSize)) {
        throw FrameDecodingException.IllegalPayloadSize(
            messageType, messageLength, maxServicePayloadSize
        )
    }

    val payload = dataInputStream.readNBytes(messageLength)
    if (payload.size != messageLength) {
        throw FrameDecodingException.InvalidFrame("Frame payload ended before the declared length was satisfied.")
    }

    return DecodedFrame(messageType, payload)

}

/**
 * Encodes a frame message for the networking protocol by serializing the provided payload
 * and appending the appropriate frame header based on the message type.
 *
 * @param messageType The type of the frame message, specifying its encoding details and maximum payload size.
 * @param message The payload of the frame message, serialized using the protocol buffer (Protobuf) format.
 * @param maxServicePayloadSize An optional override for the maximum payload size of the SERVICE frame type.
 *                              If not provided, the default `SERVICE` payload size will be used.
 * @return A `ByteArray` containing the encoded frame, including both the frame header and the serialized payload.
 * @throws IllegalArgumentException If the size of the serialized payload exceeds the allowed maximum payload size
 *                                  for the specified frame message type.
 */
fun encodeFrame(messageType: FrameMessageType, message: MessageLite, maxServicePayloadSize: Int? = null): ByteArray {
    val maxPayloadSize = messageType.maxPayloadSize(maxServicePayloadSize)
    val payload = message.toByteArray()

    if (payload.size > maxPayloadSize) {
        throw IllegalArgumentException("Frame payload exceeds configured max payload size for " + messageType + ": " + payload.size + " > " + maxPayloadSize)
    }

    val buffer = ByteBuffer.allocate(FRAME_HEADER_BYTES + payload.size)
    buffer.put(messageType.wireValue)
    buffer.putInt(payload.size)
    buffer.put(payload)
    return buffer.array()
}