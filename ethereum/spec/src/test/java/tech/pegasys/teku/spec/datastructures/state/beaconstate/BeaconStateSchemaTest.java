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

package tech.pegasys.teku.spec.datastructures.state.beaconstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateInvariants.GENESIS_TIME_FIELD;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateInvariants.GENESIS_VALIDATORS_ROOT_FIELD;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateInvariants.SLOT_FIELD;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.sos.SszField;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.SpecDependent;

public class BeaconStateSchemaTest {

  public void tearDown() {
    Constants.setConstants("minimal");
    SpecDependent.resetAll();
  }

  @Test
  public void create_minimal() {
    final Spec spec = setupMinimalSpec();
    final BeaconStateSchema specA = BeaconStateSchema.create(spec.getGenesisSpecConstants());
    final BeaconStateSchema specB = BeaconStateSchema.create();

    assertThat(specA).isEqualTo(specB);
  }

  @Test
  public void create_mainnet() {
    final Spec spec = setupMainnetSpec();
    final BeaconStateSchema specA = BeaconStateSchema.create(spec.getGenesisSpecConstants());
    final BeaconStateSchema specB = BeaconStateSchema.create();

    assertThat(specA).isEqualTo(specB);
  }

  @Test
  public void shouldValidateFieldsAreOrdered() {
    assertThatThrownBy(
            () ->
                new BeaconStateSchema(
                    List.of(GENESIS_TIME_FIELD, SLOT_FIELD, GENESIS_VALIDATORS_ROOT_FIELD)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "fields must be ordered and contiguous.  Encountered unexpected index 2 at fields element 1");
  }

  @Test
  public void shouldValidateFieldCount() {
    assertThatThrownBy(
            () -> new BeaconStateSchema(List.of(GENESIS_TIME_FIELD, GENESIS_VALIDATORS_ROOT_FIELD)))
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
                new BeaconStateSchema(
                    List.of(GENESIS_TIME_FIELD, GENESIS_VALIDATORS_ROOT_FIELD, randomField)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected invariant field 'SLOT' at index 2, but got 'random'");
  }

  private Spec setupMinimalSpec() {
    Constants.setConstants("minimal");
    SpecDependent.resetAll();
    return SpecFactory.createMinimal();
  }

  private Spec setupMainnetSpec() {
    Constants.setConstants("mainnet");
    SpecDependent.resetAll();
    return SpecFactory.createMainnet();
  }
}
