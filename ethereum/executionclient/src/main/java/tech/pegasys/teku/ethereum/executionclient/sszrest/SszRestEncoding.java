/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.ethereum.executionclient.sszrest;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.executionclient.schema.WithdrawalV1;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;

/**
 * SSZ encoding and decoding for the SSZ-REST Engine API transport (EIP-8161).
 *
 * <p>All integers are little-endian per SSZ specification. Container fields are encoded in
 * declaration order. Variable-length fields use 4-byte offsets.
 */
public class SszRestEncoding {

  // -- Encoding helpers --

  /** Encode ForkchoiceState: 3 x Bytes32 = 96 bytes fixed-size container. */
  public static byte[] encodeForkchoiceState(
      final Bytes32 headHash, final Bytes32 safeHash, final Bytes32 finalizedHash) {
    final byte[] buf = new byte[96];
    System.arraycopy(headHash.toArrayUnsafe(), 0, buf, 0, 32);
    System.arraycopy(safeHash.toArrayUnsafe(), 0, buf, 32, 32);
    System.arraycopy(finalizedHash.toArrayUnsafe(), 0, buf, 64, 32);
    return buf;
  }

  /**
   * Encode ForkchoiceUpdated request.
   *
   * <p>Fixed part: forkchoiceState(96) + attributes_offset(4) = 100 bytes Variable part:
   * Union[None, PayloadAttributes] where None = single 0x00 byte
   */
  public static byte[] encodeForkchoiceUpdatedRequest(
      final Bytes32 headHash,
      final Bytes32 safeHash,
      final Bytes32 finalizedHash,
      final byte[] payloadAttributesSsz) {
    // Fixed part is 100 bytes (96 forkchoice state + 4 offset)
    final int fixedSize = 100;
    final byte[] unionPayload;
    if (payloadAttributesSsz == null) {
      // Union selector 0 = None
      unionPayload = new byte[] {0x00};
    } else {
      // Union selector 1 + payload attributes bytes
      unionPayload = new byte[1 + payloadAttributesSsz.length];
      unionPayload[0] = 0x01;
      System.arraycopy(payloadAttributesSsz, 0, unionPayload, 1, payloadAttributesSsz.length);
    }

    final ByteBuffer buf = ByteBuffer.allocate(fixedSize + unionPayload.length);
    buf.order(ByteOrder.LITTLE_ENDIAN);
    // ForkchoiceState (96 bytes)
    buf.put(headHash.toArrayUnsafe());
    buf.put(safeHash.toArrayUnsafe());
    buf.put(finalizedHash.toArrayUnsafe());
    // Offset to variable part
    buf.putInt(fixedSize);
    // Union payload
    buf.put(unionPayload);

    return buf.array();
  }

  /**
   * Encode PayloadAttributes V3.
   *
   * <p>Fixed: timestamp(8) + prevRandao(32) + feeRecipient(20) + withdrawals_offset(4) +
   * parentBeaconBlockRoot(32) = 96 bytes Variable: List of Withdrawal (each 44 bytes)
   */
  public static byte[] encodePayloadAttributesV3(
      final UInt64 timestamp,
      final Bytes32 prevRandao,
      final Bytes20 suggestedFeeRecipient,
      final List<WithdrawalV1> withdrawals,
      final Bytes32 parentBeaconBlockRoot) {
    final int fixedSize = 96; // 8 + 32 + 20 + 4 + 32
    final int withdrawalSize = 44; // index(8) + validatorIndex(8) + address(20) + amount(8)
    final int variableSize = withdrawals.size() * withdrawalSize;

    final ByteBuffer buf = ByteBuffer.allocate(fixedSize + variableSize);
    buf.order(ByteOrder.LITTLE_ENDIAN);

    // timestamp (8 bytes LE)
    buf.putLong(timestamp.longValue());
    // prevRandao (32 bytes)
    buf.put(prevRandao.toArrayUnsafe());
    // suggestedFeeRecipient (20 bytes)
    buf.put(suggestedFeeRecipient.getWrappedBytes().toArrayUnsafe());
    // withdrawals_offset (4 bytes LE)
    buf.putInt(fixedSize);
    // parentBeaconBlockRoot (32 bytes)
    buf.put(parentBeaconBlockRoot.toArrayUnsafe());

    // Variable: withdrawals
    for (final WithdrawalV1 w : withdrawals) {
      buf.putLong(w.index.longValue());
      buf.putLong(w.validatorIndex.longValue());
      buf.put(w.address.getWrappedBytes().toArrayUnsafe());
      buf.putLong(w.amount.longValue());
    }

    return buf.array();
  }

