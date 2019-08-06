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

package pegasys.artemis.reference;

import com.google.common.primitives.UnsignedLong;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP2;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
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
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.mikuli.G2Point;
import tech.pegasys.artemis.util.mikuli.PublicKey;
import tech.pegasys.artemis.util.mikuli.SecretKey;
import tech.pegasys.artemis.util.mikuli.Signature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MapObjectUtil {

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Object convertMapToTypedObject(Class classtype, Object object) {
    if (classtype.equals(Attestation.class)) return MapObjectUtil.getAttestation((Map) object);
    else if (classtype.equals(AttestationData.class)) return getAttestationData((Map) object);
    else if (classtype.equals(AttesterSlashing.class)) return getAttesterSlashing((Map) object);
    else if (classtype.equals(AttestationDataAndCustodyBit.class)) return getAttestationDataAndCustodyBit((Map) object);
    else if (classtype.equals(BeaconBlock.class)) return getBeaconBlock((Map) object);
    else if (classtype.equals(BeaconBlockBody.class)) return getBeaconBlockBody((Map) object);
    else if (classtype.equals(BeaconBlockHeader.class)) return getBeaconBlockHeader((Map) object);
    else if (classtype.equals(Bytes[].class)) return getBytesArray((List) object);
    else if (classtype.equals(Bytes32[].class)) return getBytes32Array((List) object);
    else if (classtype.equals(BeaconState.class)) return getBeaconState((Map) object);
    else if (classtype.equals(Checkpoint.class)) return getCheckpoint((Map) object);
    else if (classtype.equals(CompactCommittee.class)) return getCompactCommittee((Map) object);
    else if (classtype.equals(Crosslink.class)) return getCrossLink((Map) object);
    else if (classtype.equals(Deposit.class)) return getDeposit((Map) object);
    else if (classtype.equals(DepositData.class)) return getDepositData((Map) object);
    else if (classtype.equals(Eth1Data.class)) return getEth1Data((Map) object);
    else if (classtype.equals(Fork.class)) return getFork((Map) object);
    else if (classtype.equals(G2Point.class)) return getG2Point(object);
    else if (classtype.equals(HistoricalBatch.class)) return getHistoricalBatch((Map) object);
    else if (classtype.equals(IndexedAttestation.class)) return getIndexedAttestation((Map) object);
    else if (classtype.equals(PendingAttestation.class)) return getPendingAttestation((Map) object);
    else if (classtype.equals(ProposerSlashing.class)) return getProposerSlashing((Map) object);
    else if (classtype.equals(PublicKey.class)) return PublicKey.fromBytesCompressed(Bytes.fromHexString(object.toString()));
    else if (classtype.equals(PublicKey[].class)) return getPublicKeyArray((List) object);
    else if (classtype.equals(SecretKey.class)) return  SecretKey.fromBytes(Bytes48.leftPad(Bytes.fromHexString(object.toString())));
    else if (classtype.equals(Signature[].class)) return getSignatureArray((List) object);
    else if (classtype.equals(Transfer.class)) return getTransfer((Map) object);
    else if (classtype.equals(Validator.class)) return getValidator((Map) object);
    else if (classtype.equals(VoluntaryExit.class)) return getVoluntaryExit((Map) object);
    else if (classtype.equals(Bytes32.class)) return Bytes32.fromHexString(object.toString());
    else if (classtype.equals(Bytes.class)) return Bytes.fromHexString(object.toString());

    return null;
  }

  private static G2Point getG2Point(Object object) {
    if(object.getClass().equals(ArrayList.class))return makePoint((List)object);
    return G2Point.hashToG2(Bytes.fromHexString(((Map) object).get("message").toString()), Bytes.fromHexString(((Map) object).get("domain").toString()));
  }

  /**
   * Utility for converting uncompressed test case data to a point
   *
   * <p>The test case data is not in standard form (Z = 1). This routine converts the input to a
   * point and applies the affine transformation. This routine is for uncompressed input.
   *
   * @param coords an array of strings {xRe, xIm, yRe, yIm, zRe, zIm}
   * @return the point corresponding to the input
   */
  private static G2Point makePoint(List list) {
    BIG xRe = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(((List)list.get(0)).get(0).toString())).toArray());
    BIG xIm = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(((List)list.get(0)).get(1).toString())).toArray());
    BIG yRe = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(((List)list.get(1)).get(0).toString())).toArray());
    BIG yIm = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(((List)list.get(1)).get(1).toString())).toArray());
    BIG zRe = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(((List)list.get(2)).get(0).toString())).toArray());
    BIG zIm = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(((List)list.get(2)).get(1).toString())).toArray());

    FP2 x = new FP2(xRe, xIm);
    FP2 y = new FP2(yRe, yIm);
    FP2 z = new FP2(zRe, zIm);

    // Normalise the point (affine transformation) so that Z = 1
    z.inverse();
    x.mul(z);
    x.reduce();
    y.mul(z);
    y.reduce();

    return new G2Point(new ECP2(x, y));
  }

  private static List<Bytes> getBytesArray(List list) {
    return (List<Bytes>)list.stream().map(object -> Bytes.fromHexString(object.toString())).collect(Collectors.toList());
  }

  private static List<PublicKey> getPublicKeyArray(List list) {
    return (List<PublicKey>) list.stream().map(object -> PublicKey.fromBytesCompressed(Bytes.fromHexString(object.toString()))).collect(Collectors.toList());
  }

  private static List<Signature> getSignatureArray(List list) {
    return (List<Signature>) list.stream().map(object -> Signature.fromBytesCompressed(Bytes.fromHexString(object.toString()))).collect(Collectors.toList());
  }

  private static List<Bytes32> getBytes32Array(List list) {
    return (List<Bytes32>)list.stream().map(object -> Bytes32.fromHexString(object.toString())).collect(Collectors.toList());
  }

  private static HistoricalBatch getHistoricalBatch(Map map) {
    List<Bytes32> block_roots =
        new ArrayList<Bytes32>(
            ((ArrayList<String>) map.get("block_roots"))
                .stream()
                    .map(e -> Bytes32.fromHexString(e.toString()))
                    .collect(Collectors.toList()));
    List<Bytes32> state_roots =
        new ArrayList<Bytes32>(
            ((ArrayList<String>) map.get("state_roots"))
                .stream()
                    .map(e -> Bytes32.fromHexString(e.toString()))
                    .collect(Collectors.toList()));

    return new HistoricalBatch(block_roots, state_roots);
  }

  private static CompactCommittee getCompactCommittee(Map map) {
    List<BLSPublicKey> pubkeys =
        new ArrayList<BLSPublicKey>(
            ((ArrayList<String>) map.get("pubkeys"))
                .stream()
                    .map(e -> BLSPublicKey.fromBytes(Bytes.fromHexString(e.toString())))
                    .collect(Collectors.toList()));
    List<UnsignedLong> compact_validators =
        new ArrayList<Integer>((ArrayList<Integer>) map.get("compact_validators"))
            .stream().map(e -> UnsignedLong.valueOf(e.longValue())).collect(Collectors.toList());
    return new CompactCommittee(pubkeys, compact_validators);
  }

  private static BeaconState getBeaconState(Map map) {
    UnsignedLong genesis_time = UnsignedLong.valueOf(map.get("genesis_time").toString());
    UnsignedLong slot = UnsignedLong.valueOf(map.get("slot").toString());
    Fork fork = getFork((Map) map.get("fork"));
    BeaconBlockHeader latest_block_header =
        getBeaconBlockHeader((Map) map.get("latest_block_header"));
    List<Bytes32> block_roots =
        new ArrayList<Bytes32>(
            ((ArrayList<String>) map.get("block_roots"))
                .stream()
                    .map(e -> Bytes32.fromHexString(e.toString()))
                    .collect(Collectors.toList()));
    List<Bytes32> state_roots =
        new ArrayList<Bytes32>(
            ((ArrayList<String>) map.get("state_roots"))
                .stream()
                    .map(e -> Bytes32.fromHexString(e.toString()))
                    .collect(Collectors.toList()));
    List<Bytes32> historical_roots =
        new ArrayList<Bytes32>(
            ((ArrayList<String>) map.get("historical_roots"))
                .stream()
                    .map(e -> Bytes32.fromHexString(e.toString()))
                    .collect(Collectors.toList()));
    Eth1Data eth1_data = getEth1Data((Map) map.get("eth1_data"));
    List<Eth1Data> eth1_data_votes =
        ((List<Map>) map.get("eth1_data_votes"))
            .stream().map(e -> getEth1Data(e)).collect(Collectors.toList());
    UnsignedLong eth1_deposit_index =
        UnsignedLong.valueOf(map.get("eth1_deposit_index").toString());
    List<Validator> validators =
        ((List<Map>) map.get("validators"))
            .stream().map(e -> getValidator(e)).collect(Collectors.toList());
    List<UnsignedLong> balances =
        new ArrayList<Integer>((ArrayList<Integer>) map.get("balances"))
            .stream().map(e -> UnsignedLong.valueOf(e.longValue())).collect(Collectors.toList());
    UnsignedLong start_shard = UnsignedLong.valueOf(map.get("start_shard").toString());
    List<Bytes32> randao_mixes =
        new ArrayList<Bytes32>(
            ((ArrayList<String>) map.get("randao_mixes"))
                .stream()
                    .map(e -> Bytes32.fromHexString(e.toString()))
                    .collect(Collectors.toList()));
    List<Bytes32> active_index_roots =
        new ArrayList<Bytes32>(
            ((ArrayList<String>) map.get("active_index_roots"))
                .stream()
                    .map(e -> Bytes32.fromHexString(e.toString()))
                    .collect(Collectors.toList()));
    List<Bytes32> compact_committees_roots =
        new ArrayList<Bytes32>(
            ((ArrayList<String>) map.get("compact_committees_roots"))
                .stream()
                    .map(e -> Bytes32.fromHexString(e.toString()))
                    .collect(Collectors.toList()));
    List<UnsignedLong> slashings =
        new ArrayList<Integer>((ArrayList<Integer>) map.get("slashings"))
            .stream().map(e -> UnsignedLong.valueOf(e.longValue())).collect(Collectors.toList());
    List<PendingAttestation> previous_epoch_attestations =
        ((List<Map>) map.get("previous_epoch_attestations"))
            .stream().map(e -> getPendingAttestation(e)).collect(Collectors.toList());
    List<PendingAttestation> current_epoch_attestations =
        ((List<Map>) map.get("current_epoch_attestations"))
            .stream().map(e -> getPendingAttestation(e)).collect(Collectors.toList());
    List<Crosslink> previous_crosslinks =
        ((List<Map>) map.get("previous_crosslinks"))
            .stream().map(e -> getCrossLink(e)).collect(Collectors.toList());
    List<Crosslink> current_crosslinks =
        ((List<Map>) map.get("current_crosslinks"))
            .stream().map(e -> getCrossLink(e)).collect(Collectors.toList());
    Bytes justification_bits = Bytes.fromHexString(map.get("justification_bits").toString());
    Checkpoint previous_justified_checkpoint =
        getCheckpoint((Map) map.get("previous_justified_checkpoint"));
    Checkpoint current_justified_checkpoint =
        getCheckpoint((Map) map.get("current_justified_checkpoint"));
    Checkpoint finalized_checkpoint = getCheckpoint((Map) map.get("finalized_checkpoint"));

    return new BeaconState(
        genesis_time,
        slot,
        fork,
        latest_block_header,
        block_roots,
        state_roots,
        historical_roots,
        eth1_data,
        eth1_data_votes,
        eth1_deposit_index,
        validators,
        balances,
        start_shard,
        randao_mixes,
        active_index_roots,
        compact_committees_roots,
        slashings,
        previous_epoch_attestations,
        current_epoch_attestations,
        previous_crosslinks,
        current_crosslinks,
        justification_bits,
        previous_justified_checkpoint,
        current_justified_checkpoint,
        finalized_checkpoint);
  }

  private static PendingAttestation getPendingAttestation(Map map) {
    Bytes aggregation_bits = Bytes.fromHexString(map.get("aggregation_bits").toString());
    AttestationData data = getAttestationData((Map) map.get("data"));
    UnsignedLong inclusion_delay = UnsignedLong.valueOf(map.get("inclusion_delay").toString());
    UnsignedLong proposer_index = UnsignedLong.valueOf(map.get("proposer_index").toString());

    return new PendingAttestation(aggregation_bits, data, inclusion_delay, proposer_index);
  }

  private static Validator getValidator(Map map) {
    BLSPublicKey pubkey = BLSPublicKey.fromBytes(Bytes.fromHexString(map.get("pubkey").toString()));
    Bytes32 withdrawal_credentials =
        Bytes32.fromHexString(map.get("withdrawal_credentials").toString());
    UnsignedLong effective_balance = UnsignedLong.valueOf(map.get("effective_balance").toString());
    ;
    boolean slashed = Boolean.getBoolean((map.get("slashed")).toString());
    UnsignedLong activation_eligibility_epoch =
        UnsignedLong.valueOf(map.get("activation_eligibility_epoch").toString());
    UnsignedLong activation_epoch = UnsignedLong.valueOf(map.get("activation_epoch").toString());
    UnsignedLong exit_epoch = UnsignedLong.valueOf(map.get("exit_epoch").toString());
    UnsignedLong withdrawable_epoch =
        UnsignedLong.valueOf(map.get("withdrawable_epoch").toString());

    return new Validator(
        pubkey,
        withdrawal_credentials,
        effective_balance,
        slashed,
        activation_eligibility_epoch,
        activation_epoch,
        exit_epoch,
        withdrawable_epoch);
  }

  private static Fork getFork(Map map) {
    Bytes previous_version = Bytes.fromHexString(map.get("previous_version").toString());
    Bytes current_version = Bytes.fromHexString(map.get("current_version").toString());
    UnsignedLong epoch = UnsignedLong.valueOf(map.get("epoch").toString());

    return new Fork(previous_version, current_version, epoch);
  }

  private static BeaconBlock getBeaconBlock(Map map) {
    UnsignedLong slot = UnsignedLong.valueOf(map.get("slot").toString());
    Bytes32 parent_root = Bytes32.fromHexString(map.get("parent_root").toString());
    Bytes32 state_root = Bytes32.fromHexString(map.get("state_root").toString());
    BeaconBlockBody body = getBeaconBlockBody((Map) map.get("body"));
    BLSSignature signature =
        BLSSignature.fromBytes(Bytes.fromHexString(map.get("signature").toString()));

    return new BeaconBlock(slot, parent_root, state_root, body, signature);
  }

  private static BeaconBlockBody getBeaconBlockBody(Map map) {
    BLSSignature randao_reveal =
        BLSSignature.fromBytes(Bytes.fromHexString(map.get("randao_reveal").toString()));
    Eth1Data eth1_data = getEth1Data((Map) map.get("eth1_data"));
    Bytes32 graffiti = Bytes32.fromHexString(map.get("graffiti").toString());
    List<ProposerSlashing> proposer_slashings =
        ((List<Map>) map.get("proposer_slashings"))
            .stream().map(e -> getProposerSlashing(e)).collect(Collectors.toList());
    List<AttesterSlashing> attester_slashings =
        ((List<Map>) map.get("attester_slashings"))
            .stream().map(e -> getAttesterSlashing(e)).collect(Collectors.toList());
    List<Attestation> attestations =
        ((List<Map>) map.get("attestations"))
            .stream().map(e -> getAttestation(e)).collect(Collectors.toList());
    List<Deposit> deposits =
        ((List<Map>) map.get("deposits"))
            .stream().map(e -> getDeposit(e)).collect(Collectors.toList());
    List<VoluntaryExit> voluntary_exits =
        ((List<Map>) map.get("voluntary_exits"))
            .stream().map(e -> getVoluntaryExit(e)).collect(Collectors.toList());
    List<Transfer> transfers =
        new ArrayList<Transfer>(
            ((ArrayList<Map>) map.get("transfers"))
                .stream().map(e -> getTransfer(e)).collect(Collectors.toList()));

    return new BeaconBlockBody(
        randao_reveal,
        eth1_data,
        graffiti,
        proposer_slashings,
        attester_slashings,
        attestations,
        deposits,
        voluntary_exits,
        transfers);
  }

  private static Transfer getTransfer(Map map) {
    UnsignedLong sender = UnsignedLong.valueOf(map.get("sender").toString());
    UnsignedLong recipient = UnsignedLong.valueOf(map.get("recipient").toString());
    UnsignedLong amount = UnsignedLong.valueOf(map.get("amount").toString());
    UnsignedLong fee = UnsignedLong.valueOf(map.get("fee").toString());
    UnsignedLong slot = UnsignedLong.valueOf(map.get("slot").toString());
    BLSPublicKey pubkey = BLSPublicKey.fromBytes(Bytes.fromHexString(map.get("pubkey").toString()));
    BLSSignature signature =
        BLSSignature.fromBytes(Bytes.fromHexString(map.get("signature").toString()));

    return new Transfer(sender, recipient, amount, fee, slot, pubkey, signature);
  }

  private static VoluntaryExit getVoluntaryExit(Map map) {
    UnsignedLong epoch = UnsignedLong.valueOf(map.get("epoch").toString());
    UnsignedLong validator_index = UnsignedLong.valueOf(map.get("validator_index").toString());
    BLSSignature signature =
        BLSSignature.fromBytes(Bytes.fromHexString(map.get("signature").toString()));

    return new VoluntaryExit(epoch, validator_index, signature);
  }

  private static Deposit getDeposit(Map map) {
    List<Bytes32> proof =
        new ArrayList<Bytes32>(
            ((ArrayList<String>) map.get("proof"))
                .stream()
                    .map(e -> Bytes32.fromHexString(e.toString()))
                    .collect(Collectors.toList()));
    DepositData data = getDepositData((Map) map.get("data"));

    return new Deposit(proof, data);
  }

  private static DepositData getDepositData(Map map) {
    BLSPublicKey pubkey = BLSPublicKey.fromBytes(Bytes.fromHexString(map.get("pubkey").toString()));
    Bytes32 withdrawal_credentials =
        Bytes32.fromHexString(map.get("withdrawal_credentials").toString());
    UnsignedLong amount = UnsignedLong.valueOf(map.get("amount").toString());
    BLSSignature signature =
        BLSSignature.fromBytes(Bytes.fromHexString(map.get("signature").toString()));

    return new DepositData(pubkey, withdrawal_credentials, amount, signature);
  }

  private static ProposerSlashing getProposerSlashing(Map map) {
    UnsignedLong proposer_index = UnsignedLong.valueOf(map.get("proposer_index").toString());
    BeaconBlockHeader header_1 = getBeaconBlockHeader((Map) map.get("header_1"));
    BeaconBlockHeader header_2 = getBeaconBlockHeader((Map) map.get("header_2"));

    return new ProposerSlashing(proposer_index, header_1, header_2);
  }

  private static BeaconBlockHeader getBeaconBlockHeader(Map map) {
    UnsignedLong slot = UnsignedLong.valueOf(map.get("slot").toString());
    Bytes32 parent_root = Bytes32.fromHexString(map.get("parent_root").toString());
    Bytes32 state_root = Bytes32.fromHexString(map.get("state_root").toString());
    Bytes32 body_root = Bytes32.fromHexString(map.get("body_root").toString());
    BLSSignature signature =
        BLSSignature.fromBytes(Bytes.fromHexString(map.get("signature").toString()));

    return new BeaconBlockHeader(slot, parent_root, state_root, body_root, signature);
  }

  private static Eth1Data getEth1Data(Map map) {
    Bytes32 deposit_root = Bytes32.fromHexString(map.get("deposit_root").toString());
    UnsignedLong deposit_count = UnsignedLong.valueOf(map.get("deposit_count").toString());
    Bytes32 block_hash = Bytes32.fromHexString(map.get("block_hash").toString());

    return new Eth1Data(deposit_root, deposit_count, block_hash);
  }

  private static AttesterSlashing getAttesterSlashing(Map map) {

    return new AttesterSlashing(
        getIndexedAttestation((Map) map.get("attestation_1")),
        getIndexedAttestation((Map) map.get("attestation_2")));
  }

  private static IndexedAttestation getIndexedAttestation(Map map) {
    List<UnsignedLong> custody_bit_0_indices =
        new ArrayList<Integer>((ArrayList<Integer>) map.get("custody_bit_0_indices"))
            .stream().map(e -> UnsignedLong.valueOf(e.longValue())).collect(Collectors.toList());
    List<UnsignedLong> custody_bit_1_indices =
        new ArrayList<Integer>((ArrayList<Integer>) map.get("custody_bit_1_indices"))
            .stream().map(e -> UnsignedLong.valueOf(e.longValue())).collect(Collectors.toList());
    AttestationData data = getAttestationData((Map) map.get("data"));
    BLSSignature signature =
        BLSSignature.fromBytes(Bytes.fromHexString(map.get("signature").toString()));

    return new IndexedAttestation(custody_bit_0_indices, custody_bit_1_indices, data, signature);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static AttestationDataAndCustodyBit getAttestationDataAndCustodyBit(Map map) {

    return new AttestationDataAndCustodyBit(
        getAttestationData((Map) map.get("data")),
        Boolean.getBoolean((map.get("custody_bit")).toString()));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Attestation getAttestation(Map map) {
    return new Attestation(
        Bytes.fromHexString(map.get("aggregation_bits").toString()),
        getAttestationData((Map) map.get("data")),
        Bytes.fromHexString(map.get("custody_bits").toString()),
        BLSSignature.fromBytes(Bytes.fromHexString(map.get("signature").toString())));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static AttestationData getAttestationData(Map map) {

    return new AttestationData(
        Bytes32.fromHexString(map.get("beacon_block_root").toString()),
        getCheckpoint((Map) map.get("source")),
        getCheckpoint((Map) map.get("target")),
        getCrossLink((Map) map.get("crosslink")));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Checkpoint getCheckpoint(Map map) {
    return new Checkpoint(
        UnsignedLong.valueOf(map.get("epoch").toString()),
        Bytes32.fromHexString(map.get("root").toString()));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Crosslink getCrossLink(Map map) {

    return new Crosslink(
        UnsignedLong.valueOf(map.get("shard").toString()),
        Bytes32.fromHexString(map.get("parent_root").toString()),
        UnsignedLong.valueOf(map.get("start_epoch").toString()),
        UnsignedLong.valueOf(map.get("end_epoch").toString()),
        Bytes32.fromHexString(map.get("data_root").toString()));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Bytes32 getBytes32(Map testObject) {

    return Bytes32.fromHexString(testObject.toString());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Bytes getBytes(Map testObject) {

    return Bytes.fromHexString(testObject.toString());
  }
}
