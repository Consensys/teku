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
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.backing.ContainerViewRead;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public interface ValidatorRead extends ContainerViewRead<ViewRead>, Merkleizable,
    SimpleOffsetSerializable, SSZContainer {

  BLSPublicKey getPubkey();

  Bytes32 getWithdrawal_credentials();

  UnsignedLong getEffective_balance();

  boolean isSlashed();

  UnsignedLong getActivation_eligibility_epoch();

  UnsignedLong getActivation_epoch();

  UnsignedLong getExit_epoch();

  UnsignedLong getWithdrawable_epoch();

  @Override
  ValidatorWrite createWritableCopy();
}
