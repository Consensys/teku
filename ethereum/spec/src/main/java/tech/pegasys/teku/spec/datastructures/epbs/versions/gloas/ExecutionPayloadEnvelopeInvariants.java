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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import static tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas.BYTES32_SCHEMA;
import static tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas.UINT64_SCHEMA;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.schema.SszType;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

/** Utility functions to extract data from the ssz bytes of Gloas execution payload envelopes. */
public class ExecutionPayloadEnvelopeInvariants {

  private static final int BYTES_PER_LENGTH_OFFSET = 4;

  private static final int EXECUTION_PAYLOAD_ENVELOPE_OFFSET_IN_SIGNED_ENVELOPE =
      BYTES_PER_LENGTH_OFFSET + SszSignatureSchema.INSTANCE.getSszFixedPartSize();

  private static final int SLOT_OFFSET_IN_EXECUTION_PAYLOAD_ENVELOPE =
      BYTES_PER_LENGTH_OFFSET
          + BYTES_PER_LENGTH_OFFSET
          + UINT64_SCHEMA.getSszFixedPartSize()
          + BYTES32_SCHEMA.getSszFixedPartSize();

  private ExecutionPayloadEnvelopeInvariants() {}

  public static UInt64 extractSignedExecutionPayloadEnvelopeSlot(final Bytes bytes) {
    return extractSlotFromSignedExecutionPayloadEnvelope(
        bytes, SLOT_OFFSET_IN_EXECUTION_PAYLOAD_ENVELOPE);
  }

  // Delegates to non-blinded extraction because both containers share identical SSZ fixed-part
  // layout: payload/payload_header are both variable-size (same 4-byte offset), followed by the
  // same fixed-size fields (builder_index, beacon_block_root, slot, state_root).
  public static UInt64 extractSignedBlindedExecutionPayloadEnvelopeSlot(final Bytes bytes) {
    return extractSignedExecutionPayloadEnvelopeSlot(bytes);
  }

  private static UInt64 extractSlotFromSignedExecutionPayloadEnvelope(
      final Bytes bytes, final int slotOffsetInExecutionPayloadEnvelope) {
    final int executionPayloadEnvelopeDataOffset =
        SszType.sszBytesToLength(bytes.slice(0, BYTES_PER_LENGTH_OFFSET));
    if (executionPayloadEnvelopeDataOffset
        != EXECUTION_PAYLOAD_ENVELOPE_OFFSET_IN_SIGNED_ENVELOPE) {
      throw new IllegalArgumentException("Unexpected signed execution payload envelope format");
    }
    final Bytes slotData =
        bytes.slice(
            executionPayloadEnvelopeDataOffset + slotOffsetInExecutionPayloadEnvelope,
            UINT64_SCHEMA.getSszFixedPartSize());
    return UINT64_SCHEMA.sszDeserialize(slotData).get();
  }
}
