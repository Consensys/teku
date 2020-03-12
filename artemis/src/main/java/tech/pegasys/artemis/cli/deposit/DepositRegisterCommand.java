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

package tech.pegasys.artemis.cli.deposit;

import static tech.pegasys.artemis.cli.deposit.CommonParams.sendDeposit;
import static tech.pegasys.teku.logging.StatusLogger.STATUS_LOG;

import org.apache.tuweni.bytes.Bytes;
import picocli.CommandLine;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.artemis.services.powchain.DepositTransactionSender;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSecretKey;
import tech.pegasys.artemis.util.cli.VersionProvider;

@CommandLine.Command(
    name = "register",
    description =
        "Register a validator from existing keys but sending a deposit transaction to an Ethereum 1 node",
    mixinStandardHelpOptions = true,
    abbreviateSynopsis = true,
    versionProvider = VersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class DepositRegisterCommand implements Runnable {
  @Mixin private CommonParams params;

  @Option(
      names = {"-s", "--signing-key"},
      paramLabel = "<PRIVATE_KEY>",
      required = true,
      description = "Private signing key for the validator")
  private String validatorKey;

  @Option(
      names = {"-w", "--withdrawal-key"},
      paramLabel = "<PUBLIC_KEY>",
      required = true,
      description = "Public withdrawal key for the validator")
  private String withdrawalKey;

  @Override
  public void run() {
    final CommonParams _params = params; // making it effective final as it gets injected by PicoCLI
    try (_params) {
      final DepositTransactionSender sender = params.createTransactionSender();
      sendDeposit(
              sender,
              privateKeyToKeyPair(validatorKey),
              BLSPublicKey.fromBytesCompressed(Bytes.fromHexString(withdrawalKey)),
              params.getAmount())
          .get();
    } catch (final Throwable t) {
      STATUS_LOG.sendDepositFailure(t);
      System.exit(1); // Web3J creates a non-daemon thread we can't shut down. :(
    }
    System.exit(0); // Web3J creates a non-daemon thread we can't shut down. :(
  }

  private BLSKeyPair privateKeyToKeyPair(final String validatorKey) {
    return new BLSKeyPair(BLSSecretKey.fromBytes(Bytes.fromHexString(validatorKey)));
  }
}
