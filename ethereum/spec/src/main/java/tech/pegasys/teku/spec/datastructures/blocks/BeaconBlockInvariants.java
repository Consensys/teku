/*
 * Copyright 2021 ConsenSys AG.
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

import static tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas.UINT64_SCHEMA;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BeaconBlockInvariants {

  /** Extract the slot value from any SignedBeaconBlock */
  public static UInt64 extractBeaconBlockSlot(final Bytes bytes) {
    return extractSlot(bytes);
  }
  /** Extract the slot value from any BeaconBlock */
  public static UInt64 extractSignedBeaconBlockSlot(final Bytes bytes) {
    return extractSlot(bytes);
  }

  /**
   * Slot is the first field in both BeaconBlock and SignedBeaconBlock. We use the two variants
   * above to ensure we are being clear about the different types.
   */
  private static UInt64 extractSlot(final Bytes bytes) {
    final int size = UINT64_SCHEMA.getSszFixedPartSize();
    final Bytes slotData = bytes.slice(0, size);
    return UINT64_SCHEMA.sszDeserialize(slotData).get();
  }
}
