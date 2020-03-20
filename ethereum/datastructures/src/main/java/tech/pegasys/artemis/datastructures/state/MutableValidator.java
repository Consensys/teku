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

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.ContainerViewWrite;
import tech.pegasys.artemis.util.backing.view.BasicViews.BitView;
import tech.pegasys.artemis.util.backing.view.BasicViews.Bytes32View;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;
import tech.pegasys.artemis.util.backing.view.ViewUtils;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

public interface MutableValidator extends Validator, ContainerViewWrite {

  default void setPubkey(BLSPublicKey pubkey) {
    set(0, ViewUtils.createVectorFromBytes(pubkey.toBytes()));
  }

  default void setWithdrawal_credentials(Bytes32 withdrawal_credentials) {
    set(1, new Bytes32View(withdrawal_credentials));
  }


  default void setEffective_balance(UnsignedLong effective_balance) {
    set(2, new UInt64View(effective_balance));
  }

  default void setSlashed(boolean slashed) {
    set(3, new BitView(slashed));
  }

  default void setActivation_eligibility_epoch(UnsignedLong activation_eligibility_epoch) {
    set(4, new UInt64View(activation_eligibility_epoch));
  }

  default void setActivation_epoch(UnsignedLong activation_epoch) {
    set(5, new UInt64View(activation_epoch));
  }

  default void setExit_epoch(UnsignedLong exit_epoch) {
    set(6, new UInt64View(exit_epoch));
  }

  default void setWithdrawable_epoch(UnsignedLong withdrawable_epoch) {
    set(7, new UInt64View(withdrawable_epoch));
  }

  @Override
  Validator commitChanges();
}
