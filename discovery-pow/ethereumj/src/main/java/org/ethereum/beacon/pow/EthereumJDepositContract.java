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

package org.ethereum.beacon.pow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.schedulers.LatestExecutor;
import org.ethereum.beacon.schedulers.Schedulers;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Bloom;
import org.ethereum.core.CallTransaction.Contract;
import org.ethereum.core.CallTransaction.Invocation;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.HashUtil;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.SyncStatus.SyncStage;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.vm.LogInfo;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.pegasys.artemis.ethereum.core.Address;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.Bytes8;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.uint.UInt64;

public class EthereumJDepositContract extends AbstractDepositContract {
  private static final Logger logger = LoggerFactory.getLogger(EthereumJDepositContract.class);

  private static final String DEPOSIT_EVENT_NAME = "DepositEvent";

  private final LatestExecutor<Long> blockExecutor;

  private final Ethereum ethereum;
  private final Address contractDeployAddress;
  private final Hash32 contractDeployAddressHash;
  private final long contractDeployBlock;
  private final Bloom contractAddressBloom;
  private final Contract contract;

  private volatile long processedUpToBlock;
  private boolean chainStartComplete;

  public EthereumJDepositContract(
      Ethereum ethereum,
      long contractDeployBlock,
      String contractDeployAddress,
      Schedulers schedulers,
      Function<BytesValue, Hash32> hashFunction,
      int merkleTreeDepth,
      BeaconChainSpec spec) {
    super(schedulers, hashFunction, merkleTreeDepth, spec);
    this.ethereum = ethereum;
    this.contractDeployAddress = Address.fromHexString(contractDeployAddress);
    contractDeployAddressHash =
        Hash32.wrap(Bytes32.wrap(HashUtil.sha3(this.contractDeployAddress.extractArray())));
    this.contractAddressBloom = Bloom.create(contractDeployAddressHash.extractArray());
    this.contract = new Contract(ContractAbi.getContractAbi());
    this.contractDeployBlock = contractDeployBlock;
    processedUpToBlock = contractDeployBlock;
    blockExecutor = new LatestExecutor<>(this.schedulers.blocking(), this::processBlocksUpTo);
  }

  @Override
  protected void chainStartSubscribed() {
    ethereum.addListener(
        new EthereumListenerAdapter() {
          @Override
          public void onSyncDone(SyncState state) {
            if (state == SyncState.COMPLETE) {
              onEthereumUpdated();
            }
          }

          @Override
          public void onBlock(Block block, List<TransactionReceipt> receipts) {
            onEthereumUpdated();
          }
        });

    if (ethereum.getSyncStatus().getStage() == SyncStage.Complete) {
      processConfirmedBlocks();
    }
  }

  @Override
  protected void chainStartDone() {
    chainStartComplete = true;
  }

  private void onEthereumUpdated() {
    processConfirmedBlocks();
  }

  protected long getBestConfirmedBlock() {
    return ethereum.getBlockchain().getBestBlock().getNumber() - getDistanceFromHead();
  }

  protected void processConfirmedBlocks() {
    long bestConfirmedBlock = getBestConfirmedBlock();
    blockExecutor.newEvent(bestConfirmedBlock);
  }

  protected void processBlocksUpTo(long bestConfirmedBlock) {
    try {
      for (long number = processedUpToBlock; number <= bestConfirmedBlock; number++) {
        Block block = ethereum.getBlockchain().getBlockByNumber(number);
        onConfirmedBlock(block);
        processedUpToBlock = number + 1;
      }
    } catch (Exception e) {
      logger.error("Error processing blocks: ", e);
    }
  }

  private List<TransactionReceipt> getBlockTransactionReceipts(Block block) {
    Blockchain blockchain = (Blockchain) ethereum.getBlockchain();
    return block.getTransactionsList().stream()
        .map(tx -> blockchain.getTransactionInfo(tx.getHash()).getReceipt())
        .collect(Collectors.toList());
  }

  private synchronized void onConfirmedBlock(Block block) {
    List<DepositEventData> depositEventDataList = new ArrayList<>();
    for (Invocation invocation : getContractEvents(block)) {
      if (DEPOSIT_EVENT_NAME.equals(invocation.function.name)) {
        depositEventDataList.add(createDepositEventData(invocation));
      }
    }
    if (!depositEventDataList.isEmpty()) {
      newDeposits(depositEventDataList, block.getHash(), block.getTimestamp());
    }
  }

  /**
   * @param depositEvent Deposit: event({ pubkey: bytes[48], withdrawal_credentials: bytes[32],
   *     amount: bytes[8], signature: bytes[96], merkle_tree_index: bytes[8], })
   */
  private DepositEventData createDepositEventData(Invocation depositEvent) {
    return new DepositEventData(
        (byte[]) depositEvent.args[0],
        (byte[]) depositEvent.args[1],
        (byte[]) depositEvent.args[2],
        (byte[]) depositEvent.args[3],
        (byte[]) depositEvent.args[4]);
  }

