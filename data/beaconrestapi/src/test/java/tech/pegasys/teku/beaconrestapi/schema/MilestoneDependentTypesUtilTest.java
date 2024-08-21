/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.beaconrestapi.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class MilestoneDependentTypesUtilTest {
  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final SchemaDefinitionCache cache = new SchemaDefinitionCache(spec);

  @Test
  void headerSelector_UsesConsensusVersionPhase0() {
    final DeserializableTypeDefinition<?> typeDefinition =
        MilestoneDependentTypesUtil.headerBasedSelector(
            Map.of(HEADER_CONSENSUS_VERSION, SpecMilestone.PHASE0.toString()),
            cache,
            SchemaDefinitions::getAttestationSchema);
    assertThat(typeDefinition.getTypeName()).contains("AttestationPhase0");
  }

  @Test
  void headerSelector_UsesConsensusVersionDeneb() {
    final DeserializableTypeDefinition<?> typeDefinition =
        MilestoneDependentTypesUtil.headerBasedSelector(
            Map.of(HEADER_CONSENSUS_VERSION, SpecMilestone.DENEB.toString()),
            cache,
            SchemaDefinitions::getAttestationSchema);
    assertThat(typeDefinition.getTypeName()).contains("AttestationPhase0");
  }

  @Test
  void headerSelector_UsesConsensusVersionElectra() {
    final DeserializableTypeDefinition<?> typeDefinition =
        MilestoneDependentTypesUtil.headerBasedSelector(
            Map.of(HEADER_CONSENSUS_VERSION, SpecMilestone.ELECTRA.toString()),
            cache,
            SchemaDefinitions::getAttestationSchema);
    assertThat(typeDefinition.getTypeName()).contains("AttestationElectra");
  }

  @Test
  void headerSelector_errorsWhenInvalid() {
    assertThatThrownBy(
            () ->
                MilestoneDependentTypesUtil.headerBasedSelector(
                    Map.of(HEADER_CONSENSUS_VERSION, "invalid"),
                    cache,
                    SchemaDefinitions::getAttestationSchema))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Invalid value for (Eth-Consensus-Version) header: invalid");
  }

  @Test
  void headerSelector_errorsWhenMissing() {
    assertThatThrownBy(
            () ->
                MilestoneDependentTypesUtil.headerBasedSelector(
                    Collections.emptyMap(), cache, SchemaDefinitions::getAttestationSchema))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Missing required header value for (Eth-Consensus-Version)");
  }
}
