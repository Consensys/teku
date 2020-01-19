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

package tech.pegasys.artemis.datastructures.blocks;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomBytes32;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.bls.BLSSignature;

class SignedBeaconBlockHeaderTest {
  private int seed = 100;
  private UnsignedLong slot = randomUnsignedLong(seed);
  private Bytes32 previous_block_root = randomBytes32(seed++);
  private Bytes32 state_root = randomBytes32(seed++);
  private Bytes32 block_body_root = randomBytes32(seed++);
  private BLSSignature signature = BLSSignature.random(seed++);

  private SignedBeaconBlockHeader signedBlockHeader =
      new SignedBeaconBlockHeader(
          new BeaconBlockHeader(slot, previous_block_root, state_root, block_body_root), signature);

  @Test
  public void shouldRoundTripViaSsz() {
    final Bytes ssz = SimpleOffsetSerializer.serialize(signedBlockHeader);
    final SignedBeaconBlockHeader result =
        SimpleOffsetSerializer.deserialize(ssz, SignedBeaconBlockHeader.class);

    assertThat(result).isEqualTo(signedBlockHeader);
  }
}
