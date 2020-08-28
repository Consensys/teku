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

package tech.pegasys.teku.cli.deposit;

import static tech.pegasys.teku.util.config.Constants.MAX_EFFECTIVE_BALANCE;

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Optional;
import java.util.function.IntConsumer;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import picocli.CommandLine.TypeConversionException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.Eth1Address;
import tech.pegasys.teku.util.config.NetworkDefinition;

public class RegisterParams {

  @Spec private CommandSpec spec;

  @Option(
      names = {"-n", "--network"},
      required = true,
      paramLabel = "<NETWORK>",
      description = "Represents which network to use.",
      arity = "1")
  private String network;

  @Option(
      names = {"--eth1-endpoint"},
      required = true,
      paramLabel = "<URL>",
      description = "JSON-RPC endpoint URL for the Ethereum 1 node to send transactions via")
  private String eth1NodeUrl;

  @Option(
      names = {"--eth1-deposit-contract-address"},
      paramLabel = "<ADDRESS>",
      description = "Address of the deposit contract")
  private Eth1Address contractAddress;

  @ArgGroup(exclusive = true, multiplicity = "1")
  private Eth1PrivateKeyOptions eth1PrivateKeyOptions;

  @Option(
      names = {"--deposit-amount-gwei"},
      paramLabel = "<GWEI>",
      converter = UInt64Converter.class,
      description =
          "Deposit amount in Gwei. Defaults to the amount required to activate a validator on the specified network.")
  private UInt64 amount;

  private final IntConsumer shutdownFunction;
  private final ConsoleAdapter consoleAdapter;

  RegisterParams() {
    this.shutdownFunction = System::exit;
    this.consoleAdapter = new ConsoleAdapter();
  }

  @VisibleForTesting
  public RegisterParams(
      final CommandSpec commandSpec,
      final Eth1PrivateKeyOptions eth1PrivateKeyOptions,
      final IntConsumer shutdownFunction,
      final ConsoleAdapter consoleAdapter) {
    this.spec = commandSpec;
    this.eth1PrivateKeyOptions = eth1PrivateKeyOptions;
    this.shutdownFunction = shutdownFunction;
    this.consoleAdapter = consoleAdapter;
  }

  public RegisterAction createRegisterAction(final boolean verboseOutputEnabled) {
    final NetworkDefinition networkDefinition = NetworkDefinition.fromCliArg(network);
    Constants.setConstants(networkDefinition.getConstants());
    return new RegisterAction(
        eth1NodeUrl,
        getEth1Credentials(),
        getContractAddress(networkDefinition),
        verboseOutputEnabled,
        getAmount(),
        shutdownFunction,
        consoleAdapter);
  }

  UInt64 getAmount() {
    return Optional.ofNullable(this.amount).orElse(UInt64.valueOf(MAX_EFFECTIVE_BALANCE));
  }

  private Eth1Address getContractAddress(final NetworkDefinition networkDefinition) {
    return Optional.ofNullable(this.contractAddress)
        .or(networkDefinition::getEth1DepositContractAddress)
        .orElseThrow(
            () ->
                new ParameterException(
                    spec.commandLine(),
                    "Selected network does not define a deposit contract address. Please specify one with --eth1-deposit-contract-address"));
  }

  Credentials getEth1Credentials() {
    if (eth1PrivateKeyOptions.eth1PrivateKey != null) {
      return Credentials.create(eth1PrivateKeyOptions.eth1PrivateKey);
    } else if (eth1PrivateKeyOptions.keystoreOptions != null) {
      return getEth1CredentialsFromKeystore();
    } else {
      // not meant to happen
      throw new IllegalStateException("Eth1 Private Key Options are not initialized");
    }
  }

  private Credentials getEth1CredentialsFromKeystore() {
    final String keystorePassword =
        KeystorePasswordOptions.readFromFile(
            spec.commandLine(), eth1PrivateKeyOptions.keystoreOptions.eth1KeystorePasswordFile);
    final File eth1KeystoreFile = eth1PrivateKeyOptions.keystoreOptions.eth1KeystoreFile;
    try {
      return WalletUtils.loadCredentials(keystorePassword, eth1KeystoreFile);
    } catch (final FileNotFoundException e) {
      throw new ParameterException(
          spec.commandLine(), "Error: File not found: " + eth1KeystoreFile, e);
    } catch (final IOException e) {
      throw new ParameterException(
          spec.commandLine(),
          "Error: Unexpected IO Error while reading Eth1 keystore ["
              + eth1KeystoreFile
              + "] : "
              + e.getMessage(),
          e);
    } catch (final CipherException e) {
      throw new ParameterException(
          spec.commandLine(),
          "Error: Unable to decrypt Eth1 keystore [" + eth1KeystoreFile + "] : " + e.getMessage(),
          e);
    }
  }

  private static class UInt64Converter implements CommandLine.ITypeConverter<UInt64> {
    @Override
    public UInt64 convert(final String value) {
      try {
        return UInt64.valueOf(value);
      } catch (final NumberFormatException e) {
        throw new TypeConversionException(
            "Invalid format: must be a numeric value but was " + value);
      }
    }
  }
}
