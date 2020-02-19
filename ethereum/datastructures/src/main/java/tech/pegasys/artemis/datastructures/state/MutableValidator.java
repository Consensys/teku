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
import tech.pegasys.artemis.util.backing.ContainerViewWrite;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

public interface MutableValidator extends Validator, ContainerViewWrite<ViewRead> {

  void setPubkey(BLSPublicKey pubkey);

  void setEffective_balance(UnsignedLong effective_balance);

  void setSlashed(boolean slashed);

  void setActivation_eligibility_epoch(UnsignedLong activation_eligibility_epoch);

  void setActivation_epoch(UnsignedLong activation_epoch);

  void setExit_epoch(UnsignedLong exit_epoch);

  void setWithdrawable_epoch(UnsignedLong withdrawable_epoch);
}
