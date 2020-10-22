/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.sync.multipeer.batches;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.StubSyncSource;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.sync.multipeer.BatchImporter.BatchImportResult;
import tech.pegasys.teku.sync.multipeer.chains.TargetChain;

public class StubBatchFactory extends BatchFactory implements Iterable<Batch> {
  private final List<Batch> batches = new ArrayList<>();
  private final Map<Batch, BatchSupport> batchSupports = new HashMap<>();

  private final EventThread eventThread;
  private final boolean enforceEventThread;

  public StubBatchFactory(final EventThread eventThread, final boolean enforceEventThread) {
    super(eventThread, null);
    this.eventThread = eventThread;
    this.enforceEventThread = enforceEventThread;
  }

  public Batch get(final int index) {
    return batches.get(index);
  }

  public List<Batch> clearBatchList() {
    final ArrayList<Batch> previousBatches = new ArrayList<>(this.batches);
    batches.clear();
    return previousBatches;
  }

  public Batch getEventThreadOnlyBatch(final Batch batch) {
    return batchSupports.get(batch).eventThreadOnlyBatch;
  }

  public SafeFuture<BatchImportResult> getImportResult(final Batch batch) {
    return batchSupports.get(batch).importResult;
  }

  public void receiveBlocks(final Batch batch, final SignedBeaconBlock... blocks) {
    batchSupports.get(batch).syncSource.receiveBlocks(blocks);
  }

  public void assertMarkedInvalid(final Batch batch) {
    assertThat(batchSupports.get(batch).markedInvalid)
        .withFailMessage("Expected batch %s to have been marked invalid but wasn't", batch)
        .isTrue();
  }

  public void assertNotMarkedInvalid(final Batch batch) {
    assertThat(batchSupports.get(batch).markedInvalid)
        .withFailMessage("Expected batch %s to not have been marked invalid but was", batch)
        .isFalse();
  }

  public void assertMarkedContested(final Batch batch) {
    assertThat(batchSupports.get(batch).markedContested)
        .withFailMessage("Expected batch %s to have been marked invalid but wasn't", batch)
        .isTrue();
  }

  @Override
  public Batch createBatch(final TargetChain chain, final UInt64 start, final UInt64 count) {
    final BatchSupport support = new BatchSupport(eventThread, chain, start, count);
    batches.add(support.batch);
    // Can look up batch support by either the wrapped or unwrapped batch
    batchSupports.put(support.batch, support);
    batchSupports.put(support.eventThreadOnlyBatch, support);
    return enforceEventThread ? support.eventThreadOnlyBatch : support.batch;
  }

  public int size() {
    return batches.size();
  }

  @Override
  public Iterator<Batch> iterator() {
    return batches.iterator();
  }

  private static final class BatchSupport
      implements SyncSourceSelector, ConflictResolutionStrategy {

    private final SafeFuture<BatchImportResult> importResult = new SafeFuture<>();
    private final StubSyncSource syncSource = new StubSyncSource();
    private final Batch batch;
    private final Batch eventThreadOnlyBatch;
    private boolean markedInvalid = false;
    private boolean markedContested = false;

    public BatchSupport(
        final EventThread eventThread,
        final TargetChain chain,
        final UInt64 start,
        final UInt64 count) {
      batch = new SyncSourceBatch(eventThread, this, this, chain, start, count);
      eventThreadOnlyBatch = new EventThreadOnlyBatch(eventThread, batch);
    }

    @Override
    public void verifyBatch(final Batch batch, final SyncSource originalSource) {
      markedContested = true;
      batch.markAsInvalid();
    }

    @Override
    public void reportInvalidBatch(final Batch batch, final SyncSource source) {
      markedInvalid = true;
    }

    @Override
    public void reportConfirmedBatch(final Batch batch, final SyncSource source) {}

    @Override
    public Optional<SyncSource> selectSource() {
      return Optional.of(syncSource);
    }
  }
}
