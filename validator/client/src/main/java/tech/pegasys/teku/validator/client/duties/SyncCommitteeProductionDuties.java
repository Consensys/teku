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

package tech.pegasys.teku.validator.client.duties;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeSignature;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.validator.api.NodeSyncingException;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;

public class SyncCommitteeProductionDuties {

  private final ForkProvider forkProvider;
  private final Collection<ValidatorAndCommitteeIndices> assignments;

  private final Spec spec;
  private final ValidatorApiChannel validatorApiChannel;

  private SyncCommitteeProductionDuties(
      final Spec spec,
      final ValidatorApiChannel validatorApiChannel,
      final ForkProvider forkProvider,
      final Collection<ValidatorAndCommitteeIndices> assignments) {
    this.spec = spec;
    this.validatorApiChannel = validatorApiChannel;
    this.forkProvider = forkProvider;
    this.assignments = assignments;
  }

  public static SyncCommitteeProductionDuties.Builder builder() {
    return new Builder();
  }

  public SafeFuture<DutyResult> produceSignatures(final UInt64 slot) {
    if (assignments.isEmpty()) {
      return SafeFuture.completedFuture(DutyResult.NO_OP);
    }
    return forkProvider
        .getForkInfo()
        .thenCompose(
            forkInfo ->
                validatorApiChannel
                    .getBlockRootInEffectAtSlot(slot)
                    .thenCompose(
                        maybeBlockRoot ->
                            maybeBlockRoot
                                .map(blockRoot -> produceSignatures(forkInfo, slot, blockRoot))
                                .orElseGet(
                                    () ->
                                        SafeFuture.completedFuture(
                                            DutyResult.forError(new NodeSyncingException())))));
  }

  private SafeFuture<DutyResult> produceSignatures(
      final ForkInfo forkInfo, final UInt64 slot, final Bytes32 blockRoot) {
    return SafeFuture.collectAll(
            assignments.stream()
                .map(assignment -> produceSignature(forkInfo, slot, blockRoot, assignment)))
        .thenApply(
            signatures -> {
              validatorApiChannel.sendSyncCommitteeSignatures(signatures);
              return DutyResult.success(blockRoot, assignments.size());
            });
  }

  private SafeFuture<SyncCommitteeSignature> produceSignature(
      final ForkInfo forkInfo,
      final UInt64 slot,
      final Bytes32 blockRoot,
      final ValidatorAndCommitteeIndices assignment) {
    return assignment
        .validator
        .getSigner()
        .signSyncCommitteeSignature(slot, blockRoot, forkInfo)
        .thenApply(
            signature -> createSyncCommitteeSignature(slot, blockRoot, assignment, signature));
  }

  private SyncCommitteeSignature createSyncCommitteeSignature(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final ValidatorAndCommitteeIndices assignment,
      final BLSSignature signature) {
    return SchemaDefinitionsAltair.required(spec.atSlot(slot).getSchemaDefinitions())
        .getSyncCommitteeSignatureSchema()
        .create(slot, blockRoot, UInt64.valueOf(assignment.validatorIndex), signature);
  }

  private static class ValidatorAndCommitteeIndices {
    private final Validator validator;
    private final int validatorIndex;
    private final Set<Integer> committeeIndices = new HashSet<>();

    private ValidatorAndCommitteeIndices(final Validator validator, final int validatorIndex) {
      this.validator = validator;
      this.validatorIndex = validatorIndex;
    }

    public void addCommitteeIndex(final int subcommitteeIndex) {
      committeeIndices.add(subcommitteeIndex);
    }
  }

  public static class Builder {
    private ValidatorApiChannel validatorApiChannel;
    private ForkProvider forkProvider;
    private final Map<Integer, ValidatorAndCommitteeIndices> assignments = new HashMap<>();
    private Spec spec;

    public Builder spec(final Spec spec) {
      this.spec = spec;
      return this;
    }

    public Builder validatorApiChannel(final ValidatorApiChannel validatorApiChannel) {
      this.validatorApiChannel = validatorApiChannel;
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

    public SyncCommitteeProductionDuties build() {
      checkNotNull(spec, "Must provide a spec");
      checkNotNull(validatorApiChannel, "Must provide a validatorApiChannel");
      checkNotNull(forkProvider, "Must provide a forkProvider");
      return new SyncCommitteeProductionDuties(
          spec, validatorApiChannel, forkProvider, assignments.values());
    }
  }
}
