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

package tech.pegasys.artemis.ethtests;

import com.google.common.primitives.UnsignedLong;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateImpl;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.HistoricalBatch;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.state.ValidatorImpl;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.mikuli.G2Point;
import tech.pegasys.artemis.util.mikuli.PublicKey;
import tech.pegasys.artemis.util.mikuli.SecretKey;
import tech.pegasys.artemis.util.mikuli.Signature;

public class MapObjectUtil {

  @SuppressWarnings({"rawtypes"})
  public static Object convertMapToTypedObject(Class classtype, Object object) {
    if (classtype.equals(Attestation.class)) return MapObjectUtil.getAttestation((Map) object);
    else if (classtype.equals(AttestationData.class)) return getAttestationData((Map) object);
    else if (classtype.equals(AttesterSlashing.class)) return getAttesterSlashing((Map) object);
    else if (classtype.equals(BeaconBlock.class)) return getBeaconBlock((Map) object);
    else if (classtype.equals(SignedBeaconBlock.class)) return getSignedBeaconBlock((Map) object);
    else if (classtype.equals(BeaconBlockBody.class)) return getBeaconBlockBody((Map) object);
    else if (classtype.equals(BeaconBlockHeader.class)) return getBeaconBlockHeader((Map) object);
    else if (classtype.equals(Bytes[].class)) return getBytesArray((List) object);
    else if (classtype.equals(Bytes32[].class)) return getBytes32Array((List) object);
    else if (classtype.equals(BeaconStateImpl.class)) return getBeaconState((Map) object);
    else if (classtype.equals(Checkpoint.class)) return getCheckpoint((Map) object);
    else if (classtype.equals(Deposit.class)) return getDeposit((Map) object);
    else if (classtype.equals(DepositData.class)) return getDepositData((Map) object);
    else if (classtype.equals(Eth1Data.class)) return getEth1Data((Map) object);
    else if (classtype.equals(Fork.class)) return getFork((Map) object);
    else if (classtype.equals(G2Point.class)) return getG2Point(object);
    else if (classtype.equals(HistoricalBatch.class)) return getHistoricalBatch((Map) object);
    else if (classtype.equals(IndexedAttestation.class)) return getIndexedAttestation((Map) object);
    else if (classtype.equals(PendingAttestation.class)) return getPendingAttestation((Map) object);
    else if (classtype.equals(ProposerSlashing.class)) return getProposerSlashing((Map) object);
    else if (classtype.equals(PublicKey.class)) return getPublicKey(object.toString());
    else if (classtype.equals(PublicKey[].class)) return getPublicKeyArray((List) object);
    else if (classtype.equals(SecretKey.class)) return getSecretKey(object.toString());
    else if (classtype.equals(Signature.class)) return getSignature(object.toString());
    else if (classtype.equals(Signature[].class)) return getSignatureArray((List) object);
    else if (classtype.equals(ValidatorImpl.class)) return getValidator((Map) object);
    else if (classtype.equals(VoluntaryExit.class)) return getVoluntaryExit((Map) object);
    else if (classtype.equals(SignedVoluntaryExit.class))
      return getSignedVoluntaryExit((Map) object);
    else if (classtype.equals(Integer[].class)) return getIntegerArray((List) object);
    else if (classtype.equals(UnsignedLong.class)) return UnsignedLong.valueOf(object.toString());
    else if (classtype.equals(Integer.class)) return Integer.valueOf(object.toString());
    else if (classtype.equals(Bytes32.class)) return Bytes32.fromHexString(object.toString());
    else if (classtype.equals(Bytes.class)) {
      return Bytes.fromHexString(object.toString());
    } else if (classtype.equals(Boolean.class)) return Boolean.valueOf(object.toString());

    return null;
  }

