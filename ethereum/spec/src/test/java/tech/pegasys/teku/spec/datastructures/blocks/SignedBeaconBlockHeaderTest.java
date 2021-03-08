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

package tech.pegasys.teku.spec.datastructures.blocks;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class SignedBeaconBlockHeaderTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private UInt64 slot = dataStructureUtil.randomUInt64();
  private UInt64 proposer_index = dataStructureUtil.randomUInt64();
  private Bytes32 previous_block_root = dataStructureUtil.randomBytes32();
  private Bytes32 state_root = dataStructureUtil.randomBytes32();
  private Bytes32 block_body_root = dataStructureUtil.randomBytes32();
  private BLSSignature signature = dataStructureUtil.randomSignature();

  private SignedBeaconBlockHeader signedBlockHeader =
      new SignedBeaconBlockHeader(
          new BeaconBlockHeader(
              slot, proposer_index, previous_block_root, state_root, block_body_root),
          signature);

  @Test
  public void shouldRoundTripViaSsz() {
    final Bytes ssz = signedBlockHeader.sszSerialize();
    final SignedBeaconBlockHeader result = SignedBeaconBlockHeader.SSZ_SCHEMA.sszDeserialize(ssz);

    assertThat(result).isEqualTo(signedBlockHeader);
  }
}
