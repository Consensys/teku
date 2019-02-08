/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.storage;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import net.consensys.cava.bytes.Bytes;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;

/** TThis class is the ChainStorage client-side logic */
public class ChainStorageClient implements ChainStorage {

  protected LinkedBlockingQueue<BeaconBlock> unprocessedBlocks;
  protected HashMap<Bytes, Bytes> processedBlocks;
  protected LinkedBlockingQueue<Attestation> unprocessedAttestations;
  protected EventBus eventBus;

  public ChainStorageClient() {}

  public ChainStorageClient(EventBus eventBus) {
    this.unprocessedBlocks = new LinkedBlockingQueue<BeaconBlock>();
    this.unprocessedAttestations = new LinkedBlockingQueue<Attestation>();
    this.processedBlocks = new HashMap<Bytes, Bytes>();
    this.eventBus = eventBus;
    this.eventBus.register(this);
  }

  @Override
  public void addProcessedBlock(Bytes blockHash, BeaconBlock block) {
    ChainStorage.<Bytes, HashMap<Bytes, Bytes>>add(blockHash, block.toBytes(), processedBlocks);
    LOG.info("BlockHash: " + blockHash.toHexString() + " added to ChainStorage.");
    // todo: post event to eventbus to notify the server that a new processed block has been added
  }

  @Override
  public void addUnprocessedBlock(BeaconBlock block) {
    ChainStorage.<BeaconBlock, LinkedBlockingQueue<BeaconBlock>>add(block, unprocessedBlocks);
  }

  @Override
  public void addUnprocessedAttestation(Attestation attestation) {
    ChainStorage.<Attestation, LinkedBlockingQueue<Attestation>>add(
        attestation, unprocessedAttestations);
  }

  @Override
  public Optional<Bytes> getProcessedBlock(Bytes blockHash) {
    return ChainStorage.<Bytes, HashMap<Bytes, Bytes>>get(blockHash, processedBlocks);
  }

  @Override
  public Optional<BeaconBlock> getUnprocessedBlock() {
    return ChainStorage.<BeaconBlock, LinkedBlockingQueue<BeaconBlock>>remove(unprocessedBlocks);
  }

  @Override
  public Optional<Attestation> getUnprocessedAttestation() {
    return ChainStorage.<Attestation, LinkedBlockingQueue<Attestation>>remove(
        unprocessedAttestations);
  }

  @Subscribe
  public void onNewUnprocessedBlock(BeaconBlock block) {
    LOG.info("ChainStorage: new unprocessed BeaconBlock detected");
    addUnprocessedBlock(block);
  }

  @Subscribe
  public void onNewUnprocessedAttestation(Attestation attestation) {
    LOG.info("ChainStorage: new unprocessed Attestation detected");
    addUnprocessedAttestation(attestation);
  }
}
