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

package tech.pegasys.teku.spec.schemas.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.spec.schemas.registry.BaseSchemaProvider.constantProviderBuilder;
import static tech.pegasys.teku.spec.schemas.registry.BaseSchemaProvider.variableProviderBuilder;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

class BaseSchemaProviderTest {
  @SuppressWarnings("unchecked")
  private static final SchemaId<String> STRING_SCHEMA_ID = mock(SchemaId.class);

  private final SchemaRegistry mockRegistry = mock(SchemaRegistry.class);

  @Test
  void shouldSupportContinuousUntilHighestMilestone() {
    final SchemaProvider<?> provider =
        variableProviderBuilder(STRING_SCHEMA_ID)
            .withCreator(ALTAIR, (r, c) -> "TestSchemaAltair")
            .withCreator(BELLATRIX, (r, c) -> "TestSchemaBellatrix")
            .build();

    assertEquals(ALTAIR, provider.getEffectiveMilestone(ALTAIR));
    assertEquals(BELLATRIX, provider.getEffectiveMilestone(BELLATRIX));

    when(mockRegistry.getMilestone()).thenReturn(ALTAIR);
    assertEquals(provider.getSchema(mockRegistry), "TestSchemaAltair");

    when(mockRegistry.getMilestone()).thenReturn(BELLATRIX);
    assertEquals(provider.getSchema(mockRegistry), "TestSchemaBellatrix");

    assertThat(provider.getSupportedMilestones())
        .containsAll(SpecMilestone.getAllMilestonesFrom(ALTAIR));
  }

  @Test
  void shouldSupportContinuousConstantWithUntil() {
    final SchemaProvider<?> provider =
        constantProviderBuilder(STRING_SCHEMA_ID)
            .withCreator(PHASE0, (r, c) -> "TestSchemaPhase0")
            .withCreator(BELLATRIX, (r, c) -> "TestSchemaBellatrix")
            .until(CAPELLA)
            .build();

    assertEquals(PHASE0, provider.getEffectiveMilestone(PHASE0));
    assertEquals(PHASE0, provider.getEffectiveMilestone(ALTAIR));
    assertEquals(BELLATRIX, provider.getEffectiveMilestone(BELLATRIX));
    assertEquals(BELLATRIX, provider.getEffectiveMilestone(CAPELLA));

    when(mockRegistry.getMilestone()).thenReturn(PHASE0);
    assertEquals(provider.getSchema(mockRegistry), "TestSchemaPhase0");

    when(mockRegistry.getMilestone()).thenReturn(ALTAIR);
    assertEquals(provider.getSchema(mockRegistry), "TestSchemaPhase0");

    when(mockRegistry.getMilestone()).thenReturn(BELLATRIX);
    assertEquals(provider.getSchema(mockRegistry), "TestSchemaBellatrix");

    when(mockRegistry.getMilestone()).thenReturn(CAPELLA);
    assertEquals(provider.getSchema(mockRegistry), "TestSchemaBellatrix");

    assertThat(provider.getSupportedMilestones())
        .containsExactly(PHASE0, ALTAIR, BELLATRIX, CAPELLA);
  }

  @Test
  void shouldSupportContinuousDefaultVariable() {
    final SchemaProvider<?> provider =
        variableProviderBuilder(STRING_SCHEMA_ID)
            .withCreator(PHASE0, (r, c) -> "TestSchema" + r.getMilestone())
            .until(CAPELLA)
            .build();

    // variable has effective milestone always equal to the milestone
    SpecMilestone.getMilestonesUpTo(CAPELLA)
        .forEach(milestone -> assertEquals(milestone, provider.getEffectiveMilestone(milestone)));

    when(mockRegistry.getMilestone()).thenReturn(PHASE0);
    assertEquals(provider.getSchema(mockRegistry), "TestSchemaPHASE0");

    when(mockRegistry.getMilestone()).thenReturn(ALTAIR);
    assertEquals(provider.getSchema(mockRegistry), "TestSchemaALTAIR");

    when(mockRegistry.getMilestone()).thenReturn(BELLATRIX);
    assertEquals(provider.getSchema(mockRegistry), "TestSchemaBELLATRIX");

    when(mockRegistry.getMilestone()).thenReturn(CAPELLA);
    assertEquals(provider.getSchema(mockRegistry), "TestSchemaCAPELLA");

    assertThat(provider.getSupportedMilestones())
        .containsExactly(PHASE0, ALTAIR, BELLATRIX, CAPELLA);
  }

  @Test
  void shouldThrowWhenNoCreators() {
    assertThatThrownBy(() -> variableProviderBuilder(STRING_SCHEMA_ID).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("There should be at least 1 creator");
  }

  @Test
  void shouldThrowWhenAskingForAnUnsupportedMilestone() {
    final SchemaProvider<?> provider =
        variableProviderBuilder(STRING_SCHEMA_ID)
            .withCreator(ALTAIR, (r, c) -> "TestSchemaAltair")
            .until(ALTAIR)
            .build();

    when(mockRegistry.getMilestone()).thenReturn(DENEB);

    assertThatThrownBy(() -> provider.getSchema(mockRegistry))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("It is not supposed to create a specific version for DENEB");
  }

  @Test
  void shouldThrowWhenNotAscendingMilestones() {
    assertThatThrownBy(
            () ->
                variableProviderBuilder(STRING_SCHEMA_ID)
                    .withCreator(PHASE0, (r, c) -> "TestSchema")
                    .withCreator(PHASE0, (r, c) -> "TestSchema"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Creator's milestones must added in strict ascending order");

    assertThatThrownBy(
            () ->
                variableProviderBuilder(STRING_SCHEMA_ID)
                    .withCreator(ALTAIR, (r, c) -> "TestSchema")
                    .withCreator(PHASE0, (r, c) -> "TestSchema"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Creator's milestones must added in strict ascending order");
  }

  @Test
  void shouldThrowWhenWithUntilIsPriorToMilestone() {
    assertThatThrownBy(
            () ->
                variableProviderBuilder(STRING_SCHEMA_ID)
                    .withCreator(PHASE0, (r, c) -> "TestSchema")
                    .withCreator(CAPELLA, (r, c) -> "TestSchema")
                    .until(ALTAIR)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("until must be greater or equal than last creator milestone");
  }
}
