/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.storage.client;

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.spec.constants.NetworkConstants.INTERVALS_PER_SLOT_EIP7732;

import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.PayloadStatus;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;

public class PayloadTimelinessProvider {
  private final RecentChainData recentChainData;
  private final Spec spec;
  private final Map<Bytes32, PayloadAndArrivalTime> payloadByBeaconBlockRoot;

  public PayloadTimelinessProvider(final RecentChainData recentChainData, final Spec spec) {
    this.recentChainData = recentChainData;
    this.spec = spec;
    this.payloadByBeaconBlockRoot = LimitedMap.createSynchronizedNatural(3);
  }

  public void onPayload(
      final SignedExecutionPayloadEnvelope payload, final UInt64 arrivalTimeMillis) {
    payloadByBeaconBlockRoot.put(
        payload.getMessage().getBeaconBlockRoot(),
        new PayloadAndArrivalTime(payload.getMessage(), arrivalTimeMillis));
  }

  public SafeFuture<Optional<PayloadStatusAndBeaconBlockRoot>> getTimelyExecutionPayload(
      final UInt64 slot) {

    return getBeaconBlockRootAtSlot(slot)
        .thenApply(
            maybeBlockRoot -> {
              if (maybeBlockRoot.isEmpty()) {
                return Optional.empty();
              }

              return Optional.of(
                  maybeBlockRoot
                      .flatMap(
                          beaconBlockRoot ->
                              Optional.ofNullable(payloadByBeaconBlockRoot.get(beaconBlockRoot)))
                      .filter(
                          payloadAndArrivalTime ->
                              payloadAndArrivalTime.arrivalTimeMillis.isLessThanOrEqualTo(
                                  getMillisCutoff(slot)))
                      .map(
                          payloadAndArrivalTime ->
                              new PayloadStatusAndBeaconBlockRoot(
                                  payloadAndArrivalTime.payload.isPayloadWithheld()
                                      ? PayloadStatus.PAYLOAD_WITHHELD
                                      : PayloadStatus.PAYLOAD_PRESENT,
                                  payloadAndArrivalTime.payload.getBeaconBlockRoot()))
                      .orElse(
                          new PayloadStatusAndBeaconBlockRoot(
                              PayloadStatus.PAYLOAD_ABSENT, Bytes32.ZERO)));
            });
  }

  private SafeFuture<Optional<Bytes32>> getBeaconBlockRootAtSlot(final UInt64 slot) {
    final Optional<Bytes32> maybeBlockRootInEffect =
        recentChainData
            .getChainHead()
            .flatMap(head -> recentChainData.getBlockRootInEffectBySlot(slot, head.getRoot()));

    if (maybeBlockRootInEffect.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }

    return recentChainData
        .retrieveSignedBlockByRoot(maybeBlockRootInEffect.get())
        .thenApply(
            maybeBlock ->
                maybeBlock
                    .filter(block -> block.getSlot().equals(slot))
                    .map(SignedBeaconBlock::getRoot));
  }

  private UInt64 getMillisCutoff(final UInt64 slot) {
    final UInt64 slotStart =
        spec.getSlotStartTimeMillis(slot, recentChainData.getGenesisTimeMillis());
    final UInt64 payloadAttestationDueOffset =
        secondsToMillis(UInt64.valueOf(spec.getSecondsPerSlot(slot)))
            .times(3)
            .dividedBy(INTERVALS_PER_SLOT_EIP7732);

    return slotStart.plus(payloadAttestationDueOffset);
  }

  private record PayloadAndArrivalTime(
      ExecutionPayloadEnvelope payload, UInt64 arrivalTimeMillis) {}

  public record PayloadStatusAndBeaconBlockRoot(
      PayloadStatus payloadStatus, Bytes32 beaconBlockRoot) {}
}
