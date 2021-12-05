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

package tech.pegasys.teku.spec.datastructures.state;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.containers.Container8;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema8;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;

public class Validator
    extends Container8<
        Validator,
        SszPublicKey,
        SszBytes32,
        SszUInt64,
        SszBit,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszUInt64> {

  public static class ValidatorSchema
      extends ContainerSchema8<
          Validator,
          SszPublicKey,
          SszBytes32,
          SszUInt64,
          SszBit,
          SszUInt64,
          SszUInt64,
          SszUInt64,
          SszUInt64> {

    public ValidatorSchema() {
      super(
          "Validator",
          namedSchema("pubkey", SszPublicKeySchema.INSTANCE),
          namedSchema("withdrawal_credentials", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("effective_balance", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("slashed", SszPrimitiveSchemas.BIT_SCHEMA),
          namedSchema("activation_eligibility_epoch", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("activation_epoch", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("exit_epoch", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("withdrawable_epoch", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public Validator createFromBackingNode(TreeNode node) {
      return new Validator(this, node);
    }
  }

  public static final ValidatorSchema SSZ_SCHEMA = new ValidatorSchema();

  private Validator(ValidatorSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public Validator(
      BLSPublicKey pubkey,
      Bytes32 withdrawal_credentials,
      UInt64 effective_balance,
      boolean slashed,
      UInt64 activation_eligibility_epoch,
      UInt64 activation_epoch,
      UInt64 exit_epoch,
      UInt64 withdrawable_epoch) {
    super(
        SSZ_SCHEMA,
        new SszPublicKey(pubkey),
        SszBytes32.of(withdrawal_credentials),
        SszUInt64.of(effective_balance),
        SszBit.of(slashed),
        SszUInt64.of(activation_eligibility_epoch),
        SszUInt64.of(activation_epoch),
        SszUInt64.of(exit_epoch),
        SszUInt64.of(withdrawable_epoch));
  }

  public Validator(
      Bytes48 pubkey,
      Bytes32 withdrawal_credentials,
      UInt64 effective_balance,
      boolean slashed,
      UInt64 activation_eligibility_epoch,
      UInt64 activation_epoch,
      UInt64 exit_epoch,
      UInt64 withdrawable_epoch) {
    super(
        SSZ_SCHEMA,
        new SszPublicKey(pubkey),
        SszBytes32.of(withdrawal_credentials),
        SszUInt64.of(effective_balance),
        SszBit.of(slashed),
        SszUInt64.of(activation_eligibility_epoch),
        SszUInt64.of(activation_epoch),
        SszUInt64.of(exit_epoch),
        SszUInt64.of(withdrawable_epoch));
  }

  /**
   * Returns compressed BLS public key bytes
   *
   * <p>{@link BLSPublicKey} instance can be created with {@link
   * BLSPublicKey#fromBytesCompressed(Bytes48)} method. However this method is pretty 'expensive'
   * and the preferred way would be to use {@link Spec#getValidatorPubKey(BeaconState, UInt64)} if
   * the {@link BeaconState} instance and validator index is available
   */
  public Bytes48 getPubkeyBytes() {
    return getField0().getBytes();
  }

  public BLSPublicKey getPublicKey() {
    return getField0().getBLSPublicKey();
  }

  public Bytes32 getWithdrawal_credentials() {
    return getField1().get();
  }

  public UInt64 getEffective_balance() {
    return getField2().get();
  }

  public boolean isSlashed() {
    return getField3().get();
  }

  public UInt64 getActivation_eligibility_epoch() {
    return getField4().get();
  }

  public UInt64 getActivation_epoch() {
    return getField5().get();
  }

  public UInt64 getExit_epoch() {
    return getField6().get();
  }

  public UInt64 getWithdrawable_epoch() {
    return getField7().get();
  }

  public Validator withEffective_balance(UInt64 effective_balance) {
    return new Validator(
        getPubkeyBytes(),
        getWithdrawal_credentials(),
        effective_balance,
        isSlashed(),
        getActivation_eligibility_epoch(),
        getActivation_epoch(),
        getExit_epoch(),
        getWithdrawable_epoch());
  }

  public Validator withSlashed(boolean slashed) {
    return new Validator(
        getPubkeyBytes(),
        getWithdrawal_credentials(),
        getEffective_balance(),
        slashed,
        getActivation_eligibility_epoch(),
        getActivation_epoch(),
        getExit_epoch(),
        getWithdrawable_epoch());
  }

  public Validator withActivation_eligibility_epoch(UInt64 activation_eligibility_epoch) {
    return new Validator(
        getPubkeyBytes(),
        getWithdrawal_credentials(),
        getEffective_balance(),
        isSlashed(),
        activation_eligibility_epoch,
        getActivation_epoch(),
        getExit_epoch(),
        getWithdrawable_epoch());
  }

  public Validator withActivation_epoch(UInt64 activation_epoch) {
    return new Validator(
        getPubkeyBytes(),
        getWithdrawal_credentials(),
        getEffective_balance(),
        isSlashed(),
        getActivation_eligibility_epoch(),
        activation_epoch,
        getExit_epoch(),
        getWithdrawable_epoch());
  }

  public Validator withExit_epoch(UInt64 exit_epoch) {
    return new Validator(
        getPubkeyBytes(),
        getWithdrawal_credentials(),
        getEffective_balance(),
        isSlashed(),
        getActivation_eligibility_epoch(),
        getActivation_epoch(),
        exit_epoch,
        getWithdrawable_epoch());
  }

  public Validator withWithdrawable_epoch(UInt64 withdrawable_epoch) {
    return new Validator(
        getPubkeyBytes(),
        getWithdrawal_credentials(),
        getEffective_balance(),
        isSlashed(),
        getActivation_eligibility_epoch(),
        getActivation_epoch(),
        getExit_epoch(),
        withdrawable_epoch);
  }
}