  /**
   * Encode NewPayload V3 request.
   *
   * <p>Fixed: payload_offset(4) + hashes_offset(4) + parentBeaconBlockRoot(32) = 40 bytes
   * Variable: executionPayloadSsz + concatenated versioned hashes (32 each)
   */
  public static byte[] encodeNewPayloadV3Request(
      final byte[] executionPayloadSsz,
      final List<Bytes32> versionedHashes,
      final Bytes32 parentBeaconBlockRoot) {
    final int fixedSize = 40; // 4 + 4 + 32
    final int hashesSize = versionedHashes.size() * 32;
    final int totalSize = fixedSize + executionPayloadSsz.length + hashesSize;

    final ByteBuffer buf = ByteBuffer.allocate(totalSize);
    buf.order(ByteOrder.LITTLE_ENDIAN);

    // payload_offset -> points to start of variable area
    buf.putInt(fixedSize);
    // hashes_offset -> points to after execution payload
    buf.putInt(fixedSize + executionPayloadSsz.length);
    // parentBeaconBlockRoot (32 bytes)
    buf.put(parentBeaconBlockRoot.toArrayUnsafe());
    // Variable: execution payload
    buf.put(executionPayloadSsz);
    // Variable: versioned hashes
    for (final Bytes32 hash : versionedHashes) {
      buf.put(hash.toArrayUnsafe());
    }

    return buf.array();
  }

  /**
   * Encode NewPayload V4 request.
   *
   * <p>V3 fields + requests_offset(4) + execution_requests. Fixed: payload_offset(4) +
   * hashes_offset(4) + parentBeaconBlockRoot(32) + requests_offset(4) = 44 Variable:
   * executionPayload + hashes + executionRequests
   */
  public static byte[] encodeNewPayloadV4Request(
      final byte[] executionPayloadSsz,
      final List<Bytes32> versionedHashes,
      final Bytes32 parentBeaconBlockRoot,
      final byte[] executionRequestsSsz) {
    final int fixedSize = 44; // 4 + 4 + 32 + 4
    final int hashesSize = versionedHashes.size() * 32;
    final int totalSize =
        fixedSize + executionPayloadSsz.length + hashesSize + executionRequestsSsz.length;

    final ByteBuffer buf = ByteBuffer.allocate(totalSize);
    buf.order(ByteOrder.LITTLE_ENDIAN);

    // payload_offset
    buf.putInt(fixedSize);
    // hashes_offset
    buf.putInt(fixedSize + executionPayloadSsz.length);
    // parentBeaconBlockRoot
    buf.put(parentBeaconBlockRoot.toArrayUnsafe());
    // requests_offset
    buf.putInt(fixedSize + executionPayloadSsz.length + hashesSize);
    // Variable: execution payload
    buf.put(executionPayloadSsz);
    // Variable: versioned hashes
    for (final Bytes32 hash : versionedHashes) {
      buf.put(hash.toArrayUnsafe());
    }
    // Variable: execution requests
    buf.put(executionRequestsSsz);

    return buf.array();
  }

  /**
   * Encode ExchangeCapabilities as SSZ Container { capabilities: List[List[uint8, 64], 128] }.
   *
   * <p>Wire format: container_offset(4) -> list_data = N * item_offset(4) + concatenated UTF-8
   * bytes
   */
  public static byte[] encodeExchangeCapabilities(final List<String> capabilities) {
    final byte[][] encoded = new byte[capabilities.size()][];
    int totalStringBytes = 0;
    for (int i = 0; i < capabilities.size(); i++) {
      encoded[i] = capabilities.get(i).getBytes(StandardCharsets.UTF_8);
      totalStringBytes += encoded[i].length;
    }
    final int innerFixedSize = capabilities.size() * 4;
    final int innerSize = innerFixedSize + totalStringBytes;

    final ByteBuffer buf = ByteBuffer.allocate(4 + innerSize);
    buf.order(ByteOrder.LITTLE_ENDIAN);

    // Container offset to the list
    buf.putInt(4);

    // List item offsets (relative to start of list data)
    int stringOffset = innerFixedSize;
    for (final byte[] enc : encoded) {
      buf.putInt(stringOffset);
      stringOffset += enc.length;
    }
    // Concatenated string bytes
    for (final byte[] enc : encoded) {
      buf.put(enc);
    }

    return buf.array();
  }

