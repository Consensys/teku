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

package tech.pegasys.teku.api.stateselector;

import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class StateSelectorFactory {

  private final Spec spec;
  private final CombinedChainDataClient client;

  public StateSelectorFactory(final Spec spec, final CombinedChainDataClient client) {
    this.spec = spec;
    this.client = client;
  }

  public StateSelector byBlockRootStateSelector(final String selectorMethod) {
    if (selectorMethod.startsWith("0x")) {
      try {
        return forBlockRoot(Bytes32.fromHexString(selectorMethod));
      } catch (IllegalArgumentException ex) {
        throw new BadRequestException("Invalid state: " + selectorMethod);
      }
    }
    return byKeywordOrSlot(selectorMethod);
  }

  public StateSelector defaultStateSelector(final String selectorMethod) {
    if (selectorMethod.startsWith("0x")) {
      try {
        return forStateRoot(Bytes32.fromHexString(selectorMethod));
      } catch (IllegalArgumentException ex) {
        throw new BadRequestException("Invalid state: " + selectorMethod);
      }
    }
    return byKeywordOrSlot(selectorMethod);
  }

  public StateSelector byKeywordOrSlot(final String selectorMethod) {
    switch (selectorMethod) {
      case "head":
        return headSelector();
      case "genesis":
        return genesisSelector();
      case "finalized":
        return finalizedSelector();
      case "justified":
        return justifiedSelector();
    }
    try {
      return forSlot(UInt64.valueOf(selectorMethod));
    } catch (NumberFormatException ex) {
      throw new BadRequestException("Invalid state: " + selectorMethod);
    }
  }

  public StateSelector headSelector() {
    return () -> {
      final Optional<ChainHead> maybeChainHead = client.getChainHead();
      if (maybeChainHead.isEmpty()) {
        // Can't have a state past the current chain head.
        return SafeFuture.completedFuture(Optional.empty());
      }
      final ChainHead chainHead = maybeChainHead.get();
      return chainHead
          .getState()
          .thenApply(
              state ->
                  Optional.of(
                      addMetaData(
                          state,
                          chainHead.isOptimistic(),
                          true,
                          client.isFinalized(state.getSlot()))));
    };
  }

  public StateSelector finalizedSelector() {
    return () ->
        SafeFuture.completedFuture(
            client
                .getLatestFinalized()
                .map(
                    finalized ->
                        addMetaData(
                            finalized.getState(),
                            // The finalized checkpoint may change because of optimistically
                            // imported blocks at the head and if the head isn't optimistic, the
                            // finalized block can't be optimistic.
                            client.isChainHeadOptimistic(),
                            true,
                            true)));
  }

  public StateSelector justifiedSelector() {
    return () ->
        client
            .getJustifiedState()
            .thenApply(
                maybeState ->
                    maybeState.map(
                        // The justified checkpoint may change because of optimistically
                        // imported blocks at the head and if the head isn't optimistic, the
                        // justified block can't be optimistic.
                        state -> addMetaData(state, client.isChainHeadOptimistic(), true, false)));
  }

  public StateSelector genesisSelector() {
    return () ->
        client
            .getStateAtSlotExact(GENESIS_SLOT)
            .thenApply(
                maybeState -> maybeState.map(state -> addMetaData(state, false, true, true)));
  }

  public StateSelector forSlot(final UInt64 slot) {
    return () ->
        client
            .getChainHead()
            .map(
                head -> {
                  if (slot.isGreaterThan(head.getSlot())) {
                    return SafeFuture.completedFuture(Optional.<StateAndMetaData>empty());
                  }
                  return client
                      .getStateAtSlotExact(slot, head.getRoot())
                      .thenApply(
                          maybeState ->
                              maybeState.map(
                                  state ->
                                      addMetaData(
                                          state,
                                          head.isOptimistic(),
                                          true,
                                          client.isFinalized(slot))));
                })
            .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  public StateSelector forStateRoot(final Bytes32 stateRoot) {
    return () -> client.getStateByStateRoot(stateRoot).thenApply(this::addMetaData);
  }

  public StateSelector forBlockRoot(final Bytes32 blockRoot) {
    return () -> client.getStateByBlockRoot(blockRoot).thenApply(this::addMetaData);
  }

  private Optional<StateAndMetaData> addMetaData(final Optional<BeaconState> maybeState) {
    final Optional<ChainHead> maybeChainHead = client.getChainHead();
    if (maybeChainHead.isEmpty() || maybeState.isEmpty()) {
      return Optional.empty();
    } else {
      final ChainHead chainHead = maybeChainHead.get();
      final BeaconState state = maybeState.get();
      final Bytes32 blockRoot = BeaconBlockHeader.fromState(state).getRoot();
      return Optional.of(
          addMetaData(
              state,
              chainHead.isOptimistic() || client.isOptimisticBlock(blockRoot),
              client.isCanonicalBlock(state.getSlot(), blockRoot, chainHead.getRoot()),
              client.isFinalized(state.getSlot())));
    }
  }

  private StateAndMetaData addMetaData(
      final BeaconState state,
      final boolean executionOptimistic,
      final boolean canonical,
      final boolean finalized) {
    return new StateAndMetaData(
        state,
        spec.atSlot(state.getSlot()).getMilestone(),
        executionOptimistic,
        canonical,
        finalized);
  }
}
