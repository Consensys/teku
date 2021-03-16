/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.ssz.SszDataAssert.assertThatSszData;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.schema.collections.SszBitvectorSchema;

class MetadataMessageTest {

  private static final Bytes EXPECTED_SSZ =
      Bytes.fromHexString("0x23000000000000000100000000000080");
  private static final MetadataMessage MESSAGE =
      new MetadataMessage(UInt64.valueOf(0x23), SszBitvectorSchema.create(64).ofBits(0, 63));

  @Test
  public void shouldSerializeToSsz() {
    final Bytes result = MESSAGE.sszSerialize();
    assertThat(result).isEqualTo(EXPECTED_SSZ);
  }

  @Test
  public void shouldDeserializeFromSsz() {
    MetadataMessage result = MetadataMessage.SSZ_SCHEMA.sszDeserialize(EXPECTED_SSZ);
    assertThatSszData(result).isEqualByAllMeansTo(MESSAGE);
  }

  @Test
  public void shouldRejectTooShortBitlist() {
    assertThatThrownBy(
            () ->
                new MetadataMessage(UInt64.valueOf(15), SszBitvectorSchema.create(63).getDefault()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectTooLongBitlist() {
    assertThatThrownBy(
            () ->
                new MetadataMessage(UInt64.valueOf(15), SszBitvectorSchema.create(65).getDefault()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
