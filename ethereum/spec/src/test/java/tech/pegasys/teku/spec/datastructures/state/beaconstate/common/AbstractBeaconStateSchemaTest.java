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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.GENESIS_TIME_FIELD;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.GENESIS_VALIDATORS_ROOT_FIELD;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.SLOT_FIELD;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.TestConfigLoader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.SszTestUtils;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.sos.SszField;
import tech.pegasys.teku.ssz.tree.TreeNode;

@ExtendWith(BouncyCastleExtension.class)
public abstract class AbstractBeaconStateSchemaTest<
    T extends BeaconState, TMutable extends MutableBeaconState> {

  private final Spec spec = SpecFactory.createMinimal();
  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecConfig genesisConfig = spec.getGenesisSpecConfig();
  private final BeaconStateSchema<T, TMutable> schema = getSchema(genesisConfig);

  protected abstract BeaconStateSchema<T, TMutable> getSchema(final SpecConfig specConfig);

  protected abstract T randomState();

  @Test
  void vectorLengthsTest() {
    List<Integer> vectorLengths =
        List.of(
            genesisConfig.getSlotsPerHistoricalRoot(),
            genesisConfig.getSlotsPerHistoricalRoot(),
            genesisConfig.getEpochsPerHistoricalVector(),
            genesisConfig.getEpochsPerSlashingsVector(),
            genesisConfig.getJustificationBitsLength());
    assertEquals(vectorLengths, SszTestUtils.getVectorLengths(schema));
  }

  @Test
  public void changeSpecConfigTest() {
    final Spec standardSpec = SpecFactory.createMinimal();
    final SpecConfig modifiedConfig =
        TestConfigLoader.loadConfig(
            "minimal",
            b ->
                b.slotsPerHistoricalRoot(123)
                    .historicalRootsLimit(123)
                    .epochsPerEth1VotingPeriod(123)
                    .validatorRegistryLimit(123L)
                    .epochsPerHistoricalVector(123)
                    .epochsPerSlashingsVector(123)
                    .maxAttestations(123));

    BeaconState s1 = getSchema(modifiedConfig).createEmpty();
    BeaconState s2 = getSchema(standardSpec.getGenesisSpecConfig()).createEmpty();

    assertThat(s1.getBlock_roots().getSchema()).isNotEqualTo(s2.getBlock_roots().getSchema());
    assertThat(s1.getState_roots().getSchema()).isNotEqualTo(s2.getState_roots().getSchema());
    assertThat(s1.getHistorical_roots().getSchema())
        .isNotEqualTo(s2.getHistorical_roots().getSchema());
    assertThat(s1.getEth1_data_votes().getSchema())
        .isNotEqualTo(s2.getEth1_data_votes().getSchema());
    assertThat(s1.getValidators().getSchema()).isNotEqualTo(s2.getValidators().getSchema());
    assertThat(s1.getBalances().getSchema()).isNotEqualTo(s2.getBalances().getSchema());
    assertThat(s1.getRandao_mixes().getSchema()).isNotEqualTo(s2.getRandao_mixes().getSchema());
    assertThat(s1.getSlashings().getSchema()).isNotEqualTo(s2.getSlashings().getSchema());
  }

  @Test
  void roundTripViaSsz() {
    // TODO - generate random version-specific state
    BeaconState beaconState = randomState();
    Bytes bytes = beaconState.sszSerialize();
    BeaconState state = schema.sszDeserialize(bytes);
    assertEquals(beaconState, state);
  }

  @Test
  public void create_compareDifferentSpecs() {
    final BeaconStateSchema<T, TMutable> minimalState =
        getSchema(SpecFactory.createMinimal().getGenesisSpecConfig());
    final BeaconStateSchema<T, TMutable> mainnetState =
        getSchema(SpecFactory.createMainnet().getGenesisSpecConfig());

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

  private BeaconStateSchema<BeaconState, MutableBeaconState> createSchema(
      final List<SszField> fields) {
    return new TestBeaconStateSchema(fields);
  }

  private static class TestBeaconStateSchema
      extends AbstractBeaconStateSchema<BeaconState, MutableBeaconState> {

    TestBeaconStateSchema(final List<SszField> allFields) {
      super("TestSchema", allFields);
    }

    @Override
    public MutableBeaconState createBuilder() {
      return null;
    }

    @Override
    public BeaconState createEmpty() {
      return null;
    }

    @Override
    public BeaconState createFromBackingNode(final TreeNode node) {
      return null;
    }
  }
}
