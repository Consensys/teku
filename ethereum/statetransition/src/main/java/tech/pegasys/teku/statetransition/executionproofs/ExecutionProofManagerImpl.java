/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.executionproofs;

import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProofSchema;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.forkchoice.ExecutionProofsAvailabilityChecker;
import tech.pegasys.teku.statetransition.validation.ExecutionProofGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

import static tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker.NOOP_EXECUTION_PROOF;

public class ExecutionProofManagerImpl implements ExecutionProofManager {

  final ExecutionProofGossipValidator executionProofGossipValidator;

  private final Subscribers<ValidExecutionProofListener> receivedExecutionProofSubscribers =
      Subscribers.create(true);
  private final Map<Bytes32, Set<ExecutionProof>> validatedExecutionProofsByBlockRoot =
      new ConcurrentHashMap<>();
    private final Consumer<ExecutionProof> onCreatedProof;
    private final SchemaDefinitionsElectra schemaDefinitionsElectra;
    private final int minProofsRequired;
  private static final Logger LOG = LogManager.getLogger();

  public ExecutionProofManagerImpl(
          final ExecutionProofGossipValidator executionProofGossipValidator,
          final Consumer<ExecutionProof> onCreatedProof,
      final SchemaDefinitionsElectra schemaDefinitionsElectra,
          final int minProofsRequired) {
    this.executionProofGossipValidator = executionProofGossipValidator;
      this.onCreatedProof = onCreatedProof;
      this.schemaDefinitionsElectra = schemaDefinitionsElectra;
      this.minProofsRequired = minProofsRequired;
  }

  @Override
  public void onExecutionProofPublish(
      final ExecutionProof executionProof, final RemoteOrigin remoteOrigin) {
    // TODO
  }

  @Override
  public SafeFuture<InternalValidationResult> onReceivedExecutionProofGossip(
      final ExecutionProof executionProof, final Optional<UInt64> arrivalTimestamp) {
    LOG.debug("Received execution proof for block {}", executionProof);
    return executionProofGossipValidator.validate(
        executionProof, executionProof.getSubnetId().get());
  }

  @Override
  public void subscribeToValidExecutionProofs(
      final ValidExecutionProofListener executionProofListener) {
    receivedExecutionProofSubscribers.subscribe(executionProofListener);
  }

  @Override
  public AvailabilityChecker<ExecutionProof> createAvailabilityChecker(
      final SignedBeaconBlock block) {
    return new ExecutionProofsAvailabilityChecker(this,block);
  }

  public SafeFuture<DataAndValidationResult<ExecutionProof>> validateBlockWithExecutionProofs(final SignedBeaconBlock block) {
      if(validatedExecutionProofsByBlockRoot.containsKey(block.getRoot())) {
          List<ExecutionProof> proofs = validatedExecutionProofsByBlockRoot.get(block.getRoot()).stream().toList();
          if(proofs.size() >= minProofsRequired) {
              return SafeFuture.completedFuture(
                      DataAndValidationResult.validResult(proofs));
          }
      }
      return SafeFuture.completedFuture(
              DataAndValidationResult.notAvailable());
  }

  @Override
  public SafeFuture<Void> generateExecutionProof(final SignedBlockContainer blockContainer) {
    // maybe we want to dealy proof generation like Kev did in LH
    // something between 1000-3000ms randomly, for now just do it immediately
      try {
          Thread.sleep(1000);
      } catch (InterruptedException e) {
          throw new RuntimeException(e);
      }
      final ExecutionPayload executionPayload =
        blockContainer
            .getSignedBlock()
            .getBeaconBlock()
            .get()
            .getBody()
            .getOptionalExecutionPayload()
            .get();
    final Bytes32 blockRoot = blockContainer.getSignedBlock().getRoot();
    final Bytes32 blockHash = executionPayload.getBlockHash();
    Bytes dummyWitness =
        Bytes.of(
            ("dummy_witness_for_block_" + blockHash.toHexString())
                .getBytes(Charset.defaultCharset()));
    Set<ExecutionProof> generatedProofs = new HashSet<>();
    for (int i = 0; i < Constants.MAX_EXECUTION_PROOF_SUBNETS.intValue(); i++) {
      final ExecutionProof executionProof =
          generateProof(blockRoot, executionPayload, dummyWitness, UInt64.valueOf(i));
      generatedProofs.add(executionProof);
      LOG.info("Generated proof for subnet {}", executionProof.getSubnetId());
        onCreatedProof.accept(executionProof);
    }
    validatedExecutionProofsByBlockRoot.put(blockRoot, generatedProofs);
    LOG.debug("Generated execution proof for block {}: {}", blockRoot, generatedProofs);


    return SafeFuture.completedFuture(null);
  }

  private ExecutionProof generateProof(
      final Bytes32 blockRoot,
      final ExecutionPayload executionPayload,
      final Bytes dummyWitness,
      final UInt64 subnetId) {
    final Bytes32 blockHash = executionPayload.getBlockHash();
    final UInt64 blockNumber = executionPayload.getBlockNumber();
    final String dummyProof =
        "dummy_proof_subnet_"
            + subnetId.intValue()
            + "_block_"
            + blockHash.toHexString()
            + "_number_"
            + blockNumber.intValue()
            + "_witness_len_"
            + dummyWitness.size();

    ExecutionProofSchema executionProofSchema = schemaDefinitionsElectra.getExecutionProofSchema();
    return executionProofSchema.create(
        blockRoot,
        executionPayload.getBlockHash(),
        subnetId,
        UInt64.ONE,
        Bytes.of(dummyProof.getBytes(Charset.defaultCharset())));
  }
}
