/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.test.acceptance.dsl;

import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeyGenerator;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class TekuDepositSender extends Node {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final ValidatorKeyGenerator validatorKeyGenerator = new ValidatorKeyGenerator();

  public TekuDepositSender(final Network network, final Spec spec) {
    super(network, TekuBeaconNode.TEKU_DOCKER_IMAGE_NAME, TekuDockerVersion.LOCAL_BUILD, LOG);
    this.spec = spec;
  }

  public ValidatorKeystores generateValidatorKeys(
      final int numberOfValidators, final Eth1Address withdrawalAddress) {
    return new ValidatorKeystores(
        validatorKeyGenerator
            .generateKeysStream(numberOfValidators, withdrawalAddress)
            .collect(Collectors.toList()));
  }

  public ValidatorKeystores generateValidatorKeys(final int numberOfValidators) {
    return generateValidatorKeys(numberOfValidators, false);
  }

  public ValidatorKeystores generateValidatorKeys(
      final int numberOfValidators, final boolean isLocked) {
    return new ValidatorKeystores(
        validatorKeyGenerator
            .generateKeysStream(numberOfValidators, isLocked)
            .collect(Collectors.toList()));
  }

  public UInt64 getMinDepositAmount() {
    return spec.getGenesisSpecConfig().getMinDepositAmount();
  }

  public UInt64 getMaxEffectiveBalance() {
    return spec.getGenesisSpecConfig().getMaxEffectiveBalance();
  }
}
