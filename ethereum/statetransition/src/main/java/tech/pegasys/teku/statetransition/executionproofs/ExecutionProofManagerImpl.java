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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteListImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProofSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.datastructures.state.BlockRootAndState;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTracker;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.validation.ExecutionProofGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class ExecutionProofManagerImpl implements ExecutionProofManager, ExecutionProofGenerator {

  final ExecutionProofGossipValidator executionProofGossipValidator;
  //    final ExecutionProofGossipValidator executionProofGossipValidator;
  private final Subscribers<ValidExecutionProofListener> receivedExecutionProofSubscribers =
      Subscribers.create(true);
  private final Map<Bytes32, Set<ExecutionProof>> validatedExecutionProofsByBlockRoot = new ConcurrentHashMap<>();
  private SchemaDefinitionsElectra schemaDefinitionsElectra;

  private static final Logger LOG = LogManager.getLogger();

  public ExecutionProofManagerImpl(
      final ExecutionProofGossipValidator executionProofGossipValidator) {
    this.executionProofGossipValidator = executionProofGossipValidator;
  }

  @Override
  public void onExecutionProofPublish(
      final ExecutionProof executionProof, final RemoteOrigin remoteOrigin) {
      //TODO
  }

  @Override
  public SafeFuture<InternalValidationResult> onExecutionProofGossip(
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
    public AvailabilityChecker<ExecutionProof> createAvailabilityChecker(final SignedBeaconBlock block) {
//        final BlockExecutionProofsTracker blockExecutionProofsTracker =
//                blockExecutionProofsTrackersPool.getOrCreateBlockExecutionProofsTracker(block);
      return null;
    }

    @Override
    public SafeFuture<Void> generateExecutionProof(final SignedBlockContainer blockContainer) {
        //maybe we want to dealy proof generation like Kev did in LH
        //something between 1000-3000ms randomly, for now just do it immediately

        final ExecutionPayload executionPayload = blockContainer.getSignedBlock().getBeaconBlock().get().getBody().getOptionalExecutionPayload().get();
        final Bytes32 blockRoot = blockContainer.getSignedBlock().getRoot();
        final Bytes32 blockHash = executionPayload.getBlockHash();
        Bytes dummyWitness = Bytes.of(("dummy_witness_for_block_"+blockHash.toHexString()).getBytes());
        Set<ExecutionProof> generatedProofs = new HashSet<>();
        for(int i = 0; i < Constants.MAX_EXECUTION_PROOF_SUBNETS.intValue(); i++) {
            final ExecutionProof executionProof = generateProof(blockRoot, executionPayload, dummyWitness, UInt64.valueOf(i));
            generatedProofs.add(executionProof);
        }
        validatedExecutionProofsByBlockRoot.put(blockRoot, generatedProofs);
        LOG.debug("Generated execution proof for block {}: {}", blockRoot, generatedProofs);
        return SafeFuture.completedFuture(null);

    }

    private ExecutionProof generateProof(final Bytes32 blockRoot, final ExecutionPayload executionPayload, final Bytes dummyWitness, final UInt64 subnetId) {
        final Bytes32 blockHash = executionPayload.getBlockHash();
        final UInt64 blockNumber = executionPayload.getBlockNumber();
        final String dummyProof = "dummy_proof_subnet_"+subnetId.intValue()+"_block_"+blockHash.toHexString()+"_number_"+blockNumber.intValue()+"_witness_len_"+dummyWitness.size();

        ExecutionProofSchema executionProofSchema = schemaDefinitionsElectra.getExecutionProofSchema();
        return executionProofSchema.create(
                blockRoot,
                executionPayload.getBlockHash(),
                subnetId,
                UInt64.ONE,
                Bytes.of(dummyProof.getBytes()));
    }
}
