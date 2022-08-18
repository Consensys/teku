/*
 * Copyright ConsenSys Software Inc., 2022
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

import static tech.pegasys.teku.cli.subcommand.RemoteSpecLoader.getSpec;

import com.google.common.base.Throwables;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import picocli.CommandLine;
import picocli.CommandLine.Help.Visibility;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.ValidatorClientOptions;
import tech.pegasys.teku.cli.options.ValidatorKeysOptions;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.RejectingSlashingProtector;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.loader.PublicKeyLoader;
import tech.pegasys.teku.validator.client.loader.SlashingProtectionLogger;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.remote.apiclient.OkHttpValidatorRestApiClient;

@CommandLine.Command(
    name = "voluntary-exit",
    description = "Create and sign a voluntary exit for a specified validator.",
    showDefaultValues = true,
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
  private static final int MAX_PUBLIC_KEY_BATCH_SIZE = 50;
  private OkHttpValidatorRestApiClient apiClient;
  private Fork fork;
  private Bytes32 genesisRoot;
  private OwnedValidators validators;
  private TekuConfiguration config;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private Spec spec;

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
      showDefaultValue = Visibility.ALWAYS,
      fallbackValue = "true",
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
      getValidatorIndices(validators).forEach(this::submitExitForValidator);
    } catch (Exception ex) {
      if (ex instanceof InvalidConfigurationException
          && Throwables.getRootCause(ex) instanceof ConnectException) {
        SUB_COMMAND_LOG.error(getFailedToConnectMessage());
      } else {
        SUB_COMMAND_LOG.error("Fatal error in VoluntaryExit. Exiting", ex);
      }
      System.exit(1);
    }
  }

  private void confirmExits() {
    SUB_COMMAND_LOG.display("Exits are going to be generated for validators: ");
    SUB_COMMAND_LOG.display(getValidatorAbbreviatedKeys());
    SUB_COMMAND_LOG.display("Epoch: " + epoch.toString());
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
    return validators.getPublicKeys().stream()
        .map(BLSPublicKey::toAbbreviatedString)
        .collect(Collectors.joining(", "));
  }

  private Object2IntMap<BLSPublicKey> getValidatorIndices(
      OwnedValidators blsPublicKeyValidatorMap) {
    final Object2IntMap<BLSPublicKey> validatorIndices = new Object2IntOpenHashMap<>();
    final List<String> publicKeys =
        blsPublicKeyValidatorMap.getPublicKeys().stream()
            .map(BLSPublicKey::toString)
            .collect(Collectors.toList());
    for (int i = 0; i < publicKeys.size(); i += MAX_PUBLIC_KEY_BATCH_SIZE) {
      final List<String> batch =
          publicKeys.subList(i, Math.min(publicKeys.size(), i + MAX_PUBLIC_KEY_BATCH_SIZE));
      apiClient
          .getValidators(batch)
          .ifPresent(
              validatorResponses ->
                  validatorResponses.forEach(
                      response ->
                          validatorIndices.put(
                              response.validator.pubkey.asBLSPublicKey(),
                              response.index.intValue())));

      batch.forEach(
          key -> {
            if (!validatorIndices.containsKey(
                BLSPublicKey.fromBytesCompressed(Bytes48.fromHexString(key)))) {
              SUB_COMMAND_LOG.error("Validator not found: " + key);
            }
          });
    }
    return validatorIndices;
  }

  private void submitExitForValidator(final BLSPublicKey publicKey, final int validatorIndex) {
    try {
      final ForkInfo forkInfo = new ForkInfo(fork, genesisRoot);
      final VoluntaryExit message = new VoluntaryExit(epoch, UInt64.valueOf(validatorIndex));
      final BLSSignature signature =
          validators
              .getValidator(publicKey)
              .orElseThrow()
              .getSigner()
              .signVoluntaryExit(message, forkInfo)
              .join();
      Optional<PostDataFailureResponse> response =
          apiClient.sendVoluntaryExit(new SignedVoluntaryExit(message, signature));
      if (response.isPresent()) {
        SUB_COMMAND_LOG.error(response.get().message);
      } else {
        SUB_COMMAND_LOG.display(
            "Exit for validator " + publicKey.toAbbreviatedString() + " submitted.");
      }

    } catch (IllegalArgumentException ex) {
      SUB_COMMAND_LOG.error(
          "Failed to submit exit for validator " + publicKey.toAbbreviatedString());
      SUB_COMMAND_LOG.error(ex.getMessage());
    }
  }

  private Optional<UInt64> getEpoch() {
    return apiClient
        .getBlockHeader("head")
        .map(response -> spec.computeEpochAtSlot(response.data.header.message.slot));
  }

  private Optional<Bytes32> getGenesisRoot() {
    return apiClient.getGenesis().map(response -> response.getData().getGenesisValidatorsRoot());
  }

  private void initialise() {
    config = tekuConfiguration();
    final AsyncRunnerFactory asyncRunnerFactory =
        AsyncRunnerFactory.createDefault(new MetricTrackingExecutorFactory(metricsSystem));
    final AsyncRunner asyncRunner = asyncRunnerFactory.create("voluntary-exits", 8);

    apiClient =
        config
            .validatorClient()
            .getValidatorConfig()
            .getPrimaryBeaconNodeApiEndpoint()
            .map(RemoteSpecLoader::createApiClient)
            .orElseThrow();

    spec = getSpec(apiClient);

    validateOrDefaultEpoch();
    fork = spec.fork(epoch);

    // get genesis time
    final Optional<Bytes32> maybeRoot = getGenesisRoot();
    if (maybeRoot.isEmpty()) {
      SUB_COMMAND_LOG.error("Unable to fetch genesis data, cannot generate an exit.");
      System.exit(1);
    }
    genesisRoot = maybeRoot.get();

    final RejectingSlashingProtector slashingProtector = new RejectingSlashingProtector();
    final SlashingProtectionLogger slashingProtectionLogger =
        new SlashingProtectionLogger(
            slashingProtector, spec, asyncRunner, ValidatorLogger.VALIDATOR_LOGGER);

    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config.validatorClient().getValidatorConfig(),
            config.validatorClient().getInteropConfig(),
            new RejectingSlashingProtector(),
            slashingProtectionLogger,
            new PublicKeyLoader(),
            asyncRunner,
            metricsSystem,
            Optional.empty());

    try {
      validatorLoader.loadValidators();
      validators = validatorLoader.getOwnedValidators();
    } catch (InvalidConfigurationException ex) {
      SUB_COMMAND_LOG.error(ex.getMessage());
      System.exit(1);
    }
    if (validators.hasNoValidators()) {
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
    } else if (maybeEpoch.isPresent()
        && spec.computeMinimumViableEpoch(spec.computeStartSlotAtEpoch(maybeEpoch.get()))
            .isGreaterThan(epoch)) {
      SUB_COMMAND_LOG.error(
          String.format(
              "The specified epoch %s (%s) is on a different milestone to the chains current epoch %s (%s), cannot continue.",
              epoch,
              spec.atEpoch(epoch).getMilestone(),
              maybeEpoch.get(),
              spec.atEpoch(maybeEpoch.get()).getMilestone()));
      System.exit(1);
    }
  }

  private TekuConfiguration tekuConfiguration() {
    final TekuConfiguration.Builder builder =
        TekuConfiguration.builder().metrics(b -> b.metricsEnabled(false));
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
            .getPrimaryBeaconNodeApiEndpoint()
            .orElse(URI.create("http://127.0.0.1:5051")));
  }
}
