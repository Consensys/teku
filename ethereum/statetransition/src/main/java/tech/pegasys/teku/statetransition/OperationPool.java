/*
 * Copyright 2020 ConsenSys AG.
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

import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.OperationValidator;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class OperationPool<T extends SszData> {
  private static final int OPERATION_POOL_SIZE = 1000;
  private static final String OPERATION_POOL_SIZE_METRIC = "operation_pool_size_";
  private static final String OPERATION_POOL_SIZE_VALIDATION_REASON = "operation_pool_validation_";
  private final Set<T> operations = LimitedSet.create(OPERATION_POOL_SIZE);
  private final Function<UInt64, SszListSchema<T, ?>> slotToSszListSchemaSupplier;
  private final OperationValidator<T> operationValidator;
  private final Optional<Comparator<T>> priorityOrderComparator;
  private final Subscribers<OperationAddedSubscriber<T>> subscribers = Subscribers.create(true);
  private final LabelledMetric<Counter> validationReasonCounter;

  public OperationPool(
      final String metricType,
      final MetricsSystem metricsSystem,
      final Function<UInt64, SszListSchema<T, ?>> slotToSszListSchemaSupplier,
      final OperationValidator<T> operationValidator) {
    this(
        metricType,
        metricsSystem,
        slotToSszListSchemaSupplier,
        operationValidator,
        Optional.empty());
  }

  public OperationPool(
      final String metricType,
      final MetricsSystem metricsSystem,
      final Function<UInt64, SszListSchema<T, ?>> slotToSszListSchemaSupplier,
      final OperationValidator<T> operationValidator,
      final Comparator<T> priorityOrderComparator) {
    this(
        metricType,
        metricsSystem,
        slotToSszListSchemaSupplier,
        operationValidator,
        Optional.of(priorityOrderComparator));
  }

  private OperationPool(
      final String metricType,
      final MetricsSystem metricsSystem,
      final Function<UInt64, SszListSchema<T, ?>> slotToSszListSchemaSupplier,
      final OperationValidator<T> operationValidator,
      final Optional<Comparator<T>> priorityOrderComparator) {
    this.slotToSszListSchemaSupplier = slotToSszListSchemaSupplier;
    this.operationValidator = operationValidator;
    this.priorityOrderComparator = priorityOrderComparator;

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

  public void subscribeOperationAdded(final OperationAddedSubscriber<T> subscriber) {
    this.subscribers.subscribe(subscriber);
  }

  public SszList<T> getItemsForBlock(final BeaconState stateAtBlockSlot) {
    return getItemsForBlock(stateAtBlockSlot, operation -> true, operation -> {});
  }

  public SszList<T> getItemsForBlock(
      final BeaconState stateAtBlockSlot,
      final Predicate<T> filter,
      final Consumer<T> includedItemConsumer) {
    final SszListSchema<T, ?> schema =
        slotToSszListSchemaSupplier.apply(stateAtBlockSlot.getSlot());
    // Note that iterating through all items does not affect their access time so we are effectively
    // evicting the oldest entries when the size is exceeded as we only ever access via iteration.
    final Stream<T> operationsStream =
        priorityOrderComparator
            .map(comparator -> operations.stream().sorted(comparator))
            .orElseGet(operations::stream);
    return operationsStream
        .filter(filter)
        .filter(
            item -> operationValidator.validateForStateTransition(stateAtBlockSlot, item).isEmpty())
        .limit(schema.getMaxLength())
        .peek(includedItemConsumer)
        .collect(schema.collector());
  }

  public SafeFuture<InternalValidationResult> add(final T item) {
    return operationValidator
        .validateFully(item)
        .thenApply(
            result -> {
              validationReasonCounter.labels(result.code().toString()).inc();
              if (result.code().equals(ValidationResultCode.ACCEPT)
                  || result.code().equals(ValidationResultCode.SAVE_FOR_FUTURE)) {
                operations.add(item);
                subscribers.forEach(s -> s.onOperationAdded(item, result));
              }

              return result;
            });
  }

  public void addAll(final SszCollection<T> items) {
    operations.addAll(items.asList());
  }

  public void removeAll(final SszCollection<T> items) {
    // Avoid AbstractSet.removeAll with because it calls List.contains for each item in the set.
    items.asList().forEach(operations::remove);
  }

  public Set<T> getAll() {
    return Collections.unmodifiableSet(operations);
  }

  public interface OperationAddedSubscriber<T> {
    void onOperationAdded(T operation, InternalValidationResult validationStatus);
  }

  private int size() {
    return operations.size();
  }
}
