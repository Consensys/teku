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

package tech.pegasys.artemis.datastructures.state;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.backing.ContainerViewRead;
import tech.pegasys.artemis.util.backing.view.BasicViews.BitView;
import tech.pegasys.artemis.util.backing.view.BasicViews.Bytes32View;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;
import tech.pegasys.artemis.util.backing.view.ViewUtils;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

@JsonAutoDetect(getterVisibility = Visibility.NONE)
public interface Validator
    extends ContainerViewRead, SimpleOffsetSerializable, Merkleizable, SSZContainer {

  static Validator createEmpty() {
    return new ValidatorImpl();
  }

  static Validator create(
      BLSPublicKey pubkey,
      Bytes32 withdrawal_credentials,
      UnsignedLong effective_balance,
      boolean slashed,
      UnsignedLong activation_eligibility_epoch,
      UnsignedLong activation_epoch,
      UnsignedLong exit_epoch,
      UnsignedLong withdrawable_epoch) {
    MutableValidator validator = createEmpty().createWritableCopy();
    validator.setPubkey(pubkey);
    validator.setWithdrawal_credentials(withdrawal_credentials);
    validator.setEffective_balance(effective_balance);
    validator.setSlashed(slashed);
    validator.setActivation_eligibility_epoch(activation_eligibility_epoch);
    validator.setActivation_epoch(activation_epoch);
    validator.setExit_epoch(exit_epoch);
    validator.setWithdrawable_epoch(withdrawable_epoch);
    return validator.commitChanges();
  }

  @JsonProperty
  default BLSPublicKey getPubkey() {
    return BLSPublicKey.fromBytes(ViewUtils.getAllBytes(getAny(0)));
  }

  @JsonProperty
  default Bytes32 getWithdrawal_credentials() {
    return ((Bytes32View) get(1)).get();
  }

  @JsonProperty
  default UnsignedLong getEffective_balance() {
    return ((UInt64View) get(2)).get();
  }

  @JsonProperty
  default boolean isSlashed() {
    return ((BitView) get(3)).get();
  }

  @JsonProperty
  default UnsignedLong getActivation_eligibility_epoch() {
    return ((UInt64View) get(4)).get();
  }

  @JsonProperty
  default UnsignedLong getActivation_epoch() {
    return ((UInt64View) get(5)).get();
  }

  @JsonProperty
  default UnsignedLong getExit_epoch() {
    return ((UInt64View) get(6)).get();
  }

  @JsonProperty
  default UnsignedLong getWithdrawable_epoch() {
    return ((UInt64View) get(7)).get();
  }

  @Override
  default Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }

  @Override
  MutableValidator createWritableCopy();
}
