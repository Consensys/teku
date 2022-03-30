/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;

public class SchemaDefinitionCacheTest {

  @ParameterizedTest
  @EnumSource(SpecMilestone.class)
  void shouldGetSchemasForAllMilestones(final SpecMilestone specMilestone) {
    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final SchemaDefinitionCache cache = new SchemaDefinitionCache(spec);
    assertThat(cache.getSchemaDefinition(specMilestone)).isNotNull();
  }

  @Test
  void shouldUseExistingSchemaDefinitionIfPossible() {
    final Spec spec = mock(Spec.class);
    when(spec.forMilestone(SpecMilestone.ALTAIR))
        .thenReturn(TestSpecFactory.createMinimalAltair().forMilestone(SpecMilestone.ALTAIR));
    final SchemaDefinitionCache cache = new SchemaDefinitionCache(spec);
    assertThat(cache.getSchemaDefinition(SpecMilestone.ALTAIR)).isNotNull();
    verify(spec).forMilestone(eq(SpecMilestone.ALTAIR));

    assertThat(cache.getSchemaDefinition(SpecMilestone.ALTAIR)).isNotNull();
    verifyNoMoreInteractions(spec);
  }

  @ParameterizedTest
  @EnumSource(SpecMilestone.class)
  void shouldGetSpecMilestoneFromSpecObject(final SpecMilestone specMilestone) {
    final Spec spec = TestSpecFactory.createMinimal(specMilestone);
    final SchemaDefinitionCache cache = new SchemaDefinitionCache(spec);
    assertThat(cache.milestoneAtSlot(UInt64.ONE)).isSameAs(specMilestone);
    assertThat(cache.getSchemaDefinition(specMilestone))
        .isSameAs(spec.forMilestone(specMilestone).getSchemaDefinitions());
  }

  @Test
  void shouldCreateSchemaIfMilestoneRequired() {
    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final SchemaDefinitionCache cache = new SchemaDefinitionCache(spec);
    assertThat(spec.forMilestone(SpecMilestone.BELLATRIX)).isNull();
    assertThat(cache.getSchemaDefinition(SpecMilestone.BELLATRIX)).isNotNull();
  }
}
