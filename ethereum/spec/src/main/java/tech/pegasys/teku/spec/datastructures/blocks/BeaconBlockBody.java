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

package tech.pegasys.teku.spec.datastructures.blocks;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.containers.Container8;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema8;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.SpecDependent;

/** A Beacon block body */
public class BeaconBlockBody
    extends Container8<
        BeaconBlockBody,
        SszSignature,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SszList<SignedVoluntaryExit>> {

  public static class BeaconBlockBodySchema
      extends ContainerSchema8<
          BeaconBlockBody,
          SszSignature,
          Eth1Data,
          SszBytes32,
          SszList<ProposerSlashing>,
          SszList<AttesterSlashing>,
          SszList<Attestation>,
          SszList<Deposit>,
          SszList<SignedVoluntaryExit>> {

    public BeaconBlockBodySchema() {
      super(
          "BeaconBlockBody",
          namedSchema("randao_reveal", SszSignatureSchema.INSTANCE),
          namedSchema("eth1_data", Eth1Data.SSZ_SCHEMA),
          namedSchema("graffiti", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema(
              "proposer_slashings",
              SszListSchema.create(ProposerSlashing.SSZ_SCHEMA, Constants.MAX_PROPOSER_SLASHINGS)),
          namedSchema(
              "attester_slashings",
              SszListSchema.create(AttesterSlashing.SSZ_SCHEMA, Constants.MAX_ATTESTER_SLASHINGS)),
          namedSchema(
              "attestations",
              SszListSchema.create(Attestation.SSZ_SCHEMA, Constants.MAX_ATTESTATIONS)),
          namedSchema("deposits", SszListSchema.create(Deposit.SSZ_SCHEMA, Constants.MAX_DEPOSITS)),
          namedSchema(
              "voluntary_exits",
              SszListSchema.create(SignedVoluntaryExit.SSZ_SCHEMA, Constants.MAX_VOLUNTARY_EXITS)));
    }

    @SuppressWarnings("unchecked")
    public SszListSchema<ProposerSlashing, ?> getProposerSlashingsSchema() {
      return (SszListSchema<ProposerSlashing, ?>) getFieldSchema3();
    }

    @SuppressWarnings("unchecked")
    public SszListSchema<AttesterSlashing, ?> getAttesterSlashingsSchema() {
      return (SszListSchema<AttesterSlashing, ?>) getFieldSchema4();
    }

    @SuppressWarnings("unchecked")
    public SszListSchema<Attestation, ?> getAttestationsSchema() {
      return (SszListSchema<Attestation, ?>) getFieldSchema5();
    }

    @SuppressWarnings("unchecked")
    public SszListSchema<Deposit, ?> getDepositsSchema() {
      return (SszListSchema<Deposit, ?>) getFieldSchema6();
    }

    @SuppressWarnings("unchecked")
    public SszListSchema<SignedVoluntaryExit, ?> getVoluntaryExitsSchema() {
      return (SszListSchema<SignedVoluntaryExit, ?>) getFieldSchema7();
    }

    @Override
    public BeaconBlockBody createFromBackingNode(TreeNode node) {
      return new BeaconBlockBody(this, node);
    }
  }

  public static BeaconBlockBodySchema getSszSchema() {
    return SSZ_SCHEMA.get();
  }

  public static final SpecDependent<BeaconBlockBodySchema> SSZ_SCHEMA =
      SpecDependent.of(BeaconBlockBodySchema::new);

  private BeaconBlockBody(BeaconBlockBodySchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  @Deprecated // Use the constructor with type
  public BeaconBlockBody(
      BLSSignature randao_reveal,
      Eth1Data eth1_data,
      Bytes32 graffiti,
      SszList<ProposerSlashing> proposer_slashings,
      SszList<AttesterSlashing> attester_slashings,
      SszList<Attestation> attestations,
      SszList<Deposit> deposits,
      SszList<SignedVoluntaryExit> voluntary_exits) {
    this(
        SSZ_SCHEMA.get(),
        randao_reveal,
        eth1_data,
        graffiti,
        proposer_slashings,
        attester_slashings,
        attestations,
        deposits,
        voluntary_exits);
  }

  public BeaconBlockBody(
      BeaconBlockBodySchema type,
      BLSSignature randao_reveal,
      Eth1Data eth1_data,
      Bytes32 graffiti,
      SszList<ProposerSlashing> proposer_slashings,
      SszList<AttesterSlashing> attester_slashings,
      SszList<Attestation> attestations,
      SszList<Deposit> deposits,
      SszList<SignedVoluntaryExit> voluntary_exits) {
    super(
        type,
        new SszSignature(randao_reveal),
        eth1_data,
        SszBytes32.of(graffiti),
        proposer_slashings,
        attester_slashings,
        attestations,
        deposits,
        voluntary_exits);
  }

  public BeaconBlockBody() {
    super(SSZ_SCHEMA.get());
  }

  public BLSSignature getRandao_reveal() {
    return getField0().getSignature();
  }

  public Eth1Data getEth1_data() {
    return getField1();
  }

  public Bytes32 getGraffiti() {
    return getField2().get();
  }

  public SszList<ProposerSlashing> getProposer_slashings() {
    return getField3();
  }

  public SszList<AttesterSlashing> getAttester_slashings() {
    return getField4();
  }

  public SszList<Attestation> getAttestations() {
    return getField5();
  }

  public SszList<Deposit> getDeposits() {
    return getField6();
  }

  public SszList<SignedVoluntaryExit> getVoluntary_exits() {
    return getField7();
  }
}
