/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.common.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.versions.deneb.block.KzgCommitmentsProcessor;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class BlockProposalUtil {

  private final BlockProcessor blockProcessor;
  private final SchemaDefinitions schemaDefinitions;

  public BlockProposalUtil(
      final SchemaDefinitions schemaDefinitions, final BlockProcessor blockProcessor) {
    this.schemaDefinitions = schemaDefinitions;
    this.blockProcessor = blockProcessor;
  }

  public SafeFuture<BeaconBlockAndState> createNewUnsignedBlock(
      final UInt64 newSlot,
      final int proposerIndex,
      final BeaconState blockSlotState,
      final Bytes32 parentBlockSigningRoot,
      final Consumer<BeaconBlockBodyBuilder> bodyBuilder,
      final boolean blinded) {
    checkArgument(
        blockSlotState.getSlot().equals(newSlot),
        "Block slot state from incorrect slot. Expected %s but got %s",
        newSlot,
        blockSlotState.getSlot());

    // Create block body
    final SafeFuture<? extends BeaconBlockBody> beaconBlockBody;
    final BeaconBlockSchema beaconBlockSchema;

    if (blinded) {
      beaconBlockBody = createBlindedBeaconBlockBody(bodyBuilder);
      beaconBlockSchema = schemaDefinitions.getBlindedBeaconBlockSchema();
    } else {
      beaconBlockBody = createBeaconBlockBody(bodyBuilder);
      beaconBlockSchema = schemaDefinitions.getBeaconBlockSchema();
    }

    // Create initial block with some stubs
    final Bytes32 tmpStateRoot = Bytes32.ZERO;
    final SafeFuture<BeaconBlock> newBlock =
        beaconBlockBody.thenApply(
            body ->
                beaconBlockSchema.create(
                    newSlot,
                    UInt64.valueOf(proposerIndex),
                    parentBlockSigningRoot,
                    tmpStateRoot,
                    body));

    return newBlock
        .thenApplyChecked(
            block -> {
              // Run state transition and set state root
              // Skip verifying signatures as all operations are coming from our own pools.

              final BeaconState newState =
                  blockProcessor.processUnsignedBlock(
                      blockSlotState,
                      block,
                      IndexedAttestationCache.NOOP,
                      BLSSignatureVerifier.NO_OP,
                      Optional.empty(),
                      KzgCommitmentsProcessor.NOOP);

              final Bytes32 stateRoot = newState.hashTreeRoot();
              final BeaconBlock newCompleteBlock = block.withStateRoot(stateRoot);

              return new BeaconBlockAndState(newCompleteBlock, newState);
            })
        .exceptionallyCompose(
            error -> {
              if (ExceptionUtil.hasCause(error, BlockProcessingException.class)) {
                return SafeFuture.failedFuture(new StateTransitionException(error));
              }
              return SafeFuture.failedFuture(error);
            });
  }

  private SafeFuture<? extends BeaconBlockBody> createBeaconBlockBody(
      final Consumer<BeaconBlockBodyBuilder> bodyBuilder) {
    return schemaDefinitions.getBeaconBlockBodySchema().createBlockBody(bodyBuilder);
  }

  private SafeFuture<? extends BeaconBlockBody> createBlindedBeaconBlockBody(
      final Consumer<BeaconBlockBodyBuilder> bodyBuilder) {
    return schemaDefinitions.getBlindedBeaconBlockBodySchema().createBlockBody(bodyBuilder);
  }
}
