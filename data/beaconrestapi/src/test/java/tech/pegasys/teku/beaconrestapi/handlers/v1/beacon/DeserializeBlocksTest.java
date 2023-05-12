/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.AttestationData;
import tech.pegasys.teku.api.schema.AttesterSlashing;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.BeaconBlockBody;
import tech.pegasys.teku.api.schema.BeaconBlockHeader;
import tech.pegasys.teku.api.schema.Checkpoint;
import tech.pegasys.teku.api.schema.Deposit;
import tech.pegasys.teku.api.schema.DepositData;
import tech.pegasys.teku.api.schema.Eth1Data;
import tech.pegasys.teku.api.schema.IndexedAttestation;
import tech.pegasys.teku.api.schema.ProposerSlashing;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.SignedBeaconBlockHeader;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.api.schema.VoluntaryExit;
import tech.pegasys.teku.api.schema.deneb.BlockContainer;
import tech.pegasys.teku.api.schema.deneb.SignedBlockContents;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableListTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

public class DeserializeBlocksTest {

  static final Spec spec = TestSpecFactory.createMinimalDeneb();

  public static final DeserializableTypeDefinition<BLSSignature> SIGNATURE_TYPE =
      DeserializableTypeDefinition.string(BLSSignature.class)
          .formatter(BLSSignature::toString)
          .parser(BLSSignature::fromHexString)
          .build();

  public static final DeserializableTypeDefinition<Eth1Data> ETH_1_DATA_TYPE =
      DeserializableTypeDefinition.object(Eth1Data.class)
          .initializer(Eth1Data::new)
          .withField(
              "deposit_root",
              CoreTypes.BYTES32_TYPE,
              Eth1Data::getDepositRoot,
              Eth1Data::setDepositRoot)
          .withField(
              "deposit_count",
              CoreTypes.UINT64_TYPE,
              Eth1Data::getDepositCount,
              Eth1Data::setDepositCount)
          .withField(
              "block_hash", CoreTypes.BYTES32_TYPE, Eth1Data::getBlockHash, Eth1Data::setBlockHash)
          .build();

  public static final DeserializableTypeDefinition<BeaconBlockHeader> BEACON_BLOCK_HEADER_TYPE =
      DeserializableTypeDefinition.object(BeaconBlockHeader.class)
          .initializer(BeaconBlockHeader::new)
          .withField(
              "slot", CoreTypes.UINT64_TYPE, BeaconBlockHeader::getSlot, BeaconBlockHeader::setSlot)
          .withField(
              "proposer_index",
              CoreTypes.UINT64_TYPE,
              BeaconBlockHeader::getProposerIndex,
              BeaconBlockHeader::setProposerIndex)
          .withField(
              "parent_root",
              CoreTypes.BYTES32_TYPE,
              BeaconBlockHeader::getParentRoot,
              BeaconBlockHeader::setParentRoot)
          .withField(
              "state_root",
              CoreTypes.BYTES32_TYPE,
              BeaconBlockHeader::getStateRoot,
              BeaconBlockHeader::setStateRoot)
          .withField(
              "body_root",
              CoreTypes.BYTES32_TYPE,
              BeaconBlockHeader::getBodyRoot,
              BeaconBlockHeader::setBodyRoot)
          .build();
  public static DeserializableTypeDefinition<SignedBeaconBlockHeader> SIGNED_BLOCK_HEADER_TYPE =
      DeserializableTypeDefinition.object(SignedBeaconBlockHeader.class)
          .initializer(SignedBeaconBlockHeader::new)
          .withField(
              "message",
              BEACON_BLOCK_HEADER_TYPE,
              SignedBeaconBlockHeader::getMessage,
              SignedBeaconBlockHeader::setMessage)
          .withField(
              "signature",
              SIGNATURE_TYPE,
              SignedBeaconBlockHeader::getSignature,
              SignedBeaconBlockHeader::setSignature)
          .build();

  public static DeserializableTypeDefinition<ProposerSlashing> PROPOSER_SLASHING_TYPE =
      DeserializableTypeDefinition.object(ProposerSlashing.class)
          .initializer(ProposerSlashing::new)
          .withField(
              "signed_header_1",
              SIGNED_BLOCK_HEADER_TYPE,
              ProposerSlashing::getSignedHeader1,
              ProposerSlashing::setSignedHeader1)
          .withField(
              "signed_header_2",
              SIGNED_BLOCK_HEADER_TYPE,
              ProposerSlashing::getSignedHeader2,
              ProposerSlashing::setSignedHeader2)
          .build();