  /** Encode GetBlobs request: hashes_offset(4) + concatenated 32-byte hashes. */
  public static byte[] encodeGetBlobsRequest(final List<Bytes32> versionedHashes) {
    final int totalSize = 4 + versionedHashes.size() * 32;
    final ByteBuffer buf = ByteBuffer.allocate(totalSize);
    buf.order(ByteOrder.LITTLE_ENDIAN);
    buf.putInt(4); // offset to start of hashes list
    for (final Bytes32 hash : versionedHashes) {
      buf.put(hash.toArrayUnsafe());
    }
    return buf.array();
  }

  /** Encode GetPayload request: 8-byte payload ID. */
  public static byte[] encodeGetPayloadRequest(final Bytes8 payloadId) {
    return payloadId.getWrappedBytes().toArrayUnsafe();
  }

  // -- Decoding helpers --

  /**
   * Decode PayloadStatus response.
   *
   * <p>Fixed: status(1) + hash_offset(4) + error_offset(4) = 9 bytes Variable: Union[None,
   * Hash32] for latestValidHash Variable: List[uint8, 1024] for validationError
   */
  public static PayloadStatusResult decodePayloadStatus(final byte[] data) {
    final ByteBuffer buf = ByteBuffer.wrap(data);
    buf.order(ByteOrder.LITTLE_ENDIAN);

    final byte statusByte = buf.get();
    final ExecutionPayloadStatus status = statusFromByte(statusByte);

    final int hashOffset = buf.getInt();
    final int errorOffset = buf.getInt();

    // Decode latestValidHash (Union)
    Bytes32 latestValidHash = null;
    if (hashOffset < errorOffset) {
      final byte unionSelector = data[hashOffset];
      if (unionSelector == 0x01 && (errorOffset - hashOffset - 1) == 32) {
        final byte[] hashBytes = new byte[32];
        System.arraycopy(data, hashOffset + 1, hashBytes, 0, 32);
        latestValidHash = Bytes32.wrap(hashBytes);
      }
    }

    // Decode validationError (variable-length byte list -> UTF-8 string)
    String validationError = null;
    if (errorOffset < data.length) {
      final int errorLen = data.length - errorOffset;
      if (errorLen > 0) {
        validationError = new String(data, errorOffset, errorLen, StandardCharsets.UTF_8);
      }
    }

    return new PayloadStatusResult(status, latestValidHash, validationError);
  }

  /**
   * Decode ForkchoiceUpdated response.
   *
   * <p>Fixed: status_offset(4) + payloadId_offset(4) = 8 bytes Variable: PayloadStatus +
   * Union[None, Bytes8]
   */
  public static ForkchoiceUpdatedResult decodeForkchoiceUpdatedResponse(final byte[] data) {
    final ByteBuffer buf = ByteBuffer.wrap(data);
    buf.order(ByteOrder.LITTLE_ENDIAN);

    final int statusOffset = buf.getInt();
    final int payloadIdOffset = buf.getInt();

    // Decode PayloadStatus (nested variable container)
    final int statusLen = payloadIdOffset - statusOffset;
    final byte[] statusBytes = new byte[statusLen];
    System.arraycopy(data, statusOffset, statusBytes, 0, statusLen);
    final PayloadStatusResult payloadStatus = decodePayloadStatus(statusBytes);

    // Decode payloadId (Union)
    Bytes8 payloadId = null;
    if (payloadIdOffset < data.length) {
      final byte unionSelector = data[payloadIdOffset];
      if (unionSelector == 0x01 && (data.length - payloadIdOffset - 1) >= 8) {
        final byte[] idBytes = new byte[8];
        System.arraycopy(data, payloadIdOffset + 1, idBytes, 0, 8);
        payloadId = new Bytes8(Bytes.wrap(idBytes));
      }
    }

    return new ForkchoiceUpdatedResult(payloadStatus, payloadId);
  }

