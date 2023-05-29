/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.blocks;

import static tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas.UINT64_SCHEMA;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.schema.SszType;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContents;

public class BeaconBlockInvariants {

  private static final int SSZ_OFFSET_SIZE = 4;

  /**
   * {@link SignedBeaconBlockSchema} 4 (MESSAGE variable length) + 96 (SIGNATURE fixed-size length)
   */
  private static final int BLOCK_DATA_OFFSET_SUGGESTS_TYPE_IS_SIGNED_BEACON_BLOCK = 100;

  /**
   * Extract the slot value from any {@link BeaconBlock}.
   *
   * <p>Slot is the first field and recorded directly because it's fixed length. So just read the
   * first UInt64 worth of bytes.
   *
   * @param bytes the SSZ bytes to extract a slot from
   */
  public static UInt64 extractBeaconBlockSlot(final Bytes bytes) {
    final int size = UINT64_SCHEMA.getSszFixedPartSize();
    final Bytes slotData = bytes.slice(0, size);
    return UINT64_SCHEMA.sszDeserialize(slotData).get();
  }

  /**
   * Extract the slot value from any {@link SignedBeaconBlock} or {@link SignedBlockContents}.
   *
   * <p>The slot is the first field but is inside the variable length beacon block so a 4 byte
   * offset to the start of the beacon block data is recorded. Use that prefix to get the beacon
   * block data and then find the slot as for an unsigned block
   *
   * @param bytes the SSZ bytes to extract a slot from
   */
  public static UInt64 extractSignedBeaconBlockSlot(final Bytes bytes) {
    int blockDataOffset = SszType.sszBytesToLength(bytes.slice(0, SSZ_OFFSET_SIZE));
    if (blockDataOffset == BLOCK_DATA_OFFSET_SUGGESTS_TYPE_IS_SIGNED_BEACON_BLOCK) {
      return extractBeaconBlockSlot(bytes.slice(blockDataOffset));
    }
    // extract blockDataOffset for SignedBlockContents
    blockDataOffset =
        blockDataOffset + SszType.sszBytesToLength(bytes.slice(blockDataOffset, SSZ_OFFSET_SIZE));
    return extractBeaconBlockSlot(bytes.slice(blockDataOffset));
  }
}
