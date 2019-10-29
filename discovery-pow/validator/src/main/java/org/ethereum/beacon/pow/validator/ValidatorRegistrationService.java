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

package org.ethereum.beacon.pow.validator;

import java.util.Optional;
import javax.annotation.Nullable;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.validator.ValidatorService;
import org.ethereum.beacon.validator.crypto.BLS381Credentials;
import tech.pegasys.artemis.ethereum.core.Address;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.BytesValue;

/**
 * Registers validator in DepositContract and runs all way to active validator according to the spec
 * <a
 * href="https://github.com/ethereum/eth2.0-specs/blob/dev/specs/validator/0_beacon-chain-validator.md">https://github.com/ethereum/eth2.0-specs/blob/dev/specs/validator/0_beacon-chain-validator.md</a>
 */
public interface ValidatorRegistrationService {
  void start(
      BLS381Credentials credentials,
      @Nullable Hash32 withdrawalCredentials,
      @Nullable Gwei amount,
      @Nullable Address eth1From,
      @Nullable BytesValue eth1PrivKey);

  Optional<ValidatorService> getValidatorService();

  enum RegistrationStage {
    SEND_TX, // Send Deposit in Eth1
    AWAIT_INCLUSION, // Await processing of DepositContract root with our validator by smb
    AWAIT_ACTIVATION, // Await activation epoch
    VALIDATOR_START, // Start validator proposer / attestation services
    COMPLETE // We are over
  }
}
