/*
 * Copyright ConsenSys Software Inc., 2023
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
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodySchemaCapella;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.SignedBlsToExecutionChangeValidator;

public class BlsToExecutionOperationPoolTest {

  private final Spec spec = TestSpecFactory.createMinimalCapella();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Function<UInt64, BeaconBlockBodySchema<?>> beaconBlockSchemaSupplier =
      slot -> spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema();
  private final BeaconState state = mock(BeaconState.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final SignedBlsToExecutionChangeValidator validator =
      mock(SignedBlsToExecutionChangeValidator.class);
  private final Function<UInt64, SszListSchema<SignedBlsToExecutionChange, ?>>
      blsToExecutionSchemaSupplier =
          beaconBlockSchemaSupplier
              .andThen(BeaconBlockBodySchema::toVersionCapella)
              .andThen(Optional::orElseThrow)
              .andThen(BeaconBlockBodySchemaCapella::getBlsToExecutionChangesSchema);
  private final OperationPool<SignedBlsToExecutionChange> pool =
      new BlsToExecutionOperationPool(
          "BlsToExecutionOperationPool", metricsSystem, blsToExecutionSchemaSupplier, validator);

  @BeforeEach
  void init() {
    when(state.getSlot()).thenReturn(UInt64.ZERO);
  }

  @Test
  void emptyPoolShouldReturnEmptyList() {
    assertThat(pool.getItemsForBlock(state)).isEmpty();
  }

  @Test
  void shouldAddEntryToPool() {
    when(validator.validateForGossip(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    final SignedBlsToExecutionChange item = dataStructureUtil.randomSignedBlsToExecutionChange();
    final SafeFuture<?> future = pool.addLocal(item);
    assertThat(future.isCompletedNormally()).isTrue();
    assertThat(pool.size()).isEqualTo(1);
    assertThat(pool.getAll()).containsExactly(item);
  }

  @Test
  void shouldRejectDuplicateEntryFromPool() throws ExecutionException, InterruptedException {
    final SignedBlsToExecutionChange item = initPoolWithSingleItem();

    final SafeFuture<InternalValidationResult> future2 = pool.addLocal(item);
    assertThat(future2.get().code()).isEqualTo(InternalValidationResult.IGNORE.code());
  }

  @Test
  void shouldRemoveEntryFromPool() {
    final SignedBlsToExecutionChange item = initPoolWithSingleItem();
    pool.removeAll(
        blsToExecutionSchemaSupplier
            .apply(UInt64.ZERO)
            .createFromElements(
                List.of(item, dataStructureUtil.randomSignedBlsToExecutionChange(), item)));
    assertThat(pool.size()).isEqualTo(0);
  }

  @Test
  void shouldAddEntriesToPool() {
    final SignedBlsToExecutionChange item = initPoolWithSingleItem();
    final SignedBlsToExecutionChange item2 = dataStructureUtil.randomSignedBlsToExecutionChange();
    pool.addAll(
        blsToExecutionSchemaSupplier.apply(UInt64.ZERO).createFromElements(List.of(item, item2)));
    assertThat(pool.size()).isEqualTo(2);
    assertThat(pool.getAll()).containsExactlyInAnyOrder(item, item2);
  }

  private SignedBlsToExecutionChange initPoolWithSingleItem() {
    when(validator.validateForGossip(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    final SignedBlsToExecutionChange item = dataStructureUtil.randomSignedBlsToExecutionChange();
    final SafeFuture<InternalValidationResult> future = pool.addLocal(item);
    assertThat(future.isCompletedNormally()).isTrue();
    assertThat(pool.size()).isEqualTo(1);
    return item;
  }

  @Test
  void shouldAddMaxItemsToPool() {
    final SignedBlsToExecutionChange item = initPoolWithSingleItem();
    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForBlockInclusion(any(), any())).thenReturn(Optional.empty());
    final int maxBlsToExecutionChanges =
        spec.getGenesisSpecConfig().toVersionCapella().orElseThrow().getMaxBlsToExecutionChanges();
    while (pool.size() < maxBlsToExecutionChanges) {
      assertThat(pool.addLocal(dataStructureUtil.randomSignedBlsToExecutionChange())).isCompleted();
    }

    assertThat(pool.getItemsForBlock(state)).hasSize(maxBlsToExecutionChanges);
    assertThat(pool.size()).isEqualTo(maxBlsToExecutionChanges);
    assertThat(pool.getItemsForBlock(state)).contains(item);
  }

  @Test
  void shouldPruneFromPoolIfNoLongerValid() {
    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForBlockInclusion(any(), any()))
        .thenReturn(Optional.of(mock(OperationInvalidReason.class)));

    initPoolWithSingleItem();
    assertThat(pool.size()).isEqualTo(1);

    assertThat(pool.getItemsForBlock(state)).isEmpty();
    assertThat(pool.size()).isEqualTo(0);
  }

  @ParameterizedTest(name = "fromNetwork={0}")
  @ValueSource(booleans = {true, false})
  void subscribeOperationAddedSuccessfully(final boolean isFromNetwork) {
    final Subscription subscription = new Subscription();
    final OperationAddedSubscriber<SignedBlsToExecutionChange> subscriber =
        (key, value, fromNetwork) -> {
          subscription.setFromNetwork(fromNetwork);
          subscription.setBlsToExecutionChange(key);
          subscription.setInternalValidationResult(value);
        };
    pool.subscribeOperationAdded(subscriber);
    final SignedBlsToExecutionChange item = dataStructureUtil.randomSignedBlsToExecutionChange();
    when(validator.validateForGossip(item)).thenReturn(completedFuture(ACCEPT));

    if (isFromNetwork) {
      assertThat(pool.addRemote(item)).isCompleted();
    } else {
      assertThat(pool.addLocal(item)).isCompleted();
    }
    assertThat(subscription.getBlsToExecutionChange()).contains(item);
    assertThat(subscription.getInternalValidationResult()).contains(ACCEPT);
    assertThat(subscription.getFromNetwork()).contains(isFromNetwork);
  }

  @ParameterizedTest(name = "fromNetwork={0}")
  @ValueSource(booleans = {true, false})
  void subscribeOperationIgnored(final boolean isFromNetwork) {
    final Subscription subscription = new Subscription();
    final OperationAddedSubscriber<SignedBlsToExecutionChange> subscriber =
        (key, value, fromNetwork) -> {
          subscription.setFromNetwork(fromNetwork);
          subscription.setBlsToExecutionChange(key);
          subscription.setInternalValidationResult(value);
        };
    pool.subscribeOperationAdded(subscriber);
    final SignedBlsToExecutionChange item = dataStructureUtil.randomSignedBlsToExecutionChange();
    when(validator.validateForGossip(item)).thenReturn(completedFuture(IGNORE));

    if (isFromNetwork) {
      assertThat(pool.addRemote(item)).isCompleted();
    } else {
      assertThat(pool.addLocal(item)).isCompleted();
    }
    assertThat(subscription.getBlsToExecutionChange()).isEmpty();
    assertThat(subscription.getInternalValidationResult()).isEmpty();
    assertThat(subscription.getFromNetwork()).isEmpty();
  }

  @ParameterizedTest(name = "fromNetwork={0}")
  @ValueSource(booleans = {true, false})
  void subscribeOperationIgnoredDuplicate(final boolean isFromNetwork)
      throws ExecutionException, InterruptedException {
    final SignedBlsToExecutionChange item = dataStructureUtil.randomSignedBlsToExecutionChange();
    when(validator.validateForGossip(item)).thenReturn(completedFuture(ACCEPT));
    // pre-populate cache
    assertThat(pool.addRemote(item)).isCompleted();

    final Subscription subscription = new Subscription();
    final OperationAddedSubscriber<SignedBlsToExecutionChange> subscriber =
        (key, value, fromNetwork) -> {
          subscription.setFromNetwork(fromNetwork);
          subscription.setBlsToExecutionChange(key);
          subscription.setInternalValidationResult(value);
        };
    pool.subscribeOperationAdded(subscriber);

    final SafeFuture<InternalValidationResult> future;
    if (isFromNetwork) {
      // pre-populate cache, then try to add a second time.
      future = pool.addRemote(item);
    } else {
      future = pool.addLocal(item);
    }
    assertThat(future).isCompleted();
    assertThat(future.get().code()).isEqualTo(IGNORE.code());
    assertThat(subscription.getBlsToExecutionChange()).isEmpty();
    assertThat(subscription.getInternalValidationResult()).isEmpty();
    assertThat(subscription.getFromNetwork()).isEmpty();
  }

  private static class Subscription {
    private Optional<Boolean> fromNetwork = Optional.empty();
    private Optional<SignedBlsToExecutionChange> blsToExecutionChange = Optional.empty();
    private Optional<InternalValidationResult> internalValidationResult = Optional.empty();

    public void setFromNetwork(final boolean fromNetwork) {
      this.fromNetwork = Optional.of(fromNetwork);
    }

    public void setBlsToExecutionChange(final SignedBlsToExecutionChange change) {
      this.blsToExecutionChange = Optional.of(change);
    }

    public void setInternalValidationResult(final InternalValidationResult result) {
      this.internalValidationResult = Optional.of(result);
    }

    public Optional<Boolean> getFromNetwork() {
      return fromNetwork;
    }

    public Optional<SignedBlsToExecutionChange> getBlsToExecutionChange() {
      return blsToExecutionChange;
    }

    public Optional<InternalValidationResult> getInternalValidationResult() {
      return internalValidationResult;
    }
  }
}
