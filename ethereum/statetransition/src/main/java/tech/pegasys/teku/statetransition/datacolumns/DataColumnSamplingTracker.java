package tech.pegasys.teku.statetransition.datacolumns;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

record DataColumnSamplingTracker(
        UInt64 slot,
        Bytes32 blockRoot,
        List<UInt64> samplingRequirement,
        Set<UInt64> missingColumns,
        SafeFuture<List<UInt64>> completionFuture) {
  private static final Logger LOG = LogManager.getLogger();

  static DataColumnSamplingTracker create(
          final UInt64 slot,
          final Bytes32 blockRoot,
          final CustodyGroupCountManager custodyGroupCountManager) {
    final List<UInt64> samplingRequirement = custodyGroupCountManager.getSamplingColumnIndices();
    final Set<UInt64> missingColumns = ConcurrentHashMap.newKeySet(samplingRequirement.size());
    missingColumns.addAll(samplingRequirement);
    return new DataColumnSamplingTracker(
            slot, blockRoot, samplingRequirement, missingColumns, new SafeFuture<>());
  }

  boolean add(
          final DataColumnSlotAndIdentifier columnIdentifier, final RemoteOrigin origin) {
    if (!slot.equals(columnIdentifier.slot())
            || !blockRoot.equals(columnIdentifier.blockRoot())) {
      return false;
    }

    LOG.debug("Adding column {} to sampling tracker", columnIdentifier);
    final boolean removed =
            missingColumns.removeIf(idx -> idx.equals(columnIdentifier.columnIndex()));
    if (!removed) {
      LOG.debug("Column {} was already marked as received, origin: {}", columnIdentifier, origin);
      return false;
    }

    if (missingColumns.isEmpty()) {
      LOG.info(
              "Sampling complete for slot {} root {} via column {} received via {}",
              slot,
              blockRoot,
              columnIdentifier.columnIndex(),
              origin);
      completionFuture.complete(samplingRequirement);
    } else {
      LOG.debug(
              "Sampling still pending for slot {} root {}, remaining columns: {}",
              slot,
              blockRoot,
              missingColumns);
    }

    return true;
  }

  List<DataColumnSlotAndIdentifier> getMissingColumnIdentifiers() {
    return missingColumns.stream()
            .map(idx -> new DataColumnSlotAndIdentifier(slot, blockRoot, idx))
            .toList();
  }
}
