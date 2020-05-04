/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.networking.eth2.rpc.core.encodings;

import static tech.pegasys.teku.util.config.Constants.MAX_CHUNK_SIZE;
import static tech.pegasys.teku.util.iostreams.IOStreamConstants.END_OF_STREAM;

import com.google.protobuf.CodedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.compression.Compressor;
import tech.pegasys.teku.networking.eth2.compression.exceptions.CompressionException;
import tech.pegasys.teku.networking.eth2.compression.exceptions.PayloadLargerThanExpectedException;
import tech.pegasys.teku.networking.eth2.compression.exceptions.PayloadSmallerThanExpectedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;

class LengthPrefixedPayloadDecoder<T> {
  private static final Logger LOG = LogManager.getLogger();

  static final Bytes MAX_CHUNK_SIZE_PREFIX = ProtobufEncoder.encodeVarInt(MAX_CHUNK_SIZE);

  private final RpcPayloadEncoder<T> payloadEncoder;
  private final Compressor compressor;

  public LengthPrefixedPayloadDecoder(
      final RpcPayloadEncoder<T> payloadEncoder, final Compressor compressor) {
    this.payloadEncoder = payloadEncoder;
    this.compressor = compressor;
  }

  public T decodePayload(final InputStream inputStream) throws RpcException {
    try {
      final int uncompressedPayloadSize = processLengthPrefixHeader(inputStream);
      return processPayload(inputStream, uncompressedPayloadSize);
    } catch (PayloadSmallerThanExpectedException e) {
      throw RpcException.PAYLOAD_TRUNCATED;
    } catch (PayloadLargerThanExpectedException e) {
      throw RpcException.EXTRA_DATA_APPENDED;
    } catch (CompressionException e) {
      LOG.debug("Failed to uncompress rpc payload", e);
      throw RpcException.FAILED_TO_UNCOMPRESS_MESSAGE;
    } catch (IOException e) {
      LOG.error("Unable to decode rpc payload", e);
      throw RpcException.SERVER_ERROR;
    }
  }

  /** Decode the length-prefix header, which contains the length of the uncompressed payload */
  private int processLengthPrefixHeader(final InputStream inputStream)
      throws RpcException, IOException {
    // Collect length prefix raw bytes
    final ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();

    boolean foundTerminatingCharacter = false;
    int nextByte;
    while ((nextByte = inputStream.read()) != END_OF_STREAM) {
      if (headerBytes.size() >= MAX_CHUNK_SIZE_PREFIX.size()) {
        // Any protobuf length requiring more bytes than this will also be bigger.
        throw RpcException.CHUNK_TOO_LONG;
      }
      headerBytes.write(nextByte);
      if ((nextByte & 0x80) == 0) {
        // Var int ends at first byte where (b & 0x80) == 0
        foundTerminatingCharacter = true;
        break;
      }
    }

    if (!foundTerminatingCharacter) {
      throw RpcException.MESSAGE_TRUNCATED;
    }

    // Decode length prefix from raw bytes
    final CodedInputStream in = CodedInputStream.newInstance(headerBytes.toByteArray());
    final int uncompressedPayloadSize;
    try {
      uncompressedPayloadSize = in.readRawVarint32();
    } catch (final IOException e) {
      throw RpcException.MALFORMED_MESSAGE_LENGTH;
    }

    // Validate length prefix size is within bounds
    if (uncompressedPayloadSize > MAX_CHUNK_SIZE) {
      LOG.trace("Rejecting message as length is too long");
      throw RpcException.CHUNK_TOO_LONG;
    }

    // Return payload size metadata
    return uncompressedPayloadSize;
  }

  private T processPayload(final InputStream inputStream, final int uncompressedPayloadSize)
      throws RpcException, CompressionException {
    final Bytes uncompressedPayload = compressor.uncompress(inputStream, uncompressedPayloadSize);
    if (uncompressedPayload.size() < uncompressedPayloadSize) {
      throw RpcException.PAYLOAD_TRUNCATED;
    }
    return payloadEncoder.decode(uncompressedPayload);
  }
}
