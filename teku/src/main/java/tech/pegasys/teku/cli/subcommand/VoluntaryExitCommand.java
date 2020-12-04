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

package tech.pegasys.teku.cli.subcommand;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.google.common.base.Throwables;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import picocli.CommandLine;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.ValidatorClientOptions;
import tech.pegasys.teku.cli.options.ValidatorKeysOptions;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.core.signatures.RejectingSlashingProtector;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.GlobalConfigurationBuilder;
import tech.pegasys.teku.util.config.InvalidConfigurationException;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.remote.apiclient.OkHttpValidatorRestApiClient;

@CommandLine.Command(
    name = "voluntary-exit",
    description = "Create and sign a voluntary exit for a specified validator.",
    mixinStandardHelpOptions = true,
    abbreviateSynopsis = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class VoluntaryExitCommand implements Runnable {
  public static final SubCommandLogger SUB_COMMAND_LOG = new SubCommandLogger();
  private OkHttpValidatorRestApiClient apiClient;
  private tech.pegasys.teku.datastructures.state.Fork fork;
  private Bytes32 genesisRoot;
  private Map<BLSPublicKey, Validator> blsPublicKeyValidatorMap;
  private TekuConfiguration config;

  @CommandLine.Mixin(name = "Validator Keys")
  private ValidatorKeysOptions validatorKeysOptions;

  @CommandLine.Mixin(name = "Validator Client")
  private ValidatorClientOptions validatorClientOptions;

  @CommandLine.Option(
      names = {"--epoch"},
      paramLabel = "<EPOCH>",
      description =
          "Earliest epoch that the voluntary exit can be processed. Defaults to current epoch.",
      arity = "1")
  private UInt64 epoch;

  @CommandLine.Option(
      names = {"--confirmation-enabled"},
      description = "Request confirmation before submitting voluntary exits.",
      paramLabel = "<BOOLEAN>",
      arity = "1")
  private boolean confirmationEnabled = true;

  @Override
  public void run() {
    SUB_COMMAND_LOG.display("Loading configuration...");
    try {
      initialise();
      if (confirmationEnabled) {
        confirmExits();
      }
      getValidatorIndices(blsPublicKeyValidatorMap).forEach(this::submitExitForValidator);
    } catch (UncheckedIOException ex) {
      if (Throwables.getRootCause(ex) instanceof ConnectException) {
        SUB_COMMAND_LOG.error(getFailedToConnectMessage());
        System.exit(1);
      }
    }
  }

  private void confirmExits() {
    SUB_COMMAND_LOG.display("Exits are going to be generated for validators: ");
    SUB_COMMAND_LOG.display(getValidatorAbbreviatedKeys());
    SUB_COMMAND_LOG.display("");
    SUB_COMMAND_LOG.display(
        "These validators won't be able to be re-activated, and withdrawals aren't likely to be possible until Phase 2 of eth2 Mainnet.");
    SUB_COMMAND_LOG.display("Are you sure you wish to continue (yes/no)? ");
    Scanner scanner = new Scanner(System.in, Charset.defaultCharset().name());
    final String confirmation = scanner.next();

    if (!confirmation.equalsIgnoreCase("yes")) {
      SUB_COMMAND_LOG.display("Cancelled sending voluntary exit.");
      System.exit(1);
    }
  }

  private String getValidatorAbbreviatedKeys() {
    return blsPublicKeyValidatorMap.keySet().stream()
        .map(key -> key.toAbbreviatedString())
        .collect(Collectors.joining(", "));
  }

  private Map<BLSPublicKey, Integer> getValidatorIndices(
      Map<BLSPublicKey, Validator> blsPublicKeyValidatorMap) {
    Map<BLSPublicKey, Integer> validatorIndices = new HashMap<>();
    apiClient
        .getValidators(
            blsPublicKeyValidatorMap.keySet().stream()
                .map(BLSPublicKey::toString)
                .collect(Collectors.toList()))
        .ifPresent(
            validatorResponses ->
                validatorResponses.forEach(
                    response ->
                        validatorIndices.put(
                            response.validator.pubkey.asBLSPublicKey(),
                            response.index.intValue())));
    return validatorIndices;
  }

  private void submitExitForValidator(final BLSPublicKey publicKey, final Integer validatorIndex) {
    try {
      final ForkInfo forkInfo = new ForkInfo(fork, genesisRoot);
      final VoluntaryExit message = new VoluntaryExit(epoch, UInt64.valueOf(validatorIndex));
      final BLSSignature signature =
          blsPublicKeyValidatorMap
              .get(publicKey)
              .getSigner()
              .signVoluntaryExit(message, forkInfo)
              .join();
      apiClient.sendVoluntaryExit(new SignedVoluntaryExit(message, signature));
      SUB_COMMAND_LOG.display(
          "Exit for validator " + publicKey.toAbbreviatedString() + " submitted.");
    } catch (IllegalArgumentException ex) {
      SUB_COMMAND_LOG.error(
          "Failed to submit exit for validator " + publicKey.toAbbreviatedString());
      SUB_COMMAND_LOG.error(ex.getMessage());
    }
  }

  private Optional<UInt64> getEpoch() {
    return apiClient
        .getBlockHeader("head")
        .map(response -> compute_epoch_at_slot(response.data.header.message.slot));
  }

  private Optional<Bytes32> getGenesisRoot() {
    return apiClient.getGenesis().map(response -> response.getData().getGenesisValidatorsRoot());
  }

  private Optional<tech.pegasys.teku.datastructures.state.Fork> getFork() {
    return apiClient.getFork().map(Fork::asInternalFork);
  }

  private void initialise() {
    config = tekuConfiguration();
    final AsyncRunnerFactory asyncRunnerFactory =
        new AsyncRunnerFactory(new MetricTrackingExecutorFactory(new NoOpMetricsSystem()));
    final AsyncRunner asyncRunner = asyncRunnerFactory.create("voluntary-exits", 8);
    final OkHttpClient okHttpClient =
        new OkHttpClient.Builder()
            .readTimeout(Constants.SECONDS_PER_SLOT * 2, TimeUnit.SECONDS)
            .build();
    apiClient =
        config
            .validatorClient()
            .getValidatorConfig()
            .getBeaconNodeApiEndpoint()
            .map(endpoint -> new OkHttpValidatorRestApiClient(HttpUrl.get(endpoint), okHttpClient))
            .orElseThrow();

    final Optional<tech.pegasys.teku.datastructures.state.Fork> maybeFork = getFork();
    if (maybeFork.isEmpty()) {
      SUB_COMMAND_LOG.error("Unable to fetch fork, cannot generate an exit.");
      System.exit(1);
    }
    fork = maybeFork.get();
    validateOrDefaultEpoch();

    // get genesis time
    final Optional<Bytes32> maybeRoot = getGenesisRoot();
    if (maybeRoot.isEmpty()) {
      SUB_COMMAND_LOG.error("Unable to fetch genesis data, cannot generate an exit.");
      System.exit(1);
    }
    genesisRoot = maybeRoot.get();

    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(new RejectingSlashingProtector(), asyncRunner);

    try {
      blsPublicKeyValidatorMap =
          validatorLoader.initializeValidators(
              config.validatorClient().getValidatorConfig(), config.global());
    } catch (InvalidConfigurationException ex) {
      SUB_COMMAND_LOG.error(ex.getMessage());
      System.exit(1);
    }
    if (blsPublicKeyValidatorMap.isEmpty()) {
      SUB_COMMAND_LOG.error("No validators were found to exit.");
      System.exit(1);
    }
  }

  private void validateOrDefaultEpoch() {
    final Optional<UInt64> maybeEpoch = getEpoch();

    if (epoch == null) {
      if (maybeEpoch.isEmpty()) {
        SUB_COMMAND_LOG.error(
            "Could not calculate epoch from latest block header, please specify --epoch");
        System.exit(1);
      }
      epoch = maybeEpoch.orElseThrow();
    } else if (maybeEpoch.isPresent() && epoch.isGreaterThan(maybeEpoch.get())) {
      SUB_COMMAND_LOG.error(
          String.format(
              "The specified epoch %s is greater than current epoch %s, cannot continue.",
              epoch, maybeEpoch.get()));
      System.exit(1);
    }
  }

  private TekuConfiguration tekuConfiguration() {
    final TekuConfiguration.Builder builder = TekuConfiguration.builder();
    builder.globalConfig(this::buildGlobalConfiguration);
    validatorKeysOptions.configure(builder);

    validatorClientOptions.configure(builder);
    // we don't use the data path, but keep configuration happy.
    builder.data(config -> config.dataBasePath(Path.of(".")));
    builder.validator(config -> config.validatorKeystoreLockingEnabled(false));
    return builder.build();
  }

  private String getFailedToConnectMessage() {
    return String.format(
        "Failed to connect to beacon node. Check that %s is available.",
        config
            .validatorClient()
            .getValidatorConfig()
            .getBeaconNodeApiEndpoint()
            .orElse(URI.create("http://127.0.0.1:5051"))
            .toString());
  }

  private void buildGlobalConfiguration(final GlobalConfigurationBuilder builder) {
    builder.setMetricsEnabled(false);
  }
}
