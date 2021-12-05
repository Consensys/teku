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
import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;

import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.altair.MetadataMessageSchemaAltair;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.phase0.MetadataMessageSchemaPhase0;
import tech.pegasys.teku.util.config.Constants;

class MetadataMessageTest {

  private static final MetadataMessageSchemaPhase0 PHASE0_SCHEMA =
      new MetadataMessageSchemaPhase0();
  private static final MetadataMessageSchemaAltair ALTAIR_SCHEMA =
      new MetadataMessageSchemaAltair();
  private static final Bytes EXPECTED_SSZ =
      Bytes.fromHexString("0x23000000000000000100000000000080");
  private static final MetadataMessage MESSAGE =
      PHASE0_SCHEMA.create(UInt64.valueOf(0x23), List.of(0, 63), Collections.emptyList());

  @Test
  public void shouldSerializeToSsz() {
    final Bytes result = MESSAGE.sszSerialize();
    assertThat(result).isEqualTo(EXPECTED_SSZ);
  }

  @Test
  public void shouldDeserializeFromSsz() {
    MetadataMessage result = PHASE0_SCHEMA.sszDeserialize(EXPECTED_SSZ);
    assertThatSszData(result).isEqualByAllMeansTo(MESSAGE);
  }

  @Test
  public void shouldRejectOutOfBoundsAttnets() {
    assertThatThrownBy(
            () ->
                PHASE0_SCHEMA.create(
                    UInt64.valueOf(15),
                    List.of(Constants.ATTESTATION_SUBNET_COUNT),
                    Collections.emptyList()))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  public void shouldRejectOutOfBoundsSyncnets() {
    assertThatThrownBy(
            () ->
                ALTAIR_SCHEMA.create(
                    UInt64.valueOf(15),
                    Collections.emptyList(),
                    List.of(NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT)))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }
}