  /**
   * Decode GetPayload response.
   *
   * <p>Fixed: payload_offset(4) + blockValue(32) + blobs_offset(4) + shouldOverrideBuilder(1) +
   * requests_offset(4) = 45 bytes
   */
  public static GetPayloadResult decodeGetPayloadResponse(final byte[] data) {
    final ByteBuffer buf = ByteBuffer.wrap(data);
    buf.order(ByteOrder.LITTLE_ENDIAN);

    final int payloadOffset = buf.getInt();
    // blockValue: 32 bytes (UInt256 LE)
    final byte[] blockValueBytes = new byte[32];
    buf.get(blockValueBytes);
    final UInt256 blockValue = UInt256.fromBytes(Bytes.wrap(blockValueBytes).reverse());

    final int blobsOffset = buf.getInt();
    final boolean shouldOverrideBuilder = buf.get() != 0;
    final int requestsOffset = buf.getInt();

    // Extract variable-length sections
    final int payloadLen = blobsOffset - payloadOffset;
    final byte[] executionPayloadSsz = new byte[payloadLen];
    System.arraycopy(data, payloadOffset, executionPayloadSsz, 0, payloadLen);

    final int blobsLen = requestsOffset - blobsOffset;
    final byte[] blobsBundleSsz = new byte[blobsLen];
    System.arraycopy(data, blobsOffset, blobsBundleSsz, 0, blobsLen);

    final int requestsLen = data.length - requestsOffset;
    final byte[] executionRequestsSsz = new byte[requestsLen];
    System.arraycopy(data, requestsOffset, executionRequestsSsz, 0, requestsLen);

    return new GetPayloadResult(
        Bytes.wrap(executionPayloadSsz),
        blockValue,
        Bytes.wrap(blobsBundleSsz),
        shouldOverrideBuilder,
        Bytes.wrap(executionRequestsSsz));
  }

  /**
   * Decode ExchangeCapabilities response (SSZ Container with List[List[uint8, 64], 128]).
   *
   * <p>Wire format: container_offset(4) -> list_data = N * item_offset(4) + concatenated UTF-8
   * bytes
   */
  public static List<String> decodeExchangeCapabilities(final byte[] data) {
    final ByteBuffer buf = ByteBuffer.wrap(data);
    buf.order(ByteOrder.LITTLE_ENDIAN);

    final int listOffset = buf.getInt();
    if (listOffset >= data.length) {
      return List.of();
    }

    return decodeStringList(data, listOffset, data.length);
  }

  /**
   * Decode GetPayload V3 response (Deneb, no execution requests).
   *
   * <p>Fixed: payload_offset(4) + blockValue(32) + blobs_offset(4) + shouldOverrideBuilder(1) = 41
   * bytes
   */
  public static GetPayloadResult decodeGetPayloadResponseV3(final byte[] data) {
    final ByteBuffer buf = ByteBuffer.wrap(data);
    buf.order(ByteOrder.LITTLE_ENDIAN);

    final int payloadOffset = buf.getInt();
    final byte[] blockValueBytes = new byte[32];
    buf.get(blockValueBytes);
    final UInt256 blockValue = UInt256.fromBytes(Bytes.wrap(blockValueBytes).reverse());

    final int blobsOffset = buf.getInt();
    final boolean shouldOverrideBuilder = buf.get() != 0;

    final int payloadLen = blobsOffset - payloadOffset;
    final byte[] executionPayloadSsz = new byte[payloadLen];
    System.arraycopy(data, payloadOffset, executionPayloadSsz, 0, payloadLen);

    final int blobsLen = data.length - blobsOffset;
    final byte[] blobsBundleSsz = new byte[blobsLen];
    System.arraycopy(data, blobsOffset, blobsBundleSsz, 0, blobsLen);

    return new GetPayloadResult(
        Bytes.wrap(executionPayloadSsz),
        blockValue,
        Bytes.wrap(blobsBundleSsz),
        shouldOverrideBuilder,
        Bytes.EMPTY);
  }