  public static DeserializableTypeDefinition<Checkpoint> CHECKPOINT_TYPE =
      DeserializableTypeDefinition.object(Checkpoint.class)
          .initializer(Checkpoint::new)
          .withField("epoch", CoreTypes.UINT64_TYPE, Checkpoint::getEpoch, Checkpoint::setEpoch)
          .withField("root", CoreTypes.BYTES32_TYPE, Checkpoint::getRoot, Checkpoint::setRoot)
          .build();

  public static DeserializableTypeDefinition<AttestationData> ATTESTATION_DATA_TYPE =
      DeserializableTypeDefinition.object(AttestationData.class)
          .initializer(AttestationData::new)
          .withField(
              "slot", CoreTypes.UINT64_TYPE, AttestationData::getSlot, AttestationData::setSlot)
          .withField(
              "index", CoreTypes.UINT64_TYPE, AttestationData::getIndex, AttestationData::setIndex)
          .withField(
              "beacon_block_root",
              CoreTypes.BYTES32_TYPE,
              AttestationData::getBeaconBlockRoot,
              AttestationData::setBeaconBlockRoot)
          .withField(
              "source", CHECKPOINT_TYPE, AttestationData::getSource, AttestationData::setSource)
          .withField(
              "target", CHECKPOINT_TYPE, AttestationData::getTarget, AttestationData::setTarget)
          .build();
  public static DeserializableTypeDefinition<IndexedAttestation> INDEXED_ATTESTATION_TYPE =
      DeserializableTypeDefinition.object(IndexedAttestation.class)
          .initializer(IndexedAttestation::new)
          .withField(
              "attesting_indices",
              new DeserializableListTypeDefinition<>(CoreTypes.UINT64_TYPE),
              IndexedAttestation::getAttestingIndices,
              IndexedAttestation::setAttestingIndices)
          .withField(
              "data",
              ATTESTATION_DATA_TYPE,
              IndexedAttestation::getData,
              IndexedAttestation::setData)
          .withField(
              "signature",
              SIGNATURE_TYPE,
              IndexedAttestation::getSignature,
              IndexedAttestation::setSignature)
          .build();

  public static DeserializableTypeDefinition<AttesterSlashing> ATTESTER_SLASHING_TYPE =
      DeserializableTypeDefinition.object(AttesterSlashing.class)
          .initializer(AttesterSlashing::new)
          .withField(
              "attestation_1",
              INDEXED_ATTESTATION_TYPE,
              AttesterSlashing::getAttestation1,
              AttesterSlashing::setAttestation1)
          .withField(
              "attestation_2",
              INDEXED_ATTESTATION_TYPE,
              AttesterSlashing::getAttestation2,
              AttesterSlashing::setAttestation2)
          .build();
  public static final DeserializableListTypeDefinition<ProposerSlashing> PROPOSER_SLASHINGS_TYPE =
      new DeserializableListTypeDefinition<>(PROPOSER_SLASHING_TYPE);

  public static final DeserializableListTypeDefinition<AttesterSlashing> ATTESTER_SLASHINGS_TYPE =
      new DeserializableListTypeDefinition<>(ATTESTER_SLASHING_TYPE);

  public static DeserializableTypeDefinition<Attestation> ATTESTATION_TYPE =
      DeserializableTypeDefinition.object(Attestation.class)
          .initializer(Attestation::new)
          .withField(
              "aggregation_bits",
              CoreTypes.BYTES_SSZ_TYPE,
              Attestation::getAggregationBits,
              Attestation::setAggregationBits)
          .withField("data", ATTESTATION_DATA_TYPE, Attestation::getData, Attestation::setData)
          .withField(
              "signature", SIGNATURE_TYPE, Attestation::getSignature, Attestation::setSignature)
          .build();
  public static final DeserializableListTypeDefinition<Attestation> ATTESTATIONS_TYPE =
      new DeserializableListTypeDefinition<>(ATTESTATION_TYPE);

  public static final StringValueTypeDefinition<BLSPubKey> PUBKEY_TYPE =
      DeserializableTypeDefinition.string(BLSPubKey.class)
          .formatter(BLSPubKey::toString)
          .parser(BLSPubKey::fromHexString)
          .format("byte")
          .build();
  public static DeserializableTypeDefinition<DepositData> DEPOSIT_DATA_TYPE =
      DeserializableTypeDefinition.object(DepositData.class)
          .initializer(DepositData::new)
          .withField("pubkey", PUBKEY_TYPE, DepositData::getPubkey, DepositData::setPubkey)
          .withField(
              "withdrawal_credentials",
              CoreTypes.BYTES32_TYPE,
              DepositData::getWithdrawalCredentials,
              DepositData::setWithdrawalCredentials)
          .withField(
              "amount", CoreTypes.UINT64_TYPE, DepositData::getAmount, DepositData::setAmount)
          .withField(
              "signature", SIGNATURE_TYPE, DepositData::getSignature, DepositData::setSignature)
          .build();

