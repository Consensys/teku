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
import static tech.pegasys.teku.spec.schemas.registry.AbstractSchemaProvider.schemaCreator;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

class BaseSchemaProviderTest {
  @SuppressWarnings("unchecked")
  private static final SchemaId<String> STRING_SCHEMA_ID = mock(SchemaId.class);

  private final SchemaRegistry mockRegistry = mock(SchemaRegistry.class);

  @Test
  void shouldSupportContinuousWithoutUntil() {
    final TestSchemaProvider provider =
        new TestSchemaProvider(
            schemaCreator(ALTAIR, (r, c) -> "TestSchemaAltair"),
            schemaCreator(BELLATRIX, (r, c) -> "TestSchemaBellatrix"));
    assertEquals(ALTAIR, provider.getEffectiveMilestone(ALTAIR));
    assertEquals(BELLATRIX, provider.getEffectiveMilestone(BELLATRIX));

    when(mockRegistry.getMilestone()).thenReturn(ALTAIR);
    assertEquals(provider.getSchema(mockRegistry), "TestSchemaAltair");

    when(mockRegistry.getMilestone()).thenReturn(BELLATRIX);
    assertEquals(provider.getSchema(mockRegistry), "TestSchemaBellatrix");

    assertThat(provider.getSupportedMilestones()).containsExactly(ALTAIR, BELLATRIX);
  }

  @Test
  void shouldSupportContinuousWithUntil() {
    final TestSchemaProvider provider =
        new TestSchemaProvider(
            schemaCreator(PHASE0, ALTAIR, (r, c) -> "TestSchemaPhase0"),
            schemaCreator(BELLATRIX, CAPELLA, (r, c) -> "TestSchemaBellatrix"));

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
  void shouldThrowWhenNoCreators() {
    assertThatThrownBy(TestSchemaProvider::new)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("There should be at least 1 creator");
  }

  @Test
  void shouldThrowWhenNotAscendingMilestonesWithUntil() {
    assertThatThrownBy(
            () ->
                new TestSchemaProvider(
                    schemaCreator(PHASE0, ALTAIR, (r, c) -> "TestSchema"),
                    schemaCreator(ALTAIR, (r, c) -> "TestSchema")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Milestones ascending ordering error: from ALTAIR to ALTAIR");

    assertThatThrownBy(
            () ->
                new TestSchemaProvider(
                    schemaCreator(ALTAIR, BELLATRIX, (r, c) -> "TestSchema"),
                    schemaCreator(PHASE0, (r, c) -> "TestSchema")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Milestones ascending ordering error: from BELLATRIX to PHASE0");
  }

  @Test
  void shouldThrowWhenNotAscendingMilestonesWithoutUntil() {
    assertThatThrownBy(
            () ->
                new TestSchemaProvider(
                    schemaCreator(PHASE0, (r, c) -> "TestSchema"),
                    schemaCreator(PHASE0, (r, c) -> "TestSchema")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Milestones ascending ordering error: from PHASE0 to PHASE0");

    assertThatThrownBy(
            () ->
                new TestSchemaProvider(
                    schemaCreator(DENEB, (r, c) -> "TestSchema"),
                    schemaCreator(ALTAIR, (r, c) -> "TestSchema")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Milestones ascending ordering error: from DENEB to ALTAIR");
  }

  @Test
  void shouldThrowWhenDeclaringGaps() {
    assertThatThrownBy(
            () ->
                new TestSchemaProvider(
                    schemaCreator(PHASE0, (r, c) -> "TestSchema"),
                    schemaCreator(BELLATRIX, (r, c) -> "TestSchema")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Milestones gap detected: from PHASE0 to ALTAIR");
  }

  private static class TestSchemaProvider extends AbstractSchemaProvider<String> {
    @SafeVarargs
    TestSchemaProvider(final SchemaProviderCreator<String>... schemaProviderCreators) {
      super(STRING_SCHEMA_ID, schemaProviderCreators);
    }
  }
}
