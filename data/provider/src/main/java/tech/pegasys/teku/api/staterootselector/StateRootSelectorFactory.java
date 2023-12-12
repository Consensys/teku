/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.api.staterootselector;

import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.AbstractSelectorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class StateRootSelectorFactory extends AbstractSelectorFactory<StateRootSelector> {
  private final Spec spec;

  public StateRootSelectorFactory(final Spec spec, final CombinedChainDataClient client) {
    super(client);
    this.spec = spec;
  }

  @Override
  public StateRootSelector stateRootSelector(final Bytes32 stateRoot) {
    return () -> client.getStateByStateRoot(stateRoot).thenApply(this::fromState);
  }

  @Override
  public StateRootSelector headSelector() {
    return () ->
        client
            .getChainHead()
            .map(this::fromChainHead)
            .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  @Override
  public StateRootSelector finalizedSelector() {
    return () -> {
      final Optional<Bytes32> maybeFinalizedStateRoot = client.getFinalizedStateRoot();
      final Optional<UInt64> maybeFinalizedSlot = client.getFinalizedStateSlot();
      if (maybeFinalizedStateRoot.isPresent() && maybeFinalizedSlot.isPresent()) {
        return SafeFuture.completedFuture(
            Optional.of(
                lookupStateRootData(
                    maybeFinalizedStateRoot.get(),
                    maybeFinalizedSlot.get(),
                    client.isChainHeadOptimistic(),
                    true,
                    true)));
      } else {
        return SafeFuture.completedFuture(
            client
                .getFinalizedState()
                .map(
                    finalizedState ->
                        lookupCanonicalStateRootData(
                            finalizedState, client.isChainHeadOptimistic())));
      }
    };
  }

  @Override
  public StateRootSelector genesisSelector() {
    return () ->
        client
            .getStateAtSlotExact(GENESIS_SLOT)
            .thenApply(
                maybeBeaconState ->
                    maybeBeaconState.map(
                        beaconState -> lookupCanonicalStateRootData(beaconState, false)));
  }

  @Override
  public StateRootSelector slotSelector(final UInt64 slot) {
    return () -> forSlot(client.getChainHead(), slot);
  }

  private SafeFuture<Optional<ObjectAndMetaData<Bytes32>>> forSlot(
      final Optional<ChainHead> maybeHead, final UInt64 slot) {
    return maybeHead
        .map(head -> forSlot(head, slot))
        .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  private SafeFuture<Optional<ObjectAndMetaData<Bytes32>>> forSlot(
      final ChainHead head, final UInt64 slot) {
    return client
        .getStateRootAtSlotExact(slot)
        .thenApply(
            maybeStateRoot ->
                maybeStateRoot.map(
                    stateRoot ->
                        lookupCanonicalStateRootData(stateRoot, slot, head.isOptimistic())));
  }

  private SafeFuture<Optional<ObjectAndMetaData<Bytes32>>> fromChainHead(final ChainHead head) {
    return head.getState()
        .thenApply(beaconState -> lookupCanonicalStateRootData(beaconState, head.isOptimistic()))
        .thenApply(Optional::of);
  }

  private Optional<ObjectAndMetaData<Bytes32>> fromState(
      final Optional<BeaconState> maybeBeaconState) {
    final Optional<ChainHead> chainHead = client.getChainHead();
    if (maybeBeaconState.isEmpty() || chainHead.isEmpty()) {
      return Optional.empty();
    }
    return maybeBeaconState.map(
        beaconState ->
            lookupStateRootData(
                beaconState.hashTreeRoot(),
                beaconState.getSlot(),
                chainHead.get().isOptimistic(),
                client.isCanonicalBlock(
                    beaconState.getSlot(),
                    BeaconBlockHeader.fromState(beaconState).getRoot(),
                    chainHead.get().getRoot()),
                client.isFinalized(beaconState.getSlot())));
  }

  private ObjectAndMetaData<Bytes32> lookupStateRootData(
      final Bytes32 stateRoot,
      final UInt64 slot,
      final boolean isOptimistic,
      final boolean isCanonical,
      final boolean finalized) {
    return new ObjectAndMetaData<>(
        stateRoot, spec.atSlot(slot).getMilestone(), isOptimistic, isCanonical, finalized);
  }

  private ObjectAndMetaData<Bytes32> lookupCanonicalStateRootData(
      final BeaconState beaconState, final boolean isOptimistic) {
    return lookupCanonicalStateRootData(
        beaconState.hashTreeRoot(), beaconState.getSlot(), isOptimistic);
  }

  private ObjectAndMetaData<Bytes32> lookupCanonicalStateRootData(
      final Bytes32 stateRoot, final UInt64 slot, final boolean isOptimistic) {
    return lookupStateRootData(stateRoot, slot, isOptimistic, true, client.isFinalized(slot));
  }
}
