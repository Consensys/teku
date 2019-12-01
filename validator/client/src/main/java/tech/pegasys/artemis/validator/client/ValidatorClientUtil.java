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

package tech.pegasys.artemis.validator.client;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;

import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.tx.gas.DefaultGasProvider;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.mikuli.BLS12381;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.PublicKey;

public class ValidatorClientUtil {

  public static boolean is_proposer(BeaconState state, int validator_index) {
    return get_beacon_proposer_index(state) == validator_index;
  }

  public static Bytes blsSignatureHelper(
      KeyPair blsKeys, Bytes32 withdrawal_credentials, long amount) {
    Bytes deposit_data =
        Bytes.wrap(
            Bytes.ofUnsignedLong(amount),
            withdrawal_credentials,
            getPublicKeyFromKeyPair(blsKeys).toBytesCompressed());
    return generateProofOfPossession(blsKeys, deposit_data);
  }

  public static Bytes generateProofOfPossession(KeyPair blsKeys, Bytes deposit_data) {
    return BLS12381
        .sign(blsKeys, deposit_data, Constants.DOMAIN_DEPOSIT.getWrappedBytes())
        .signature()
        .toBytesCompressed();
  }

  public static PublicKey getPublicKeyFromKeyPair(KeyPair blsKeys) {
    return BLSPublicKey.fromBytesCompressed(blsKeys.publicKey().toBytesCompressed()).getPublicKey();
  }

  public static void registerValidatorEth1(
      Validator validator,
      long amount,
      String address,
      Web3j web3j,
      DefaultGasProvider gasProvider,
      Bytes depositRoot,
      BLSSignature sig)
      throws Exception {
    Credentials credentials =
        Credentials.create(validator.getSecpKeys().secretKey().bytes().toHexString());
    DepositContract contract = DepositContract.load(address, web3j, credentials, gasProvider);

    contract
        .deposit(
            validator.getBlsKeys().getPublicKey().toBytesCompressed().toArray(),
            validator.getWithdrawal_credentials().toArray(),
            sig.toBytes().toArray(),
            depositRoot.toArray(),
            new BigInteger(amount + "000000000"))
        .send();
  }
}
