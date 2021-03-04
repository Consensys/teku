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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateInvariants.GENESIS_TIME_FIELD;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateInvariants.GENESIS_VALIDATORS_ROOT_FIELD;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateInvariants.SLOT_FIELD;

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
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.genesis.BeaconStateSchemaGenesis;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.backing.SszTestUtils;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.sos.SszField;

@ExtendWith(BouncyCastleExtension.class)
public abstract class AbstractBeaconStateSchemaTest<
    T extends BeaconState, TMutable extends MutableBeaconState> {

  private final Spec spec = SpecFactory.createMinimal();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecConstants genesisConstants = spec.getGenesisSpecConstants();
  private final BeaconStateSchema<T, TMutable> schema = getSchema(genesisConstants);

  protected abstract BeaconStateSchema<T, TMutable> getSchema(final SpecConstants specConstants);

  protected abstract BeaconStateSchema<T, TMutable> createSchema(final List<SszField> fields);

  @Test
  void vectorLengthsTest() {
    List<Integer> vectorLengths =
        List.of(
            genesisConstants.getSlotsPerHistoricalRoot(),
            genesisConstants.getSlotsPerHistoricalRoot(),
            genesisConstants.getEpochsPerHistoricalVector(),
            genesisConstants.getEpochsPerSlashingsVector(),
            genesisConstants.getJustificationBitsLength());
    assertEquals(vectorLengths, SszTestUtils.getVectorLengths(schema));
  }

  @Test
  public void changeSpecConstantsTest() {
    final Spec standardSpec = SpecFactory.createMinimal();
    final SpecConstants modifiedConstants =
        TestConstantsLoader.loadConstantsBuilder("minimal")
            .slotsPerHistoricalRoot(123)
            .historicalRootsLimit(123)
            .epochsPerEth1VotingPeriod(123)
            .validatorRegistryLimit(123L)
            .epochsPerHistoricalVector(123)
            .epochsPerSlashingsVector(123)
            .maxAttestations(123)
            .build();

    BeaconState s1 = getSchema(modifiedConstants).createEmpty();
    BeaconState s2 = getSchema(standardSpec.getGenesisSpecConstants()).createEmpty();

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
    BeaconState state = schema.sszDeserialize(bytes);
    assertEquals(beaconState, state);
  }

  @Test
  public void create_compareDifferentSpecs() {
    final BeaconStateSchema<BeaconState, MutableBeaconState> minimalState =
        BeaconStateSchemaGenesis.create(SpecFactory.createMinimal().getGenesisSpecConstants());
    final BeaconStateSchema<BeaconState, MutableBeaconState> mainnetState =
        BeaconStateSchemaGenesis.create(SpecFactory.createMainnet().getGenesisSpecConstants());

    assertThat(minimalState).isNotEqualTo(mainnetState);
  }

  @Test
  public void shouldValidateFieldsAreOrdered() {
    assertThatThrownBy(
            () ->
                createSchema(
                    List.of(GENESIS_TIME_FIELD, SLOT_FIELD, GENESIS_VALIDATORS_ROOT_FIELD)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "fields must be ordered and contiguous.  Encountered unexpected index 2 at fields element 1");
  }

  @Test
  public void shouldValidateFieldCount() {
    assertThatThrownBy(
            () -> createSchema(List.of(GENESIS_TIME_FIELD, GENESIS_VALIDATORS_ROOT_FIELD)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Must provide at least 3 fields");
  }

  @Test
  public void shouldValidateInvariantFields() {
    final SszField randomField =
        new SszField(
            2, "random", () -> SszVectorSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 10));
    assertThatThrownBy(
            () ->
                createSchema(
                    List.of(GENESIS_TIME_FIELD, GENESIS_VALIDATORS_ROOT_FIELD, randomField)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected invariant field 'SLOT' at index 2, but got 'random'");
  }

  @Test
  void simpleMutableBeaconStateTest() {
    UInt64 val1 = UInt64.valueOf(0x3333);
    BeaconState stateR1 =
        schema
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
}