  /**
   * Decode BlobsBundle SSZ.
   *
   * <p>Container { commitments: List[KZGCommitment(48)], proofs: List[KZGProof(48)], blobs:
   * List[Blob] } Fixed: 3 offsets (12 bytes). Variable: concatenated fixed-size elements.
   */
  public static DecodedBlobsBundle decodeBlobsBundle(final byte[] data) {
    if (data.length == 0) {
      return new DecodedBlobsBundle(List.of(), List.of(), List.of());
    }
    final ByteBuffer buf = ByteBuffer.wrap(data);
    buf.order(ByteOrder.LITTLE_ENDIAN);

    final int commitmentsOffset = buf.getInt();
    final int proofsOffset = buf.getInt();
    final int blobsOffset = buf.getInt();

    // commitments: 48 bytes each
    final int numCommitments = (proofsOffset - commitmentsOffset) / 48;
    final List<Bytes> commitments = new ArrayList<>(numCommitments);
    for (int i = 0; i < numCommitments; i++) {
      final byte[] c = new byte[48];
      System.arraycopy(data, commitmentsOffset + i * 48, c, 0, 48);
      commitments.add(Bytes.wrap(c));
    }

    // proofs: 48 bytes each
    final int numProofs = (blobsOffset - proofsOffset) / 48;
    final List<Bytes> proofs = new ArrayList<>(numProofs);
    for (int i = 0; i < numProofs; i++) {
      final byte[] p = new byte[48];
      System.arraycopy(data, proofsOffset + i * 48, p, 0, 48);
      proofs.add(Bytes.wrap(p));
    }

    // blobs: each blob is the same size, inferred from total size / number of blobs
    final int blobsLen = data.length - blobsOffset;
    final int numBlobs = numCommitments; // always equal
    final List<Bytes> blobs = new ArrayList<>(numBlobs);
    if (numBlobs > 0) {
      final int blobSize = blobsLen / numBlobs;
      for (int i = 0; i < numBlobs; i++) {
        final byte[] b = new byte[blobSize];
        System.arraycopy(data, blobsOffset + i * blobSize, b, 0, blobSize);
        blobs.add(Bytes.wrap(b));
      }
    }

    return new DecodedBlobsBundle(commitments, proofs, blobs);
  }

  // -- Inner result types --

  public record PayloadStatusResult(
      ExecutionPayloadStatus status, Bytes32 latestValidHash, String validationError) {}

  public record ForkchoiceUpdatedResult(PayloadStatusResult payloadStatus, Bytes8 payloadId) {}

  public record GetPayloadResult(
      Bytes executionPayloadSsz,
      UInt256 blockValue,
      Bytes blobsBundleSsz,
      boolean shouldOverrideBuilder,
      Bytes executionRequestsSsz) {}

  public record DecodedBlobsBundle(List<Bytes> commitments, List<Bytes> proofs, List<Bytes> blobs) {}

  // -- Private helpers --

  private static ExecutionPayloadStatus statusFromByte(final byte b) {
    return switch (b) {
      case 0 -> ExecutionPayloadStatus.VALID;
      case 1 -> ExecutionPayloadStatus.INVALID;
      case 2 -> ExecutionPayloadStatus.SYNCING;
      case 3 -> ExecutionPayloadStatus.ACCEPTED;
      default -> throw new IllegalArgumentException("Unknown payload status byte: " + b);
    };
  }

  private static List<String> decodeStringList(
      final byte[] data, final int start, final int end) {
    if (start >= end) {
      return List.of();
    }

    final ByteBuffer buf = ByteBuffer.wrap(data, start, end - start);
    buf.order(ByteOrder.LITTLE_ENDIAN);

    final int firstStringOffset = buf.getInt();
    final int numStrings = firstStringOffset / 4;

    if (numStrings <= 0) {
      return List.of();
    }

    final int[] offsets = new int[numStrings];
    offsets[0] = firstStringOffset;
    for (int i = 1; i < numStrings; i++) {
      offsets[i] = buf.getInt();
    }

    final List<String> result = new ArrayList<>(numStrings);
    final int innerLength = end - start;
    for (int i = 0; i < numStrings; i++) {
      final int strStart = offsets[i];
      final int strEnd = (i + 1 < numStrings) ? offsets[i + 1] : innerLength;
      result.add(
          new String(data, start + strStart, strEnd - strStart, StandardCharsets.UTF_8));
    }

    return result;
  }
}
