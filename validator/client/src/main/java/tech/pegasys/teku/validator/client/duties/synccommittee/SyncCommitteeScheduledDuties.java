/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.validator.client.duties.synccommittee;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

public class SyncCommitteeScheduledDuties implements ScheduledDuties {

  private final Collection<ValidatorAndCommitteeIndices> assignments;

  private final SyncCommitteeProductionDuty productionDuty;

  private SyncCommitteeScheduledDuties(
      final SyncCommitteeProductionDuty productionDuty,
      final Collection<ValidatorAndCommitteeIndices> assignments) {
    this.productionDuty = productionDuty;
    this.assignments = assignments;
  }

  public static SyncCommitteeScheduledDuties.Builder builder() {
    return new Builder();
  }

  @Override
  public boolean requiresRecalculation(final Bytes32 newHeadDependentRoot) {
    return false;
  }

  @Override
  public SafeFuture<DutyResult> performProductionDuty(final UInt64 slot) {
    return productionDuty.produceSignatures(slot);
  }

  @Override
  public String getProductionType() {
    return "sync_committee_signature";
  }

  @Override
  public SafeFuture<DutyResult> performAggregationDuty(final UInt64 slot) {
    return SafeFuture.completedFuture(DutyResult.NO_OP);
  }

  @Override
  public String getAggregationType() {
    return "sync_committee_contribution";
  }

  @Override
  public int countDuties() {
    return assignments.size();
  }

  public static class Builder {
    private ValidatorApiChannel validatorApiChannel;
    private ForkProvider forkProvider;
    private final Map<Integer, ValidatorAndCommitteeIndices> assignments = new HashMap<>();
    private Spec spec;
    private ChainHeadTracker chainHeadTracker;

    public Builder spec(final Spec spec) {
      this.spec = spec;
      return this;
    }

    public Builder validatorApiChannel(final ValidatorApiChannel validatorApiChannel) {
      this.validatorApiChannel = validatorApiChannel;
      return this;
    }

    public Builder chainHeadTracker(final ChainHeadTracker chainHeadTracker) {
      this.chainHeadTracker = chainHeadTracker;
      return this;
    }

    public Builder forkProvider(final ForkProvider forkProvider) {
      this.forkProvider = forkProvider;
      return this;
    }

    public Builder committeeAssignment(
        final Validator validator, final int validatorIndex, final int committeeIndex) {
      assignments
          .computeIfAbsent(
              validatorIndex, __ -> new ValidatorAndCommitteeIndices(validator, validatorIndex))
          .addCommitteeIndex(committeeIndex);
      return this;
    }

    public Builder committeeAssignments(
        final Validator validator,
        final int validatorIndex,
        final Collection<Integer> committeeIndices) {
      assignments
          .computeIfAbsent(
              validatorIndex, __ -> new ValidatorAndCommitteeIndices(validator, validatorIndex))
          .addCommitteeIndices(committeeIndices);
      return this;
    }

    public SyncCommitteeScheduledDuties build() {
      checkNotNull(spec, "Must provide a spec");
      checkNotNull(validatorApiChannel, "Must provide a validatorApiChannel");
      checkNotNull(chainHeadTracker, "Must provide a chainHeadTracker");
      checkNotNull(forkProvider, "Must provide a forkProvider");
      return new SyncCommitteeScheduledDuties(
          new SyncCommitteeProductionDuty(
              spec, forkProvider, validatorApiChannel, chainHeadTracker, assignments.values()),
          assignments.values());
    }
  }
}