  public static DeserializableTypeDefinition<Deposit> DEPOSIT_TYPE =
      DeserializableTypeDefinition.object(Deposit.class)
          .initializer(Deposit::new)
          .withField(
              "proof",
              new DeserializableListTypeDefinition<>(CoreTypes.BYTES32_TYPE),
              Deposit::getProof,
              Deposit::setProof)
          .withField("data", DEPOSIT_DATA_TYPE, Deposit::getData, Deposit::setData)
          .build();
  public static final DeserializableListTypeDefinition<Deposit> DEPOSITS_TYPE =
      new DeserializableListTypeDefinition<>(DEPOSIT_TYPE);

  public static DeserializableTypeDefinition<VoluntaryExit> VOLUNTARY_EXIT_TYPE =
      DeserializableTypeDefinition.object(VoluntaryExit.class)
          .initializer(VoluntaryExit::new)
          .withField(
              "epoch", CoreTypes.UINT64_TYPE, VoluntaryExit::getEpoch, VoluntaryExit::setEpoch)
          .withField(
              "validator_index",
              CoreTypes.UINT64_TYPE,
              VoluntaryExit::getValidatorIndex,
              VoluntaryExit::setValidatorIndex)
          .build();
  public static DeserializableTypeDefinition<SignedVoluntaryExit> SIGNED_VOLUNTARY_EXIT_TYPE =
      DeserializableTypeDefinition.object(SignedVoluntaryExit.class)
          .initializer(SignedVoluntaryExit::new)
          .withField(
              "message",
              VOLUNTARY_EXIT_TYPE,
              SignedVoluntaryExit::getMessage,
              SignedVoluntaryExit::setMessage)
          .withField(
              "signature",
              SIGNATURE_TYPE,
              SignedVoluntaryExit::getSignature,
              SignedVoluntaryExit::setSignature)
          .build();

  public static final DeserializableListTypeDefinition<SignedVoluntaryExit>
      SIGNED_VOLUNTARY_EXITS_TYPE =
          new DeserializableListTypeDefinition<>(SIGNED_VOLUNTARY_EXIT_TYPE);
  static final DeserializableTypeDefinition<BeaconBlockBody> BEACON_BLOCK_BODY =
      DeserializableTypeDefinition.object(BeaconBlockBody.class)
          .initializer(BeaconBlockBody::new)
          .withField(
              "randao_reveal",
              SIGNATURE_TYPE,
              BeaconBlockBody::getRandaoReveal,
              BeaconBlockBody::setRandaoReveal)
          .withField(
              "eth1_data",
              ETH_1_DATA_TYPE,
              BeaconBlockBody::getEth1Data,
              BeaconBlockBody::setEth1Data)
          .withField(
              "graffiti",
              CoreTypes.BYTES32_TYPE,
              BeaconBlockBody::getGraffiti,
              BeaconBlockBody::setGraffiti)
          .withField(
              "proposer_slashings",
              PROPOSER_SLASHINGS_TYPE,
              BeaconBlockBody::getProposerSlashings,
              BeaconBlockBody::setProposerSlashings)
          .withField(
              "attester_slashings",
              ATTESTER_SLASHINGS_TYPE,
              BeaconBlockBody::getAttesterSlashings,
              BeaconBlockBody::setAttesterSlashings)
          .withField(
              "attestations",
              ATTESTATIONS_TYPE,
              BeaconBlockBody::getAttestations,
              BeaconBlockBody::setAttestations)
          .withField(
              "deposits", DEPOSITS_TYPE, BeaconBlockBody::getDeposits, BeaconBlockBody::setDeposits)
          .withField(
              "voluntary_exits",
              SIGNED_VOLUNTARY_EXITS_TYPE,
              BeaconBlockBody::getVoluntaryExits,
              BeaconBlockBody::setVoluntaryExits)
          .build();

