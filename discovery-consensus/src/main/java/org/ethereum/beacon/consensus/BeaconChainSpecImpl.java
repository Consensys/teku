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

package org.ethereum.beacon.consensus;

import java.util.function.Function;
import org.ethereum.beacon.consensus.hasher.ObjectHasher;
import org.ethereum.beacon.core.spec.SpecConstants;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.BytesValue;

/** Default implementation of {@link BeaconChainSpec}. */
public class BeaconChainSpecImpl implements BeaconChainSpec {
  private final SpecConstants constants;
  private final Function<BytesValue, Hash32> hashFunction;
  private final ObjectHasher<Hash32> objectHasher;
  private final boolean blsVerify;
  private final boolean blsVerifyProofOfPossession;
  private final boolean verifyDepositProof;
  private final boolean computableGenesisTime;

  public BeaconChainSpecImpl(
      SpecConstants constants,
      Function<BytesValue, Hash32> hashFunction,
      ObjectHasher<Hash32> objectHasher,
      boolean blsVerify,
      boolean blsVerifyProofOfPossession,
      boolean verifyDepositProof,
      boolean computableGenesisTime) {
    this.constants = constants;
    this.hashFunction = hashFunction;
    this.objectHasher = objectHasher;
    this.blsVerify = blsVerify;
    this.blsVerifyProofOfPossession = blsVerifyProofOfPossession;
    this.verifyDepositProof = verifyDepositProof;
    this.computableGenesisTime = computableGenesisTime;
  }

  @Override
  public SpecConstants getConstants() {
    return constants;
  }

  @Override
  public ObjectHasher<Hash32> getObjectHasher() {
    return objectHasher;
  }

  @Override
  public Function<BytesValue, Hash32> getHashFunction() {
    return hashFunction;
  }

  @Override
  public boolean isBlsVerify() {
    return blsVerify;
  }

  @Override
  public boolean isBlsVerifyProofOfPossession() {
    return blsVerifyProofOfPossession;
  }

  @Override
  public boolean isVerifyDepositProof() {
    return verifyDepositProof;
  }

  @Override
  public boolean isComputableGenesisTime() {
    return computableGenesisTime;
  }
}
