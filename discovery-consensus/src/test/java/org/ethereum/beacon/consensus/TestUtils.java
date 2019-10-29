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

import static org.ethereum.beacon.core.spec.SignatureDomains.DEPOSIT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.operations.deposit.DepositData;
import org.ethereum.beacon.core.types.BLSPubkey;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.crypto.BLS381;
import org.ethereum.beacon.crypto.BLS381.KeyPair;
import org.ethereum.beacon.crypto.BLS381.PrivateKey;
import org.ethereum.beacon.crypto.BLS381.Signature;
import org.ethereum.beacon.crypto.MessageParameters;
import org.javatuples.Pair;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.Bytes4;
import tech.pegasys.artemis.util.bytes.Bytes48;
import tech.pegasys.artemis.util.bytes.Bytes96;
import tech.pegasys.artemis.util.uint.UInt64;

public class TestUtils {

  private static Pair<List<Deposit>, List<KeyPair>> cachedDeposits =
      Pair.with(new ArrayList<>(), new ArrayList<>());

  public static synchronized Pair<List<Deposit>, List<KeyPair>> getAnyDeposits(
      Random rnd, BeaconChainSpec spec, int count) {
    if (count > cachedDeposits.getValue0().size()) {
      Pair<List<Deposit>, List<KeyPair>> newDeposits =
          generateRandomDeposits(rnd, spec, count - cachedDeposits.getValue0().size());
      cachedDeposits.getValue0().addAll(newDeposits.getValue0());
      cachedDeposits.getValue1().addAll(newDeposits.getValue1());
    }
    return Pair.with(
        cachedDeposits.getValue0().subList(0, count), cachedDeposits.getValue1().subList(0, count));
  }

  private static synchronized Pair<List<Deposit>, List<KeyPair>> generateRandomDeposits(
      Random rnd, BeaconChainSpec spec, int count) {
    List<Deposit> deposits = new ArrayList<>();
    List<KeyPair> validatorsKeys = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      KeyPair keyPair = KeyPair.create(PrivateKey.create(Bytes32.random(rnd)));
      Hash32 withdrawalCredentials = Hash32.random(rnd);
      DepositData depositDataWithoutSignature =
          new DepositData(
              BLSPubkey.wrap(Bytes48.leftPad(keyPair.getPublic().getEncodedBytes())),
              withdrawalCredentials,
              spec.getConstants().getMaxEffectiveBalance(),
              BLSSignature.wrap(Bytes96.ZERO));
      Hash32 msgHash = spec.signing_root(depositDataWithoutSignature);
      UInt64 domain = spec.compute_domain(DEPOSIT, Bytes4.ZERO);
      Signature signature = BLS381.sign(MessageParameters.create(msgHash, domain), keyPair);

      validatorsKeys.add(keyPair);

      Deposit deposit =
          Deposit.create(
              Collections.nCopies(
                  spec.getConstants().getDepositContractTreeDepthPlusOne().getIntValue(),
                  Hash32.random(rnd)),
              new DepositData(
                  BLSPubkey.wrap(Bytes48.leftPad(keyPair.getPublic().getEncodedBytes())),
                  withdrawalCredentials,
                  spec.getConstants().getMaxEffectiveBalance(),
                  BLSSignature.wrap(signature.getEncoded())));
      deposits.add(deposit);
    }

    return Pair.with(deposits, validatorsKeys);
  }

  public static List<Deposit> generateRandomDepositsWithoutSig(
      Random rnd, BeaconChainSpec spec, int count) {
    List<Deposit> deposits = new ArrayList<>();

    UInt64 counter = UInt64.ZERO;
    for (int i = 0; i < count; i++) {
      Hash32 withdrawalCredentials = Hash32.random(rnd);

      BLSPubkey pubkey = BLSPubkey.wrap(Bytes48.leftPad(counter.toBytesBigEndian()));
      Deposit deposit =
          Deposit.create(
              Collections.singletonList(Hash32.random(rnd)),
              new DepositData(
                  pubkey,
                  withdrawalCredentials,
                  spec.getConstants().getMaxEffectiveBalance(),
                  BLSSignature.ZERO));
      deposits.add(deposit);
      counter = counter.increment();
    }
    return deposits;
  }
}
