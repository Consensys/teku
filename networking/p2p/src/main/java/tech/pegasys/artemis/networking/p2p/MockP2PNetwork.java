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

package tech.pegasys.artemis.networking.p2p;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.ProposalSignedData;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.CasperSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositInput;
import tech.pegasys.artemis.datastructures.operations.Exit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.SlashableVoteData;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;

public class MockP2PNetwork implements P2PNetwork {

  private final EventBus eventBus;
  private static final Logger LOG = LogManager.getLogger();

  public MockP2PNetwork(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  /**
   * Returns a snapshot of the currently connected peer connections.
   *
   * @return Peers currently connected.
   */
  @Override
  public Collection<?> getPeers() {
    return null;
  }

  /**
   * Connects to a {@link String Peer}.
   *
   * @param peer Peer to connect to.
   * @return Future of the established {}
   */
  @Override
  public CompletableFuture<?> connect(String peer) {
    return null;
  }

  /**
   * Subscribe a to all incoming events.
   *
   * @param event to subscribe to.
   */
  @Override
  public void subscribe(String event) {}

  /** Stops the P2P network layer. */
  @Override
  public void stop() {}

  /**
   * Checks if the node is listening for network connections
   *
   * @return true if the node is listening for network connections, false, otherwise.
   */
  @Override
  public boolean isListening() {
    return false;
  }

  @Override
  public void run() {
    this.simulateNewMessages();
    // this.simulateNewAttestations();
  }

  @Override
  public void close() {
    this.stop();
  }

  private void simulateNewMessages() {
    try {
      while (true) {
        Random random = new Random();
        long n = 1000L * Integer.toUnsignedLong(random.nextInt(7) + 2);
        Thread.sleep(n);
        if (n % 3 == 0) {
          BeaconBlock block = createEmptyBeaconBlock(n);
          this.eventBus.post(block);
        } else {
          Attestation attestation = createEmptyAttestation(n);
          this.eventBus.post(attestation);
        }
      }
    } catch (InterruptedException e) {
      LOG.warn(e.toString());
    }
  }

  // TODO: These helper methods below almost certianly belong somewhere else.
  private Attestation createEmptyAttestation(long slotNum) {
    return new Attestation(
        createEmptyAttestationData(slotNum), Bytes32.ZERO, Bytes32.ZERO, Constants.EMPTY_SIGNATURE);
  }

  private AttestationData createEmptyAttestationData(long slotNum) {
    return new AttestationData(
        slotNum,
        UnsignedLong.ZERO,
        Bytes32.ZERO,
        Bytes32.ZERO,
        Bytes32.ZERO,
        Bytes32.ZERO,
        UnsignedLong.ZERO,
        Bytes32.ZERO);
  }

  private BeaconBlock createEmptyBeaconBlock(long slotNum) {
    return new BeaconBlock(
        slotNum,
        Arrays.asList(Bytes32.ZERO),
        Bytes32.ZERO,
        Constants.EMPTY_SIGNATURE,
        new Eth1Data(Bytes32.ZERO, Bytes32.ZERO),
        Constants.EMPTY_SIGNATURE,
        createEmptyBeaconBlockBody(slotNum));
  }

  private BeaconBlockBody createEmptyBeaconBlockBody(long slotNum) {
    return new BeaconBlockBody(
        Arrays.asList(createEmptyAttestation(slotNum)),
        Arrays.asList(createEmptyProposerSlashing()),
        Arrays.asList(createEmptyCasperSlashing(slotNum)),
        Arrays.asList(createEmptyDeposit()),
        Arrays.asList(createEmptyExit()));
  }

  private ProposerSlashing createEmptyProposerSlashing() {
    return new ProposerSlashing(
        0,
        createEmptyProposalSignedData(),
        Constants.EMPTY_SIGNATURE,
        createEmptyProposalSignedData(),
        Constants.EMPTY_SIGNATURE);
  }

  private ProposalSignedData createEmptyProposalSignedData() {
    return new ProposalSignedData(UnsignedLong.ZERO, UnsignedLong.ZERO, Bytes32.ZERO);
  }

  private CasperSlashing createEmptyCasperSlashing(long slotNum) {
    return new CasperSlashing(
        createEmptySlashableVoteData(slotNum), createEmptySlashableVoteData(slotNum));
  }

  private SlashableVoteData createEmptySlashableVoteData(long slotNum) {
    return new SlashableVoteData(
        Arrays.asList(0),
        Arrays.asList(0),
        createEmptyAttestationData(slotNum),
        Constants.EMPTY_SIGNATURE);
  }

  private Deposit createEmptyDeposit() {
    return new Deposit(Arrays.asList(Bytes32.ZERO), UnsignedLong.ZERO, createEmptyDepositData());
  }

  private DepositData createEmptyDepositData() {
    return new DepositData(createEmptyDepositInput(), UnsignedLong.ZERO, UnsignedLong.ZERO);
  }

  private DepositInput createEmptyDepositInput() {
    return new DepositInput(Bytes48.ZERO, Bytes32.ZERO, new ArrayList<Bytes48>());
  }

  private Exit createEmptyExit() {
    return new Exit(UnsignedLong.ZERO, UnsignedLong.ZERO, Constants.EMPTY_SIGNATURE);
  }
}