  private List<Invocation> getContractEvents(Block block) {
    if (!new Bloom(block.getLogBloom()).matches(contractAddressBloom)) {
      return Collections.emptyList();
    }
    List<Invocation> ret = new ArrayList<>();

    List<TransactionReceipt> receipts = getBlockTransactionReceipts(block);
    for (TransactionReceipt receipt : receipts) {
      for (LogInfo logInfo : receipt.getLogInfoList()) {
        if (Arrays.equals(logInfo.getAddress(), contractDeployAddress.getArrayUnsafe())) {
          ret.add(contract.parseEvent(logInfo));
        }
      }
    }

    return ret;
  }

  @Override
  protected boolean hasDepositRootImpl(byte[] blockHash, byte[] depositRoot) {
    Block block = ethereum.getBlockchain().getBlockByHash(blockHash);
    if (block == null) {
      return false;
    }
    if (block.getNumber() > getBestConfirmedBlock()) {
      return false;
    }

    return getContractEvents(block).stream()
        .filter(invocation -> DEPOSIT_EVENT_NAME.equals(invocation.function.name))
        .anyMatch(invocation -> Arrays.equals((byte[]) invocation.args[0], depositRoot));
  }

  @Override
  protected synchronized Optional<Triplet<byte[], Integer, byte[]>>
      getLatestBlockHashDepositRoot() {
    long bestBlock = getBestConfirmedBlock();
    for (long blockNum = bestBlock; blockNum >= contractDeployBlock; blockNum--) {
      Block block = ethereum.getBlockchain().getBlockByNumber(blockNum);
      List<Invocation> contractEvents = getContractEvents(block);
      Collections.reverse(contractEvents);
      for (Invocation contractEvent : contractEvents) {
        byte[] merkleTreeIndex = (byte[]) contractEvent.args[4];
        return Optional.of(
            Triplet.with(
                getDepositRoot(merkleTreeIndex).extractArray(),
                UInt64.fromBytesLittleEndian(Bytes8.wrap(merkleTreeIndex)).increment().intValue(),
                block.getHash()));
      }
    }
    return Optional.empty();
  }

  @Override
  protected List<Pair<byte[], List<DepositEventData>>> peekDepositsImpl(
      int count, byte[] startBlockHash, byte[] endBlockHash) {
    List<Pair<byte[], List<DepositEventData>>> ret = new ArrayList<>();
    Block startBlock = ethereum.getBlockchain().getBlockByHash(startBlockHash);
    Block endBlock = ethereum.getBlockchain().getBlockByHash(endBlockHash);

    Iterator<Pair<Block, List<DepositEventData>>> iterator =
        iterateDepositEvents(startBlock, endBlock);
    boolean started = false;
    while (iterator.hasNext()) {
      if (Arrays.equals(startBlockHash, iterator.next().getValue0().getHash())) {
        started = true;
        break;
      }
    }

    if (!started) {
      throw new IllegalStateException("Starting depositRoot not found");
    }

    while (iterator.hasNext() && count > 0) {
      Pair<Block, List<DepositEventData>> event = iterator.next();
      ret.add(Pair.with(event.getValue0().getHash(), event.getValue1()));
      count--;
      if (Arrays.equals(endBlockHash, event.getValue0().getHash())) {
        break;
      }
    }

    return ret;
  }

  private Iterator<Pair<Block, List<DepositEventData>>> iterateDepositEvents(
      Block fromInclusive, Block tillInclusive) {
    return new Iterator<Pair<Block, List<DepositEventData>>>() {
      Iterator<List<Invocation>> iterator = Collections.emptyIterator();
      Block curBlock;

      @Override
      public boolean hasNext() {
        while (!iterator.hasNext()) {
          if (curBlock == null) {
            curBlock = fromInclusive;
          } else {
            if (curBlock.getNumber() >= tillInclusive.getNumber()) {
              return false;
            }
            if (getBestConfirmedBlock() <= curBlock.getNumber()) {
              return false;
            }
            curBlock = ethereum.getBlockchain().getBlockByNumber(curBlock.getNumber() + 1);
          }
          List<Invocation> cur =
              getContractEvents(curBlock).stream()
                  .filter(invocation -> DEPOSIT_EVENT_NAME.equals(invocation.function.name))
                  .collect(Collectors.toList());
          if (!cur.isEmpty()) {
            List<List<Invocation>> iteratorList = new ArrayList<>();
            iteratorList.add(cur);
            iterator = iteratorList.iterator();
          }
        }
        return true;
      }

      @Override
      public Pair<Block, List<DepositEventData>> next() {
        return Pair.with(
            curBlock,
            iterator.next().stream()
                .map(i -> createDepositEventData(i))
                .collect(Collectors.toList()));
      }
    };
  }
}
