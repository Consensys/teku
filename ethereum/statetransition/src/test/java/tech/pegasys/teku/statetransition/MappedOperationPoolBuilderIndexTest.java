/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.OperationValidator;

/**
 * Verifies that MappedOperationPool handles builder-indexed voluntary exits (ePBS/Gloas) without
 * throwing ArithmeticException. Builder indices have BUILDER_INDEX_FLAG (2^40) ORed in, making them
 * exceed Integer.MAX_VALUE.
 */
public class MappedOperationPoolBuilderIndexTest {

  private static final UInt64 BUILDER_INDEX_FLAG = SpecConfigGloas.BUILDER_INDEX_FLAG;
  // A typical builder index: flag | actual_builder_index_0
  private static final UInt64 BUILDER_VALIDATOR_INDEX = BUILDER_INDEX_FLAG;

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @SuppressWarnings("unchecked")
  private final OperationValidator<SignedVoluntaryExit> validator = mock(OperationValidator.class);

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1_000_000);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final BeaconState state = mock(BeaconState.class);

  private final OperationPool<SignedVoluntaryExit> pool =
      new MappedOperationPool<>(
          "VoluntaryExitPool",
          metricsSystem,
          slot ->
              (SszListSchema<SignedVoluntaryExit, ?>)
                  spec.atSlot(slot)
                      .getSchemaDefinitions()
                      .getBeaconBlockBodySchema()
                      .getVoluntaryExitsSchema(),
          validator,
          asyncRunner,
          timeProvider);

  @BeforeEach
  void setUp() {
    when(state.getSlot()).thenReturn(UInt64.ZERO);
  }

  private SignedVoluntaryExit builderExit() {
    return dataStructureUtil.randomSignedVoluntaryExit(BUILDER_VALIDATOR_INDEX);
  }

  @Test
  void addLocal_withBuilderIndex_doesNotThrow() {
    when(validator.validateForGossip(any())).thenReturn(SafeFuture.completedFuture(ACCEPT));

    final SafeFuture<InternalValidationResult> result = pool.addLocal(builderExit());

    assertThat(result).isCompleted();
    assertThat(result.join().isAccept()).isTrue();
    assertThat(pool.size()).isEqualTo(1);
  }

  @Test
  void addRemote_withBuilderIndex_doesNotThrow() {
    when(validator.validateForGossip(any())).thenReturn(SafeFuture.completedFuture(ACCEPT));

    final SafeFuture<InternalValidationResult> result =
        pool.addRemote(builderExit(), Optional.empty());

    assertThat(result).isCompleted();
    assertThat(result.join().isAccept()).isTrue();
    assertThat(pool.size()).isEqualTo(1);
  }

  @Test
  void removeAll_withBuilderIndex_doesNotThrow() {
    when(validator.validateForGossip(any())).thenReturn(SafeFuture.completedFuture(ACCEPT));
    final SignedVoluntaryExit exit = builderExit();
    assertThat(pool.addLocal(exit).join().isAccept()).isTrue();

    final SszListSchema<SignedVoluntaryExit, ?> schema =
        (SszListSchema<SignedVoluntaryExit, ?>)
            spec.atSlot(UInt64.ZERO)
                .getSchemaDefinitions()
                .getBeaconBlockBodySchema()
                .getVoluntaryExitsSchema();
    pool.removeAll(schema.createFromElements(List.of(exit)));

    assertThat(pool.size()).isEqualTo(0);
  }

  @Test
  void getItemsForBlock_withBuilderIndex_doesNotThrow() {
    when(validator.validateForGossip(any())).thenReturn(SafeFuture.completedFuture(ACCEPT));
    when(validator.validateForBlockInclusion(any(), any())).thenReturn(Optional.empty());
    final SignedVoluntaryExit exit = builderExit();
    assertThat(pool.addLocal(exit).join().isAccept()).isTrue();

    assertThat(pool.getItemsForBlock(state)).containsExactly(exit);
  }

  @Test
  void duplicateBuilderIndex_isRejectedWithIgnore() {
    when(validator.validateForGossip(any())).thenReturn(SafeFuture.completedFuture(ACCEPT));
    final SignedVoluntaryExit exit = builderExit();
    assertThat(pool.addLocal(exit).join().isAccept()).isTrue();

    // Adding the same builder index again must be silently ignored, not throw
    final InternalValidationResult second = pool.addLocal(exit).join();
    assertThat(second.isIgnore()).isTrue();
    assertThat(pool.size()).isEqualTo(1);
  }
}