  @SuppressWarnings({"rawtypes"})
  private static G2Point getG2Point(Object object) {
    if (object.getClass() == ArrayList.class) {
      List list = (List) object;
      // If we have only two elements then these are the compressed X_im and X_re coordinates
      if (list.size() == 2) {
        return G2Point.fromBytesCompressed(
            Bytes.concatenate(
                Bytes48.leftPad(Bytes.fromHexString(list.get(0).toString())),
                Bytes48.leftPad(Bytes.fromHexString(list.get(1).toString()))));
      }

      // If we have three elements then these are the uncompressed complex X, Y and Z coords
      BIG xRe =
          BIG.fromBytes(
              Bytes48.leftPad(Bytes.fromHexString(((List) list.get(0)).get(0).toString()))
                  .toArray());
      BIG xIm =
          BIG.fromBytes(
              Bytes48.leftPad(Bytes.fromHexString(((List) list.get(0)).get(1).toString()))
                  .toArray());
      BIG yRe =
          BIG.fromBytes(
              Bytes48.leftPad(Bytes.fromHexString(((List) list.get(1)).get(0).toString()))
                  .toArray());
      BIG yIm =
          BIG.fromBytes(
              Bytes48.leftPad(Bytes.fromHexString(((List) list.get(1)).get(1).toString()))
                  .toArray());
      BIG zRe =
          BIG.fromBytes(
              Bytes48.leftPad(Bytes.fromHexString(((List) list.get(2)).get(0).toString()))
                  .toArray());
      BIG zIm =
          BIG.fromBytes(
              Bytes48.leftPad(Bytes.fromHexString(((List) list.get(2)).get(1).toString()))
                  .toArray());

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
    } else {
      Map map = (Map) object;
      return G2Point.hashToG2(
          Bytes.fromHexString(map.get("message").toString()),
          Bytes.fromHexString(map.get("domain").toString()));
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static List<Bytes> getBytesArray(List list) {
    return (List<Bytes>)
        list.stream()
            .map(object -> Bytes.fromHexString(object.toString()))
            .collect(Collectors.toList());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static List<PublicKey> getPublicKeyArray(List list) {
    return (List<PublicKey>)
        list.stream().map(object -> getPublicKey(object.toString())).collect(Collectors.toList());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static List<Signature> getSignatureArray(List list) {
    return (List<Signature>)
        list.stream().map(object -> getSignature(object.toString())).collect(Collectors.toList());
  }

  private static PublicKey getPublicKey(String s) {
    return PublicKey.fromBytesCompressed(Bytes.fromHexString(s));
  }

  private static SecretKey getSecretKey(String s) {
    return SecretKey.fromBytes(Bytes48.leftPad(Bytes.fromHexString(s)));
  }

  private static Signature getSignature(String s) {
    return Signature.fromBytesCompressed(Bytes.fromHexString(s));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static List<Bytes32> getBytes32Array(List list) {
    return (List<Bytes32>)
        list.stream()
            .map(object -> Bytes32.fromHexString(object.toString()))
            .collect(Collectors.toList());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static HistoricalBatch getHistoricalBatch(Map map) {
    SSZVector<Bytes32> block_roots =
        SSZVector.create(
            new ArrayList<>(
                ((List<String>) map.get("block_roots"))
                    .stream().map(e -> Bytes32.fromHexString(e)).collect(Collectors.toList())),
            Bytes32.class);
    SSZVector<Bytes32> state_roots =
        SSZVector.create(
            new ArrayList<>(
                ((List<String>) map.get("state_roots"))
                    .stream().map(e -> Bytes32.fromHexString(e)).collect(Collectors.toList())),
            Bytes32.class);

    return new HistoricalBatch(block_roots, state_roots);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static BeaconState getBeaconState(Map map) {
    UnsignedLong genesis_time = UnsignedLong.valueOf(map.get("genesis_time").toString());
    UnsignedLong slot = UnsignedLong.valueOf(map.get("slot").toString());
    Fork fork = getFork((Map) map.get("fork"));
    BeaconBlockHeader latest_block_header =
        getBeaconBlockHeader((Map) map.get("latest_block_header"));
    SSZVector<Bytes32> block_roots =
        SSZVector.create(
            new ArrayList<>(
                ((List<String>) map.get("block_roots"))
                    .stream().map(e -> Bytes32.fromHexString(e)).collect(Collectors.toList())),
            Bytes32.class);
    SSZVector<Bytes32> state_roots =
        SSZVector.create(
            new ArrayList<>(
                ((List<String>) map.get("state_roots"))
                    .stream().map(e -> Bytes32.fromHexString(e)).collect(Collectors.toList())),
            Bytes32.class);
    SSZList<Bytes32> historical_roots =
        SSZList.create(
            new ArrayList<>(
                ((List<String>) map.get("historical_roots"))
                    .stream().map(e -> Bytes32.fromHexString(e)).collect(Collectors.toList())),
            Constants.HISTORICAL_ROOTS_LIMIT,
            Bytes32.class);
    Eth1Data eth1_data = getEth1Data((Map) map.get("eth1_data"));
    SSZList<Eth1Data> eth1_data_votes =
        SSZList.create(
            ((List<Map>) map.get("eth1_data_votes"))
                .stream().map(e -> getEth1Data(e)).collect(Collectors.toList()),
            Constants.SLOTS_PER_ETH1_VOTING_PERIOD,
            Eth1Data.class);
    UnsignedLong eth1_deposit_index =
        UnsignedLong.valueOf(map.get("eth1_deposit_index").toString());
    SSZList<Validator> validators =
        SSZList.create(
            ((List<Map>) map.get("validators"))
                .stream().map(e -> getValidator(e)).collect(Collectors.toList()),
            Constants.VALIDATOR_REGISTRY_LIMIT,
            ValidatorImpl.class);
    SSZList<UnsignedLong> balances =
        SSZList.create(
            new ArrayList<>(
                ((List<Object>) map.get("balances"))
                    .stream()
                        .map(e -> UnsignedLong.valueOf(convertUntypedNumericalClassesToString(e)))
                        .collect(Collectors.toList())),
            Constants.VALIDATOR_REGISTRY_LIMIT,
            UnsignedLong.class);
    SSZVector<Bytes32> randao_mixes =
        SSZVector.create(
            new ArrayList<>(
                ((List<String>) map.get("randao_mixes"))
                    .stream().map(e -> Bytes32.fromHexString(e)).collect(Collectors.toList())),
            Bytes32.class);
    SSZVector<UnsignedLong> slashings =
        SSZVector.create(
            new ArrayList<>((List<Object>) map.get("slashings"))
                .stream()
                    .map(e -> UnsignedLong.valueOf(convertUntypedNumericalClassesToString(e)))
                    .collect(Collectors.toList()),
            UnsignedLong.class);
    SSZList<PendingAttestation> previous_epoch_attestations =
        SSZList.create(
            ((List<Map>) map.get("previous_epoch_attestations"))
                .stream().map(e -> getPendingAttestation(e)).collect(Collectors.toList()),
            Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH,
            PendingAttestation.class);
    SSZList<PendingAttestation> current_epoch_attestations =
        SSZList.create(
            ((List<Map>) map.get("current_epoch_attestations"))
                .stream().map(e -> getPendingAttestation(e)).collect(Collectors.toList()),
            Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH,
            PendingAttestation.class);
    Bitvector justification_bits =
        Bitvector.fromBytes(
            Bytes.fromHexString(map.get("justification_bits").toString()),
            Constants.JUSTIFICATION_BITS_LENGTH);
    Checkpoint previous_justified_checkpoint =
        getCheckpoint((Map) map.get("previous_justified_checkpoint"));
    Checkpoint current_justified_checkpoint =
        getCheckpoint((Map) map.get("current_justified_checkpoint"));
    Checkpoint finalized_checkpoint = getCheckpoint((Map) map.get("finalized_checkpoint"));

    return BeaconState.create(
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
        randao_mixes,
        slashings,
        previous_epoch_attestations,
        current_epoch_attestations,
        justification_bits,
        previous_justified_checkpoint,
        current_justified_checkpoint,
        finalized_checkpoint);
  }

  @SuppressWarnings({"rawtypes"})
  private static PendingAttestation getPendingAttestation(Map map) {
    Bitlist aggregation_bits =
        Bitlist.fromBytes(
            Bytes.fromHexString(map.get("aggregation_bits").toString()),
            Constants.MAX_VALIDATORS_PER_COMMITTEE);
    AttestationData data = getAttestationData((Map) map.get("data"));
    UnsignedLong inclusion_delay = UnsignedLong.valueOf(map.get("inclusion_delay").toString());
    UnsignedLong proposer_index = UnsignedLong.valueOf(map.get("proposer_index").toString());

    return new PendingAttestation(aggregation_bits, data, inclusion_delay, proposer_index);
  }

  @SuppressWarnings({"rawtypes"})
  private static Validator getValidator(Map map) {
    BLSPublicKey pubkey = BLSPublicKey.fromBytes(Bytes.fromHexString(map.get("pubkey").toString()));
    Bytes32 withdrawal_credentials =
        Bytes32.fromHexString(map.get("withdrawal_credentials").toString());
    UnsignedLong effective_balance = UnsignedLong.valueOf(map.get("effective_balance").toString());
    boolean slashed = Boolean.getBoolean(map.get("slashed").toString());
    UnsignedLong activation_eligibility_epoch =
        UnsignedLong.valueOf(map.get("activation_eligibility_epoch").toString());
    UnsignedLong activation_epoch = UnsignedLong.valueOf(map.get("activation_epoch").toString());
    UnsignedLong exit_epoch = UnsignedLong.valueOf(map.get("exit_epoch").toString());
    UnsignedLong withdrawable_epoch =
        UnsignedLong.valueOf(map.get("withdrawable_epoch").toString());

    return Validator.create(
        pubkey,
        withdrawal_credentials,
        effective_balance,
        slashed,
        activation_eligibility_epoch,
        activation_epoch,
        exit_epoch,
        withdrawable_epoch);
  }

  @SuppressWarnings({"rawtypes"})
  private static Fork getFork(Map map) {
    Bytes4 previous_version =
        new Bytes4(Bytes.fromHexString(map.get("previous_version").toString()));
    Bytes4 current_version = new Bytes4(Bytes.fromHexString(map.get("current_version").toString()));
    UnsignedLong epoch = UnsignedLong.valueOf(map.get("epoch").toString());

    return new Fork(previous_version, current_version, epoch);
  }

  @SuppressWarnings({"rawtypes"})
  private static SignedBeaconBlock getSignedBeaconBlock(Map map) {
    final BeaconBlock block = getBeaconBlock((Map) map.get("message"));
    final BLSSignature signature =
        BLSSignature.fromBytes(Bytes.fromHexString(map.get("signature").toString()));
    return new SignedBeaconBlock(block, signature);
  }

  @SuppressWarnings({"rawtypes"})
  private static BeaconBlock getBeaconBlock(Map map) {
    UnsignedLong slot = UnsignedLong.valueOf(map.get("slot").toString());
    Bytes32 parent_root = Bytes32.fromHexString(map.get("parent_root").toString());
    Bytes32 state_root = Bytes32.fromHexString(map.get("state_root").toString());
    BeaconBlockBody body = getBeaconBlockBody((Map) map.get("body"));

    return new BeaconBlock(slot, parent_root, state_root, body);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static BeaconBlockBody getBeaconBlockBody(Map map) {
    BLSSignature randao_reveal =
        BLSSignature.fromBytes(Bytes.fromHexString(map.get("randao_reveal").toString()));
    Eth1Data eth1_data = getEth1Data((Map) map.get("eth1_data"));
    Bytes32 graffiti = Bytes32.fromHexString(map.get("graffiti").toString());
    SSZList<ProposerSlashing> proposer_slashings =
        SSZList.create(
            ((List<Map>) map.get("proposer_slashings"))
                .stream().map(e -> getProposerSlashing(e)).collect(Collectors.toList()),
            Constants.MAX_PROPOSER_SLASHINGS,
            ProposerSlashing.class);
    SSZList<AttesterSlashing> attester_slashings =
        SSZList.create(
            ((List<Map>) map.get("attester_slashings"))
                .stream().map(e -> getAttesterSlashing(e)).collect(Collectors.toList()),
            Constants.MAX_ATTESTER_SLASHINGS,
            AttesterSlashing.class);
    SSZList<Attestation> attestations =
        SSZList.create(
            ((List<Map>) map.get("attestations"))
                .stream().map(e -> getAttestation(e)).collect(Collectors.toList()),
            Constants.MAX_ATTESTATIONS,
            Attestation.class);
    SSZList<Deposit> deposits =
        SSZList.create(
            ((List<Map>) map.get("deposits"))
                .stream().map(e -> getDeposit(e)).collect(Collectors.toList()),
            Constants.MAX_DEPOSITS,
            Deposit.class);
    SSZList<SignedVoluntaryExit> voluntary_exits =
        SSZList.create(
            ((List<Map>) map.get("voluntary_exits"))
                .stream().map(e -> getSignedVoluntaryExit(e)).collect(Collectors.toList()),
            Constants.MAX_VOLUNTARY_EXITS,
            SignedVoluntaryExit.class);

    return new BeaconBlockBody(
        randao_reveal,
        eth1_data,
        graffiti,
        proposer_slashings,
        attester_slashings,
        attestations,
        deposits,
        voluntary_exits);
  }

  @SuppressWarnings({"rawtypes"})
  private static SignedVoluntaryExit getSignedVoluntaryExit(Map map) {
    final VoluntaryExit message = getVoluntaryExit((Map) map.get("message"));
    BLSSignature signature =
        BLSSignature.fromBytes(Bytes.fromHexString(map.get("signature").toString()));
    return new SignedVoluntaryExit(message, signature);
  }

  @SuppressWarnings({"rawtypes"})
  private static VoluntaryExit getVoluntaryExit(Map map) {
    UnsignedLong epoch = UnsignedLong.valueOf(map.get("epoch").toString());
    UnsignedLong validator_index = UnsignedLong.valueOf(map.get("validator_index").toString());

    return new VoluntaryExit(epoch, validator_index);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static List<Integer> getIntegerArray(List list) {
    return (List<Integer>)
        list.stream()
            .map(object -> Integer.valueOf(object.toString()))
            .collect(Collectors.toList());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Deposit getDeposit(Map map) {
    SSZVector<Bytes32> proof =
        SSZVector.create(
            new ArrayList<>(
                ((List<String>) map.get("proof"))
                    .stream().map(e -> Bytes32.fromHexString(e)).collect(Collectors.toList())),
            Bytes32.class);
    DepositData data = getDepositData((Map) map.get("data"));

    return new Deposit(proof, data);
  }

  @SuppressWarnings({"rawtypes"})
  private static DepositData getDepositData(Map map) {
    BLSPublicKey pubkey = BLSPublicKey.fromBytes(Bytes.fromHexString(map.get("pubkey").toString()));
    Bytes32 withdrawal_credentials =
        Bytes32.fromHexString(map.get("withdrawal_credentials").toString());
    UnsignedLong amount = UnsignedLong.valueOf(map.get("amount").toString());
    BLSSignature signature =
        BLSSignature.fromBytes(Bytes.fromHexString(map.get("signature").toString()));

    return new DepositData(pubkey, withdrawal_credentials, amount, signature);
  }

  @SuppressWarnings({"rawtypes"})
  private static ProposerSlashing getProposerSlashing(Map map) {
    UnsignedLong proposer_index = UnsignedLong.valueOf(map.get("proposer_index").toString());
    SignedBeaconBlockHeader header_1 = getSignedBeaconBlockHeader((Map) map.get("header_1"));
    SignedBeaconBlockHeader header_2 = getSignedBeaconBlockHeader((Map) map.get("header_2"));

    return new ProposerSlashing(proposer_index, header_1, header_2);
  }

  @SuppressWarnings({"rawtypes"})
  private static BeaconBlockHeader getBeaconBlockHeader(Map map) {
    UnsignedLong slot = UnsignedLong.valueOf(map.get("slot").toString());
    Bytes32 parent_root = Bytes32.fromHexString(map.get("parent_root").toString());
    Bytes32 state_root = Bytes32.fromHexString(map.get("state_root").toString());
    Bytes32 body_root = Bytes32.fromHexString(map.get("body_root").toString());

    return new BeaconBlockHeader(slot, parent_root, state_root, body_root);
  }

  @SuppressWarnings({"rawtypes"})
  private static SignedBeaconBlockHeader getSignedBeaconBlockHeader(Map map) {
    final BeaconBlockHeader beaconBlockHeader = getBeaconBlockHeader(map);
    BLSSignature signature =
        BLSSignature.fromBytes(Bytes.fromHexString(map.get("signature").toString()));

    return new SignedBeaconBlockHeader(beaconBlockHeader, signature);
  }

  @SuppressWarnings({"rawtypes"})
  private static Eth1Data getEth1Data(Map map) {
    Bytes32 deposit_root = Bytes32.fromHexString(map.get("deposit_root").toString());
    UnsignedLong deposit_count = UnsignedLong.valueOf(map.get("deposit_count").toString());
    Bytes32 block_hash = Bytes32.fromHexString(map.get("block_hash").toString());

    return new Eth1Data(deposit_root, deposit_count, block_hash);
  }

  @SuppressWarnings({"rawtypes"})
  private static AttesterSlashing getAttesterSlashing(Map map) {

    return new AttesterSlashing(
        getIndexedAttestation((Map) map.get("attestation_1")),
        getIndexedAttestation((Map) map.get("attestation_2")));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static IndexedAttestation getIndexedAttestation(Map map) {
    SSZList<UnsignedLong> attesting_indices =
        SSZList.create(
            new ArrayList<>((List<Object>) map.get("attesting_indices"))
                .stream()
                    .map(e -> UnsignedLong.valueOf(convertUntypedNumericalClassesToString(e)))
                    .collect(Collectors.toList()),
            Constants.MAX_VALIDATORS_PER_COMMITTEE,
            UnsignedLong.class);
    AttestationData data = getAttestationData((Map) map.get("data"));
    BLSSignature signature =
        BLSSignature.fromBytes(Bytes.fromHexString(map.get("signature").toString()));

    return new IndexedAttestation(attesting_indices, data, signature);
  }

  @SuppressWarnings({"rawtypes"})
  private static Attestation getAttestation(Map map) {
    return new Attestation(
        Bitlist.fromBytes(
            Bytes.fromHexString(map.get("aggregation_bits").toString()),
            Constants.MAX_VALIDATORS_PER_COMMITTEE),
        getAttestationData((Map) map.get("data")),
        BLSSignature.fromBytes(Bytes.fromHexString(map.get("signature").toString())));
  }

  @SuppressWarnings({"rawtypes"})
  private static AttestationData getAttestationData(Map map) {

    return new AttestationData(
        UnsignedLong.valueOf(map.get("slot").toString()),
        UnsignedLong.valueOf(map.get("index").toString()),
        Bytes32.fromHexString(map.get("beacon_block_root").toString()),
        getCheckpoint((Map) map.get("source")),
        getCheckpoint((Map) map.get("target")));
  }

  @SuppressWarnings({"rawtypes"})
  private static Checkpoint getCheckpoint(Map map) {
    return new Checkpoint(
        UnsignedLong.valueOf(map.get("epoch").toString()),
        Bytes32.fromHexString(map.get("root").toString()));
  }

  @SuppressWarnings({"rawtypes"})
  public static Bytes32 getBytes32(Map testObject) {

    return Bytes32.fromHexString(testObject.toString());
  }

  @SuppressWarnings({"rawtypes"})
  public static Bytes getBytes(Map testObject) {

    return Bytes.fromHexString(testObject.toString());
  }

  private static String convertUntypedNumericalClassesToString(Object e) {
    if (e.getClass().equals(BigInteger.class)
        || e.getClass().equals(Long.class)
        || e.getClass().equals(Integer.class)) {
      return e.toString();
    }
    return null;
  }
}
