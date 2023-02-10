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

import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.IGNORE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.OperationValidator;
import tech.pegasys.teku.statetransition.validation.SignedBlsToExecutionChangeValidator;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class BlsToExecutionOperationPool implements OperationPool<SignedBlsToExecutionChange> {
  private static final Logger LOG = LogManager.getLogger();
  private static final int DEFAULT_OPERATION_POOL_SIZE = 50_000;
  private final Map<Integer, SignedBlsToExecutionChange> operations;
  private final Function<UInt64, SszListSchema<SignedBlsToExecutionChange, ?>>
      slotToSszListSchemaSupplier;
  private final OperationValidator<SignedBlsToExecutionChange> operationValidator;
  private final Subscribers<OperationAddedSubscriber<SignedBlsToExecutionChange>> subscribers =
      Subscribers.create(true);
  private final LabelledMetric<Counter> validationReasonCounter;

  public BlsToExecutionOperationPool(
      final String metricType,
      final MetricsSystem metricsSystem,
      final Function<UInt64, SszListSchema<SignedBlsToExecutionChange, ?>>
          slotToSszListSchemaSupplier,
      final SignedBlsToExecutionChangeValidator operationValidator) {

    this.slotToSszListSchemaSupplier = slotToSszListSchemaSupplier;
    this.operations = LimitedMap.createSynchronized(DEFAULT_OPERATION_POOL_SIZE);
    this.operationValidator = operationValidator;
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.BEACON,
        OPERATION_POOL_SIZE_METRIC + metricType,
        "Current number of operations in the pool",
        this::size);
    validationReasonCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            OPERATION_POOL_SIZE_VALIDATION_REASON + metricType,
            "Total number of attempts to add an operation to the pool, broken down by validation result",
            "result");
  }

  private static InternalValidationResult rejectForDuplicatedMessage(final int validatorIndex) {
    final String logMessage =
        String.format(
            "BlsToExecutionChange is not the first one for validator %s.", validatorIndex);
    LOG.trace(logMessage);
    return InternalValidationResult.create(IGNORE, logMessage);
  }

  @Override
  public void subscribeOperationAdded(
      final OperationAddedSubscriber<SignedBlsToExecutionChange> subscriber) {
    this.subscribers.subscribe(subscriber);
  }

  @Override
  public SszList<SignedBlsToExecutionChange> getItemsForBlock(final BeaconState stateAtBlockSlot) {
    return getItemsForBlock(stateAtBlockSlot, operation -> true, operation -> {});
  }

  @Override
  public SszList<SignedBlsToExecutionChange> getItemsForBlock(
      final BeaconState stateAtBlockSlot,
      final Predicate<SignedBlsToExecutionChange> filter,
      final Consumer<SignedBlsToExecutionChange> includedItemConsumer) {
    final SszListSchema<SignedBlsToExecutionChange, ?> schema =
        slotToSszListSchemaSupplier.apply(stateAtBlockSlot.getSlot());

    // Note that iterating through all items does not affect their access time so we are effectively
    // evicting the oldest entries when the size is exceeded as we only ever access via iteration.
    final Collection<SignedBlsToExecutionChange> sortedViableOperations = operations.values();
    final List<SignedBlsToExecutionChange> selected = new ArrayList<>();
    for (final SignedBlsToExecutionChange item : sortedViableOperations) {
      if (!filter.test(item)) {
        continue;
      }
      final int validatorIndex = getValidatorIndex(item);
      if (operationValidator.validateForBlockInclusion(stateAtBlockSlot, item).isEmpty()) {
        selected.add(item);
        includedItemConsumer.accept(item);
        if (selected.size() == schema.getMaxLength()) {
          break;
        }
      } else {
        // The item is no longer valid to be included in a block so remove it from the pool.
        operations.remove(validatorIndex);
      }
    }
    return schema.createFromElements(selected);
  }

  @Override
  public SafeFuture<InternalValidationResult> addLocal(final SignedBlsToExecutionChange item) {
    final int validatorIndex = getValidatorIndex(item);
    if (operations.containsKey(validatorIndex)) {
      return SafeFuture.completedFuture(rejectForDuplicatedMessage(validatorIndex))
          .thenPeek(result -> validationReasonCounter.labels(result.code().toString()).inc());
    }
    return add(item, false);
  }

  @Override
  public SafeFuture<InternalValidationResult> addRemote(final SignedBlsToExecutionChange item) {
    final int validatorIndex = getValidatorIndex(item);
    if (operations.containsKey(validatorIndex)) {
      return SafeFuture.completedFuture(rejectForDuplicatedMessage(validatorIndex))
          .thenPeek(result -> validationReasonCounter.labels(result.code().toString()).inc());
    }
    return add(item, true);
  }

  @Override
  public void addAll(final SszCollection<SignedBlsToExecutionChange> items) {
    items.forEach(
        item -> {
          final int validatorIndex = getValidatorIndex(item);
          operations.putIfAbsent(validatorIndex, item);
        });
  }

  @Override
  public void removeAll(final SszCollection<SignedBlsToExecutionChange> items) {
    items.forEach(
        item -> {
          final int validatorIndex = getValidatorIndex(item);
          operations.remove(validatorIndex);
        });
  }

  @Override
  public Set<SignedBlsToExecutionChange> getAll() {
    return Set.copyOf(operations.values());
  }

  @Override
  public int size() {
    return operations.size();
  }

  private SafeFuture<InternalValidationResult> add(
      SignedBlsToExecutionChange item, boolean fromNetwork) {
    final int validatorIndex = getValidatorIndex(item);
    return operationValidator
        .validateForGossip(item)
        .thenApply(
            result -> {
              validationReasonCounter.labels(result.code().toString()).inc();
              if (result.code().equals(ValidationResultCode.ACCEPT)
                  || result.code().equals(ValidationResultCode.SAVE_FOR_FUTURE)) {
                operations.put(validatorIndex, item);
                subscribers.forEach(s -> s.onOperationAdded(item, result, fromNetwork));
              }
              return result;
            });
  }

  private int getValidatorIndex(final SignedBlsToExecutionChange blsToExecutionChange) {
    return blsToExecutionChange.getMessage().getValidatorIndex().intValue();
  }
}
