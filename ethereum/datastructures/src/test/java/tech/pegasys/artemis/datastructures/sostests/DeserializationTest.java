package tech.pegasys.artemis.datastructures.sostests;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttestationDataAndCustodyBit;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.Transfer;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.CompactCommittee;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.HistoricalBatch;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DeserializationTest {
  @Test
  void isBeaconBlockBodyVariableTest() {
   // assertEquals(true, SimpleOffsetSerializer.classReflectionInfo.get(BeaconBlockBody.class).isVariable());
  }

  @Test
  void BeaconBlockHeaderTest() {
    BeaconBlockHeader beaconBlockHeader = DataStructureUtil.randomBeaconBlockHeader(100);
    Bytes beaconBlockSerialized = SimpleOffsetSerializer.serialize(beaconBlockHeader);
    BeaconBlockHeader newBeaconBlockHeader = SimpleOffsetSerializer.deserialize(beaconBlockSerialized, BeaconBlockHeader.class);
    assertEquals(beaconBlockHeader, newBeaconBlockHeader);
  }

  @Test
  void isBeaconBlockVariableTest() {
   // assertEquals(true, SimpleOffsetSerializer.classReflectionInfo.get(BeaconBlock.class).isVariable());
  }

  @Test
  void Eth1DataTest() {
    Eth1Data eth1Data = DataStructureUtil.randomEth1Data(100);
    Bytes eth1DataSerialized = SimpleOffsetSerializer.serialize(eth1Data);
    Eth1Data newEth1Data = SimpleOffsetSerializer.deserialize(eth1DataSerialized, Eth1Data.class);
    assertEquals(eth1Data, newEth1Data);
  }

  @Test
  void AttestationDataAndCustodyBitTest() {
    AttestationDataAndCustodyBit attestationDataAndCustodyBit = new AttestationDataAndCustodyBit(
            DataStructureUtil.randomAttestationData(100),
            true);
    Bytes attestationDataAndCustodyBitSerialized = SimpleOffsetSerializer.serialize(attestationDataAndCustodyBit);
    AttestationDataAndCustodyBit newObject = SimpleOffsetSerializer.deserialize(attestationDataAndCustodyBitSerialized , AttestationDataAndCustodyBit.class);
    assertEquals(attestationDataAndCustodyBit, newObject);
  }

  @Test
  void isAttestationDataVariableTest() {
    //assertEquals(false, SimpleOffsetSerializer.classReflectionInfo.get(AttestationData.class).isVariable());
  }

  @Test
  void isAttestationVariableTest() {
    //assertEquals(false, SimpleOffsetSerializer.classReflectionInfo.get(Attestation.class).isVariable());
  }

  @Test
  void isAttesterSlashingVariableTest() {
    //assertEquals(true, SimpleOffsetSerializer.classReflectionInfo.get(AttesterSlashing.class).isVariable());
  }

  @Test
  void isDepositDataVariableTest() {
    //assertEquals(false, SimpleOffsetSerializer.classReflectionInfo.get(DepositData.class).isVariable());
  }

  @Test
  void DepositTest() {
    Deposit deposit = DataStructureUtil.randomDeposit(100);
    Bytes serialized = SimpleOffsetSerializer.serialize(deposit);
    Deposit newDeposit = SimpleOffsetSerializer.deserialize(serialized, Deposit.class);
    assertEquals(deposit, newDeposit);
  }

  @Test
  void isIndexedAttestationVariableTest() {
    //assertEquals(true, SimpleOffsetSerializer.classReflectionInfo.get(IndexedAttestation.class).isVariable());
  }

  @Test
  void isProposerSlashingVariableTest() {
    //assertEquals(false, SimpleOffsetSerializer.classReflectionInfo.get(ProposerSlashing.class).isVariable());
  }

  @Test
  void isTransferVariableTest() {
    //assertEquals(false, SimpleOffsetSerializer.classReflectionInfo.get(Transfer.class).isVariable());
  }

  @Test
  void isVoluntaryExitVariableTest() {
    //assertEquals(false, SimpleOffsetSerializer.classReflectionInfo.get(VoluntaryExit.class).isVariable());
  }

  @Test
  void isBeaconStateVariableTest() {
    //assertEquals(true, SimpleOffsetSerializer.classReflectionInfo.get(BeaconState.class).isVariable());
  }

  @Test
  void isCheckpointVariableTest() {
    Checkpoint checkpoint = DataStructureUtil.randomCheckpoint(100);
    Bytes checkpointSerialized = SimpleOffsetSerializer.serialize(checkpoint);
    Checkpoint newCheckpoint = SimpleOffsetSerializer.deserialize(checkpointSerialized, Checkpoint.class);
    assertEquals(checkpoint, newCheckpoint);
  }

  @Test
  void isCompactCommitteVariableTest() {
  //  assertEquals(true, SimpleOffsetSerializer.classReflectionInfo.get(CompactCommittee.class).isVariable());
  }

  @Test
  void isCrosslinkVariableTest() {
   // assertEquals(false, SimpleOffsetSerializer.classReflectionInfo.get(Crosslink.class).isVariable());
  }

  @Test
  void ForkTest() {
    //assertEquals(false, SimpleOffsetSerializer.classReflectionInfo.get(Fork.class).isVariable());
  }

  @Test
  void HistoricalBatchTest() {
    List<Bytes32> block_roots = new ArrayList<>();
    List<Bytes32> state_roots = new ArrayList<>();
    IntStream.range(0, Constants.SLOTS_PER_HISTORICAL_ROOT).forEach(i -> {
              block_roots.add(DataStructureUtil.randomBytes32(i));
              state_roots.add(DataStructureUtil.randomBytes32(i));
            });
    HistoricalBatch deposit = new HistoricalBatch(
            new SSZVector<>(block_roots),
            new SSZVector<>(state_roots));
    Bytes serialized = SimpleOffsetSerializer.serialize(deposit);
    HistoricalBatch newDeposit = SimpleOffsetSerializer.deserialize(serialized, HistoricalBatch.class);
    assertEquals(deposit, newDeposit);
  }

  @Test
  void isPendingAttestationVariableTest() {
    //assertEquals(false, SimpleOffsetSerializer.classReflectionInfo.get(PendingAttestation.class).isVariable());
  }

  @Test
  void isValidatorVariableTest() {
    //assertEquals(false, SimpleOffsetSerializer.classReflectionInfo.get(Validator.class).isVariable());
  }
}