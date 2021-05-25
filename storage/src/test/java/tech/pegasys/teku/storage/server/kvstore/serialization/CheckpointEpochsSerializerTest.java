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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.CHECKPOINT_EPOCHS_SERIALIZER;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.CheckpointEpochs;

class CheckpointEpochsSerializerTest {

  private static final CheckpointEpochs VALUE =
      new CheckpointEpochs(UInt64.valueOf(3232), UInt64.valueOf(9382));
  /**
   * The original serialization format for CheckpointEpochs. This may be written to existing
   * databases so if you need to update it, backwards compatibility is likely to have been broken.
   */
  private static final Bytes EXPECTED_SERIALIZATION =
      Bytes.fromHexString("0xa00c000000000000a624000000000000");

  @Test
  void shouldRoundTripCheckpointEpochs() {

    final byte[] data = CHECKPOINT_EPOCHS_SERIALIZER.serialize(VALUE);
    final CheckpointEpochs result = CHECKPOINT_EPOCHS_SERIALIZER.deserialize(data);
    assertThat(result).isEqualTo(VALUE);
  }

  @Test
  void shouldDecodeLegacyValue() {
    assertThat(CHECKPOINT_EPOCHS_SERIALIZER.deserialize(EXPECTED_SERIALIZATION.toArrayUnsafe()))
        .isEqualTo(VALUE);
  }
}
