/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistryBuilder;

class SpecVersionTest {
  private final SchemaRegistryBuilder schemaRegistryBuilder = SchemaRegistryBuilder.create();
  private final SpecConfig minimalConfig =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName());

  @ParameterizedTest
  @EnumSource(SpecMilestone.class)
  void shouldCreateSpec(final SpecMilestone milestone) {
    // make intelliJ happy
    SpecVersion expectedVersion = null;
    Optional<SpecVersion> actualVersion = Optional.empty();

    switch (milestone) {
      case PHASE0 -> {
        expectedVersion = SpecVersion.createPhase0(minimalConfig, schemaRegistryBuilder);
        actualVersion =
            SpecVersion.create(SpecMilestone.PHASE0, minimalConfig, schemaRegistryBuilder);
      }

      case ALTAIR -> {
        expectedVersion =
            SpecVersion.createAltair(
                SpecConfigAltair.required(minimalConfig), schemaRegistryBuilder);
        actualVersion =
            SpecVersion.create(SpecMilestone.ALTAIR, minimalConfig, schemaRegistryBuilder);
      }
      case BELLATRIX -> {
        expectedVersion =
            SpecVersion.createBellatrix(
                SpecConfigBellatrix.required(minimalConfig), schemaRegistryBuilder);
        actualVersion =
            SpecVersion.create(SpecMilestone.BELLATRIX, minimalConfig, schemaRegistryBuilder);
      }
      case CAPELLA -> {
        expectedVersion =
            SpecVersion.createCapella(
                SpecConfigCapella.required(minimalConfig), schemaRegistryBuilder);
        actualVersion =
            SpecVersion.create(SpecMilestone.CAPELLA, minimalConfig, schemaRegistryBuilder);
      }
      case DENEB -> {
        expectedVersion =
            SpecVersion.createDeneb(SpecConfigDeneb.required(minimalConfig), schemaRegistryBuilder);
        actualVersion =
            SpecVersion.create(SpecMilestone.DENEB, minimalConfig, schemaRegistryBuilder);
      }
      case ELECTRA -> {
        expectedVersion =
            SpecVersion.createElectra(
                SpecConfigElectra.required(minimalConfig), schemaRegistryBuilder);
        actualVersion =
            SpecVersion.create(SpecMilestone.ELECTRA, minimalConfig, schemaRegistryBuilder);
      }
    }

    assertThat(actualVersion).isPresent();
    assertThat(actualVersion.get().getMilestone()).isEqualTo(milestone);
    assertThat(actualVersion.get().getSchemaDefinitions())
        .hasSameClassAs(expectedVersion.getSchemaDefinitions());
    assertThat(actualVersion.get().getSchemaDefinitions().getSchemaRegistry().getMilestone())
        .isSameAs(milestone);
  }
}
