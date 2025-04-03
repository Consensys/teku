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

package tech.pegasys.teku.statetransition.datacolumns.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil.getRootCauseMessage;
import static tech.pegasys.teku.statetransition.blobs.RemoteOrigin.LOCAL_PROPOSAL;
import static tech.pegasys.teku.statetransition.blobs.RemoteOrigin.RECOVERED;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndProof;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarELRecoveryManager;
import tech.pegasys.teku.statetransition.util.AbstractIgnoringFutureHistoricalSlot;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DataColumnSidecarELRecoveryManagerImpl extends AbstractIgnoringFutureHistoricalSlot
    implements DataColumnSidecarELRecoveryManager {
  private static final Logger LOG = LogManager.getLogger();

  private final Map<Bytes32, RecoveryTask> recoveryTasks = new HashMap<>();
  private final NavigableSet<SlotAndBlockRoot> orderedRecoveryTasks = new TreeSet<>();
  private final Spec spec;
  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final ExecutionLayerChannel executionLayer;
  private final int maxTrackers;
  private final Consumer<List<DataColumnSidecar>> dataColumnSidecarPublisher;
  private final CustodyGroupCountManager custodyGroupCountManager;
  private final KZG kzg;

  private final Supplier<MiscHelpersFulu> miscHelpersFuluSupplier;

  @VisibleForTesting
  public DataColumnSidecarELRecoveryManagerImpl(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayer,
      final UInt64 historicalSlotTolerance,
      final UInt64 futureSlotTolerance,
      final int maxTrackers,
      final KZG kzg,
      final Consumer<List<DataColumnSidecar>> dataColumnSidecarPublisher,
      final CustodyGroupCountManager custodyGroupCountManager) {
    super(spec, futureSlotTolerance, historicalSlotTolerance);
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.executionLayer = executionLayer;
    this.maxTrackers = maxTrackers;
    this.kzg = kzg;
    this.dataColumnSidecarPublisher = dataColumnSidecarPublisher;
    this.custodyGroupCountManager = custodyGroupCountManager;
    this.miscHelpersFuluSupplier =
        () -> MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
  }

  @Override
  public void onNewDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar, final RemoteOrigin remoteOrigin) {
    if (!spec.atSlot(dataColumnSidecar.getSlot())
        .getMilestone()
        .isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      return;
    }
    if (recentChainData.containsBlock(dataColumnSidecar.getBlockRoot())) {
      return;
    }
    if (shouldIgnoreItemAtSlot(dataColumnSidecar.getSlot())) {
      return;
    }

    final SlotAndBlockRoot slotAndBlockRoot = dataColumnSidecar.getSlotAndBlockRoot();

    if (createRecoveryTaskFromDataColumnSidecar(dataColumnSidecar)) {
      onFirstSeen(slotAndBlockRoot, Optional.of(remoteOrigin));
      orderedRecoveryTasks.add(slotAndBlockRoot);
    }
  }

  private synchronized boolean createRecoveryTaskFromDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar) {
    if (recoveryTasks.containsKey(dataColumnSidecar.getBlockRoot())) {
      return false;
    }
    if (!dataColumnSidecar.getSlot().equals(getCurrentSlot())) {
      return false;
    }
    makeRoomForNewTracker();
    recoveryTasks.put(
        dataColumnSidecar.getBlockRoot(),
        new RecoveryTask(
            dataColumnSidecar.getSignedBeaconBlockHeader(),
            dataColumnSidecar.getSszKZGCommitments(),
            dataColumnSidecar.getKzgCommitmentsInclusionProof().asListUnboxed()));
    return true;
  }

  private synchronized boolean createRecoveryTaskFromBlock(final SignedBeaconBlock block) {
    if (recoveryTasks.containsKey(block.getRoot())) {
      return false;
    }
    if (!block.getSlot().equals(getCurrentSlot())) {
      return false;
    }
    makeRoomForNewTracker();
    final BeaconBlockBodyDeneb blockBodyDeneb =
        BeaconBlockBodyDeneb.required(block.getMessage().getBody());
    recoveryTasks.put(
        block.getRoot(),
        new RecoveryTask(
            block.asHeader(),
            blockBodyDeneb.getBlobKzgCommitments(),
            miscHelpersFuluSupplier
                .get()
                .computeDataColumnKzgCommitmentsInclusionProof(blockBodyDeneb)));
    return true;
  }

  private void publishRecoveredDataColumnSidecars(
      final RecoveryTask recoveryTask, final List<Blob> blobs) {
    final List<DataColumnSidecar> dataColumnSidecars =
        miscHelpersFuluSupplier
            .get()
            .constructDataColumnSidecars(
                recoveryTask.signedBeaconBlockHeader(),
                recoveryTask.sszKZGCommitments(),
                recoveryTask.kzgCommitmentsInclusionProof(),
                blobs,
                kzg);
    final int custodyCount = custodyGroupCountManager.getCustodyGroupCount();
    final int maxCustodyGroups =
        SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig())
            .getNumberOfCustodyGroups();
    final List<DataColumnSidecar> myCustodySidecars;
    if (custodyCount == maxCustodyGroups) {
      myCustodySidecars = dataColumnSidecars;
    } else {
      final Set<UInt64> myCustodyIndices =
          new HashSet<>(custodyGroupCountManager.getCustodyColumnIndices());
      myCustodySidecars =
          dataColumnSidecars.stream()
              .filter(sidecar -> myCustodyIndices.contains(sidecar.getIndex()))
              .toList();
    }

    LOG.info(
        "Publishing {} data column sidecars for {}",
        myCustodySidecars.size(),
        recoveryTask.getSlotAndBlockRoot());
    dataColumnSidecarPublisher.accept(myCustodySidecars);
  }

  @Override
  public synchronized void onNewBlock(
      final SignedBeaconBlock block, final Optional<RemoteOrigin> remoteOrigin) {
    if (block.getMessage().getBody().toVersionDeneb().isEmpty()) {
      return;
    }
    if (!spec.atSlot(block.getSlot()).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      return;
    }
    if (recentChainData.containsBlock(block.getRoot())) {
      return;
    }
    if (shouldIgnoreItemAtSlot(block.getSlot())) {
      return;
    }
    if (createRecoveryTaskFromBlock(block)) {
      onFirstSeen(block.getSlotAndBlockRoot(), remoteOrigin);
      orderedRecoveryTasks.add(block.getSlotAndBlockRoot());
    }
  }

  private synchronized void removeAllForBlock(final Bytes32 blockRoot) {
    final RecoveryTask recoveryTask = recoveryTasks.remove(blockRoot);

    if (recoveryTask != null) {
      orderedRecoveryTasks.remove(recoveryTask.getSlotAndBlockRoot());
    }
  }

  @VisibleForTesting
  RecoveryTask getRecoveryTask(final SlotAndBlockRoot slotAndBlockRoot) {
    return recoveryTasks.get(slotAndBlockRoot.getBlockRoot());
  }

  @Override
  public void onSlot(final UInt64 slot) {
    super.onSlot(slot);
    LOG.trace(
        "Recovery tasks: {}",
        () -> {
          synchronized (this) {
            return recoveryTasks.toString();
          }
        });
  }

  @VisibleForTesting
  @Override
  protected synchronized void prune(final UInt64 slotLimit) {
    final List<SlotAndBlockRoot> toRemove = new ArrayList<>();
    for (SlotAndBlockRoot slotAndBlockRoot : orderedRecoveryTasks) {
      if (slotAndBlockRoot.getSlot().isGreaterThan(slotLimit)) {
        break;
      }
      toRemove.add(slotAndBlockRoot);
    }

    toRemove.stream().map(SlotAndBlockRoot::getBlockRoot).forEach(this::removeAllForBlock);
  }

  public synchronized int getTotalRecoveryTasks() {
    return recoveryTasks.size();
  }

  private void makeRoomForNewTracker() {
    while (recoveryTasks.size() > maxTrackers - 1) {
      final SlotAndBlockRoot toRemove = orderedRecoveryTasks.pollFirst();
      if (toRemove == null) {
        break;
      }
      removeAllForBlock(toRemove.getBlockRoot());
    }
  }

  private void onFirstSeen(
      final SlotAndBlockRoot slotAndBlockRoot, final Optional<RemoteOrigin> remoteOrigin) {
    final boolean isLocalBlockProductionOrRecovered =
        remoteOrigin.map(ro -> Set.of(LOCAL_PROPOSAL, RECOVERED).contains(ro)).orElse(false);
    if (isLocalBlockProductionOrRecovered) {
      return;
    }

    asyncRunner
        .runAsync(
            () ->
                // fetch blobs from EL with no delay
                fetchMissingBlobsFromLocalEL(slotAndBlockRoot))
        .handleException(this::logLocalElBlobsLookupFailure)
        .ifExceptionGetsHereRaiseABug();
  }

  private synchronized SafeFuture<Void> fetchMissingBlobsFromLocalEL(
      final SlotAndBlockRoot slotAndBlockRoot) {
    final RecoveryTask recoveryTask = recoveryTasks.get(slotAndBlockRoot.getBlockRoot());

    if (recoveryTask == null) {
      return SafeFuture.COMPLETE;
    }
    if (recoveryTask.sszKZGCommitments().isEmpty()) {
      return SafeFuture.COMPLETE;
    }

    final List<BlobIdentifier> missingBlobsIdentifiers =
        IntStream.range(0, recoveryTask.sszKZGCommitments().size())
            .mapToObj(
                index -> new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), UInt64.valueOf(index)))
            .toList();

    final SpecVersion specVersion = spec.atSlot(slotAndBlockRoot.getSlot());
    final MiscHelpersDeneb miscHelpersDeneb =
        specVersion.miscHelpers().toVersionDeneb().orElseThrow();
    final SszList<SszKZGCommitment> sszKZGCommitments = recoveryTask.sszKZGCommitments();

    final List<VersionedHash> versionedHashes =
        missingBlobsIdentifiers.stream()
            .map(
                blobIdentifier ->
                    miscHelpersDeneb.kzgCommitmentToVersionedHash(
                        sszKZGCommitments
                            .get(blobIdentifier.getIndex().intValue())
                            .getKZGCommitment()))
            .toList();

    return executionLayer
        .engineGetBlobs(versionedHashes, slotAndBlockRoot.getSlot())
        .thenAccept(
            blobAndProofs -> {
              checkArgument(
                  blobAndProofs.size() == versionedHashes.size(),
                  "Queried %s versionedHashed but got %s blobAndProofs",
                  versionedHashes.size(),
                  blobAndProofs.size());

              for (int index = 0; index < blobAndProofs.size(); index++) {
                final Optional<BlobAndProof> blobAndProof = blobAndProofs.get(index);
                final BlobIdentifier blobIdentifier = missingBlobsIdentifiers.get(index);
                if (blobAndProof.isEmpty()) {
                  LOG.debug(
                      "Blob not found on local EL: {}, reconstruction not possible",
                      blobIdentifier);
                  return;
                }
              }

              LOG.info(
                  "Collected all blobSidecars from EL for slot {}, recovering data column sidecars",
                  slotAndBlockRoot.getSlot());
              publishRecoveredDataColumnSidecars(
                  recoveryTask,
                  blobAndProofs.stream()
                      .filter(Optional::isPresent)
                      .map(blobAndProof -> blobAndProof.get().blob())
                      .toList());
            });
  }

  private void logLocalElBlobsLookupFailure(final Throwable error) {
    LOG.warn("Local EL blobs lookup failed: {}", getRootCauseMessage(error));
  }

  record RecoveryTask(
      SignedBeaconBlockHeader signedBeaconBlockHeader,
      SszList<SszKZGCommitment> sszKZGCommitments,
      List<Bytes32> kzgCommitmentsInclusionProof) {
    public SlotAndBlockRoot getSlotAndBlockRoot() {
      return signedBeaconBlockHeader.getMessage().getSlotAndBlockRoot();
    }
  }
}
