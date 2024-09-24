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
import org.junit.jupiter.api.Test;
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
  private final SpecConfigAltair minimalConfig =
      SpecConfigAltair.required(SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName()));

  @Test
  void shouldCreatePhase0Spec() {
    final SpecVersion expectedVersion =
        SpecVersion.createPhase0(minimalConfig, schemaRegistryBuilder);
    final Optional<SpecVersion> actualVersion =
        SpecVersion.create(SpecMilestone.PHASE0, minimalConfig, schemaRegistryBuilder);
    assertThat(actualVersion).isPresent();
    assertThat(actualVersion.get().getMilestone()).isEqualTo(SpecMilestone.PHASE0);
    assertThat(actualVersion.get().getSchemaDefinitions())
        .hasSameClassAs(expectedVersion.getSchemaDefinitions());
  }

  @Test
  void shouldCreateAltairSpec() {
    final SpecConfigAltair altairSpecConfig = SpecConfigAltair.required(minimalConfig);
    final SpecVersion expectedVersion =
        SpecVersion.createAltair(altairSpecConfig, schemaRegistryBuilder);
    final Optional<SpecVersion> actualVersion =
        SpecVersion.create(SpecMilestone.ALTAIR, minimalConfig, schemaRegistryBuilder);
    assertThat(actualVersion).isPresent();
    assertThat(actualVersion.get().getMilestone()).isEqualTo(SpecMilestone.ALTAIR);
    assertThat(actualVersion.get().getSchemaDefinitions())
        .hasSameClassAs(expectedVersion.getSchemaDefinitions());
  }

  @Test
  void shouldCreateBellatrixSpec() {
    final SpecConfigBellatrix bellatrixSpecConfig = SpecConfigBellatrix.required(minimalConfig);
    final SpecVersion expectedVersion =
        SpecVersion.createBellatrix(bellatrixSpecConfig, schemaRegistryBuilder);
    final Optional<SpecVersion> actualVersion =
        SpecVersion.create(SpecMilestone.BELLATRIX, minimalConfig, schemaRegistryBuilder);
    assertThat(actualVersion).isPresent();
    assertThat(actualVersion.get().getMilestone()).isEqualTo(SpecMilestone.BELLATRIX);
    assertThat(actualVersion.get().getSchemaDefinitions())
        .hasSameClassAs(expectedVersion.getSchemaDefinitions());
  }

  @Test
  void shouldCreateCapellaSpec() {
    final SpecConfigCapella capellaSpecConfig = SpecConfigCapella.required(minimalConfig);
    final SpecVersion expectedVersion =
        SpecVersion.createCapella(capellaSpecConfig, schemaRegistryBuilder);
    final Optional<SpecVersion> actualVersion =
        SpecVersion.create(SpecMilestone.CAPELLA, minimalConfig, schemaRegistryBuilder);
    assertThat(actualVersion).isPresent();
    assertThat(actualVersion.get().getMilestone()).isEqualTo(SpecMilestone.CAPELLA);
    assertThat(actualVersion.get().getSchemaDefinitions())
        .hasSameClassAs(expectedVersion.getSchemaDefinitions());
  }

  @Test
  void shouldCreateDenebSpec() {
    final SpecConfigDeneb denebSpecConfig = SpecConfigDeneb.required(minimalConfig);
    final SpecVersion expectedVersion =
        SpecVersion.createDeneb(denebSpecConfig, schemaRegistryBuilder);
    final Optional<SpecVersion> actualVersion =
        SpecVersion.create(SpecMilestone.DENEB, minimalConfig, schemaRegistryBuilder);
    assertThat(actualVersion).isPresent();
    assertThat(actualVersion.get().getMilestone()).isEqualTo(SpecMilestone.DENEB);
    assertThat(actualVersion.get().getSchemaDefinitions())
        .hasSameClassAs(expectedVersion.getSchemaDefinitions());
  }

  @Test
  void shouldCreateElectraSpec() {
    final SpecConfigElectra electraSpecConfig = SpecConfigElectra.required(minimalConfig);
    final SpecVersion expectedVersion =
        SpecVersion.createElectra(electraSpecConfig, schemaRegistryBuilder);
    final Optional<SpecVersion> actualVersion =
        SpecVersion.create(SpecMilestone.ELECTRA, minimalConfig, schemaRegistryBuilder);
    assertThat(actualVersion).isPresent();
    assertThat(actualVersion.get().getMilestone()).isEqualTo(SpecMilestone.ELECTRA);
    assertThat(actualVersion.get().getSchemaDefinitions())
        .hasSameClassAs(expectedVersion.getSchemaDefinitions());
  }
}
