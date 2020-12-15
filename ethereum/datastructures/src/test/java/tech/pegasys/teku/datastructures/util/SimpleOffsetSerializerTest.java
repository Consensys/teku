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

package tech.pegasys.teku.datastructures.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SSZDeserializeException;

class SimpleOffsetSerializerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  // Ideally these would live in the test specific classes
  // but those modules can't access SimpleOffsetSerializer
  @Test
  public void shouldRoundTripBlsPublicKey() {
    final BLSPublicKey original = dataStructureUtil.randomPublicKey();
    final Bytes data = SimpleOffsetSerializer.serialize(original);
    final BLSPublicKey result = SimpleOffsetSerializer.deserialize(data, BLSPublicKey.class);
    assertThat(result).isEqualTo(original);
  }

  @Test
  public void shouldRoundTripBlsSignature() {
    final BLSSignature original = dataStructureUtil.randomSignature();
    final Bytes data = SimpleOffsetSerializer.serialize(original);
    final BLSSignature result = SimpleOffsetSerializer.deserialize(data, BLSSignature.class);
    assertThat(result).isEqualTo(original);
  }

  @Test
  public void decode_boolean() {
    final Bytes data = Bytes.fromHexString("0x01");
    final Boolean decoded = SimpleOffsetSerializer.deserialize(data, boolean.class);
    assertThat(decoded).isTrue();
  }

  @Test
  public void decode_Boolean() {
    final Bytes data = Bytes.fromHexString("0x01");
    final Boolean decoded = SimpleOffsetSerializer.deserialize(data, Boolean.class);
    assertThat(decoded).isTrue();
  }

  @Test
  public void deserialize_toInvalidClass() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(1);

    final Bytes encoded = SimpleOffsetSerializer.serialize(block);
    assertThatThrownBy(() -> SimpleOffsetSerializer.deserialize(encoded, RandomClass.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unable to deserialize RandomClass");
  }

  @Test
  public void deserialize_toInvalidPrimitive() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(1);

    final Bytes encoded = SimpleOffsetSerializer.serialize(block);
    assertThatThrownBy(() -> SimpleOffsetSerializer.deserialize(encoded, boolean.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unread data detected");
  }

  @Test
  public void deserialize_extraDataAppended() {
    final Checkpoint checkpoint = dataStructureUtil.randomCheckpoint();
    final Bytes extraData = Bytes.fromHexString("0x00");

    final Bytes checkpointData = SimpleOffsetSerializer.serialize(checkpoint);
    final Bytes encoded = Bytes.concatenate(checkpointData, extraData);
    assertThatThrownBy(() -> SimpleOffsetSerializer.deserialize(encoded, Checkpoint.class))
        .isInstanceOf(SSZDeserializeException.class)
        .hasMessageContaining("unread bytes");
  }

  @Test
  public void decode_toUnregisteredContainerType() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(1);

    final Bytes encoded = SimpleOffsetSerializer.serialize(block);
    assertThatThrownBy(
            () -> SimpleOffsetSerializer.deserialize(encoded, UnregisteredContainer.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Unable to find reflection information for class UnregisteredContainer");
  }

  private static class UnregisteredContainer implements SSZContainer {}

  private static class RandomClass {}
}