  static final DeserializableTypeDefinition<BeaconBlock> BEACON_BLOCK_TYPE =
      DeserializableTypeDefinition.object(BeaconBlock.class)
          .initializer(BeaconBlock::new)
          .withField("slot", CoreTypes.UINT64_TYPE, BeaconBlock::getSlot, BeaconBlock::setSlot)
          .withField(
              "proposer_index",
              CoreTypes.UINT64_TYPE,
              BeaconBlock::getProposerIndex,
              BeaconBlock::setProposerIndex)
          .withField(
              "parent_root",
              CoreTypes.BYTES32_TYPE,
              BeaconBlock::getParentRoot,
              BeaconBlock::setParentRoot)
          .withField(
              "state_root",
              CoreTypes.BYTES32_TYPE,
              BeaconBlock::getStateRoot,
              BeaconBlock::setStateRoot)
          .withField("body", BEACON_BLOCK_BODY, BeaconBlock::getBody, BeaconBlock::setBody)
          .build();
  public static final DeserializableTypeDefinition<SignedBeaconBlock> SIGNED_BEACON_BLOCK_TYPE =
      DeserializableTypeDefinition.object(SignedBeaconBlock.class)
          .name("SignedBeaconBlock")
          .initializer(SignedBeaconBlock::new)
          .withField(
              "message",
              BEACON_BLOCK_TYPE,
              SignedBeaconBlock::getBeaconBlock,
              SignedBeaconBlock::setBeaconBlock)
          .withField(
              "signature",
              SIGNATURE_TYPE,
              SignedBeaconBlock::getSignature,
              SignedBeaconBlock::setSignature)
          .build();
  public static final DeserializableTypeDefinition<SignedBlockContents> SIGNED_BLOCK_CONTENTS_TYPE =
      DeserializableTypeDefinition.object(SignedBlockContents.class)
          .name("SignedBlockContents")
          .initializer(SignedBlockContents::new)
          .withField(
              "signed_block",
              SIGNED_BEACON_BLOCK_TYPE,
              SignedBlockContents::getSignedBeaconBlock,
              SignedBlockContents::setSignedBeaconBlock)
          .build();

  public static final DeserializableOneOfTypeDefinition<BlockContainer, BlockContainerBuilder>
      DESERIALIZABLE_ONE_OF_SIGNED_BEACON_BLOCK_OR_SIGNED_BLOCK_CONTENTS =
          DeserializableOneOfTypeDefinition.object(
                  BlockContainer.class, BlockContainerBuilder.class)
              .description(
                  "Submit a signed beacon block to the beacon node to be imported."
                      + " The beacon node performs the required validation.")
              .withType(
                  SignedBeaconBlock.isInstance,
                  s -> !s.contains("blob_sidecars"),
                  SIGNED_BEACON_BLOCK_TYPE)
              .withType(
                  SignedBlockContents.isInstance,
                  s -> s.contains("blob_sidecars"),
                  SIGNED_BLOCK_CONTENTS_TYPE)
              .build();

  @Test
  void shouldDeserializeSignedBlockContents() throws JsonProcessingException {
    final BlockContainer result =
        JsonUtil.parse(
            readResource("json/signed_block_contents.json"),
            DESERIALIZABLE_ONE_OF_SIGNED_BEACON_BLOCK_OR_SIGNED_BLOCK_CONTENTS);
    assertThat(result).isInstanceOf(SignedBlockContents.class);
  }

  @Test
  void shouldDeserializeSignedBeaconBlock() throws JsonProcessingException {
    final BlockContainer result =
        JsonUtil.parse(
            readResource("json/signed_beacon_block.json"),
            DESERIALIZABLE_ONE_OF_SIGNED_BEACON_BLOCK_OR_SIGNED_BLOCK_CONTENTS);

    assertThat(result).isInstanceOf(SignedBeaconBlock.class);

    SignedBeaconBlock signedBeaconBlock = (SignedBeaconBlock) result;
    assertThat(signedBeaconBlock.getSignature())
        .isEqualTo(
            BLSSignature.fromHexString(
                "0x99f5bc997861976d73013dd4c06f42a2318912e5e075a2746840bb34134e7f4765ba88dec12f4bd5d2fa24d7bc2c17ce105bfb796108064499faaa4d4b3e17db6e62bc88f8e0a6243105b01c00302c3d9f81f5e790d6a6951532ac94c19d1114"));
    assertThat(signedBeaconBlock.getMessage().getProposerIndex())
        .isEqualTo(UInt64.valueOf(4666673844721362956L));
    assertThat(signedBeaconBlock.getMessage().getParentRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x367cbd40ac7318427aadb97345a91fa2e965daf3158d7f1846f1306305f41bef"));
    assertThat(signedBeaconBlock.getMessage().getStateRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xfd18cf40cc907a739be483f1ca0ee23ad65cdd3df23205eabc6d660a75d1f54e"));

    tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock internalSignedBeaconBlock =
        signedBeaconBlock.asInternalSignedBeaconBlock(spec);
  }

  protected String readResource(final String resource) {
    try {
      return Resources.toString(Resources.getResource(resource), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static class BlockContainerBuilder {}
}
