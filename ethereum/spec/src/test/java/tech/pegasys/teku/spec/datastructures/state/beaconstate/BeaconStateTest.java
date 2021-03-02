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

package tech.pegasys.teku.spec.datastructures.state.beaconstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.constants.TestConstantsLoader;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.backing.SszTestUtils;
import tech.pegasys.teku.util.config.Constants;

@ExtendWith(BouncyCastleExtension.class)
class BeaconStateTest {

  private final Spec spec = SpecFactory.createMinimal();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SchemaDefinitions schemaDefinitions = spec.getGenesisSchemaDefinitions();

  @Test
  void vectorLengthsTest() {
    List<Integer> vectorLengths =
        List.of(
            Constants.SLOTS_PER_HISTORICAL_ROOT,
            Constants.SLOTS_PER_HISTORICAL_ROOT,
            Constants.EPOCHS_PER_HISTORICAL_VECTOR,
            Constants.EPOCHS_PER_SLASHINGS_VECTOR,
            Constants.JUSTIFICATION_BITS_LENGTH);
    assertEquals(
        vectorLengths, SszTestUtils.getVectorLengths(schemaDefinitions.getBeaconStateSchema()));
  }

  @Test
  void simpleMutableBeaconStateTest() {
    UInt64 val1 = UInt64.valueOf(0x3333);
    BeaconState stateR1 =
        spec.getGenesisSpec()
            .getSchemaDefinitions()
            .getBeaconStateSchema()
            .createEmpty()
            .updated(
                state -> {
                  state.getBalances().add(val1);
                });
    UInt64 v1 = stateR1.getBalances().get(0);

    assertThat(stateR1.getBalances().size()).isEqualTo(1);
    assertThat(stateR1.getBalances().get(0)).isEqualTo(UInt64.valueOf(0x3333));

    BeaconState stateR2 =
        stateR1.updated(
            state -> {
              state.getBalances().add(UInt64.valueOf(0x4444));
            });
    UInt64 v2 = stateR2.getBalances().get(0);

    // check that view caching is effectively works and the value
    // is not recreated from tree node without need
    assertThat(v1).isSameAs(val1);
    assertThat(v2).isSameAs(val1);
  }

  @Test
  public void changeSpecConstantsTest() {
    final Spec standardSpec = SpecFactory.createMinimal();
    final SpecConstants specConstants =
        TestConstantsLoader.loadConstantsBuilder("minimal")
            .slotsPerHistoricalRoot(123)
            .historicalRootsLimit(123)
            .epochsPerEth1VotingPeriod(123)
            .validatorRegistryLimit(123L)
            .epochsPerHistoricalVector(123)
            .epochsPerSlashingsVector(123)
            .maxAttestations(123)
            .build();
    final Spec modifiedSpec = SpecFactory.create(specConstants);
    BeaconState s1 =
        modifiedSpec.getGenesisSchemaDefinitions().getBeaconStateSchema().createEmpty();

    BeaconState s2 =
        standardSpec.getGenesisSchemaDefinitions().getBeaconStateSchema().createEmpty();

    assertThat(s1.getBlock_roots().getMaxSize()).isNotEqualTo(s2.getBlock_roots().getMaxSize());
    assertThat(s1.getState_roots().getMaxSize()).isNotEqualTo(s2.getState_roots().getMaxSize());
    assertThat(s1.getHistorical_roots().getMaxSize())
        .isNotEqualTo(s2.getHistorical_roots().getMaxSize());
    assertThat(s1.getEth1_data_votes().getMaxSize())
        .isNotEqualTo(s2.getEth1_data_votes().getMaxSize());
    assertThat(s1.getValidators().getMaxSize()).isNotEqualTo(s2.getValidators().getMaxSize());
    assertThat(s1.getBalances().getMaxSize()).isNotEqualTo(s2.getBalances().getMaxSize());
    assertThat(s1.getRandao_mixes().getMaxSize()).isNotEqualTo(s2.getRandao_mixes().getMaxSize());
    assertThat(s1.getSlashings().getMaxSize()).isNotEqualTo(s2.getSlashings().getMaxSize());
    assertThat(s1.getPrevious_epoch_attestations().getMaxSize())
        .isNotEqualTo(s2.getPrevious_epoch_attestations().getMaxSize());
    assertThat(s1.getCurrent_epoch_attestations().getMaxSize())
        .isNotEqualTo(s2.getCurrent_epoch_attestations().getMaxSize());
  }

  @Test
  void roundTripViaSsz() {
    BeaconState beaconState = dataStructureUtil.randomBeaconState();
    Bytes bytes = beaconState.sszSerialize();
    BeaconState state = schemaDefinitions.getBeaconStateSchema().sszDeserialize(bytes);
    assertEquals(beaconState, state);
  }
}
