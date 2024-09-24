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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

class BaseSchemaProviderTest {
  @SuppressWarnings("unchecked")
  private static final SchemaId<String> STRING_SCHEMA_ID = mock(SchemaId.class);

  private final TestSchemaProvider provider = new TestSchemaProvider();
  private final SchemaRegistry mockRegistry = mock(SchemaRegistry.class);

  @Test
  void shouldGetEffectiveMilestone() {
    provider.addMilestoneMapping(PHASE0, ALTAIR);
    assertEquals(PHASE0, provider.getEffectiveMilestone(PHASE0));
    assertEquals(PHASE0, provider.getEffectiveMilestone(ALTAIR));
    assertEquals(BELLATRIX, provider.getEffectiveMilestone(BELLATRIX));
  }

  @Test
  void shouldGetSchema() {
    when(mockRegistry.getMilestone()).thenReturn(PHASE0);
    String result = provider.getSchema(mockRegistry);
    assertEquals("TestSchema", result);
  }

  @Test
  void shouldGetNonOverlappingVersionMappings() {
    provider.addMilestoneMapping(PHASE0, ALTAIR);
    provider.addMilestoneMapping(BELLATRIX, CAPELLA);

    assertEquals(PHASE0, provider.getEffectiveMilestone(PHASE0));
    assertEquals(PHASE0, provider.getEffectiveMilestone(ALTAIR));
    assertEquals(BELLATRIX, provider.getEffectiveMilestone(BELLATRIX));
    assertEquals(BELLATRIX, provider.getEffectiveMilestone(CAPELLA));
  }

  @Test
  void testOverlappingVersionMappingsThrowsException() {
    provider.addMilestoneMapping(PHASE0, ALTAIR);

    assertThatThrownBy(() -> provider.addMilestoneMapping(ALTAIR, BELLATRIX))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Milestone ALTAIR is already mapped to PHASE0");
  }

  private static class TestSchemaProvider extends AbstractSchemaProvider<String> {
    TestSchemaProvider() {
      super(STRING_SCHEMA_ID);
    }

    @Override
    protected String createSchema(
        final SchemaRegistry registry,
        final SpecMilestone effectiveMilestone,
        final SpecConfig specConfig) {
      return "TestSchema";
    }

    @Override
    public Set<SpecMilestone> getSupportedMilestones() {
      return EnumSet.allOf(SpecMilestone.class);
    }
  }
}
