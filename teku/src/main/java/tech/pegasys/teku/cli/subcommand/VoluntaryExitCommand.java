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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
import tech.pegasys.teku.cli.options.InteropOptions;
import tech.pegasys.teku.cli.options.NetworkOptions;
import tech.pegasys.teku.cli.options.ValidatorClientDataOptions;
import tech.pegasys.teku.cli.options.ValidatorClientOptions;
import tech.pegasys.teku.cli.options.ValidatorOptions;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.core.signatures.SlashingProtector;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.GlobalConfigurationBuilder;
import tech.pegasys.teku.util.config.InvalidConfigurationException;
import tech.pegasys.teku.util.config.NetworkDefinition;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.ValidatorClientService;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.remote.apiclient.OkHttpValidatorRestApiClient;

@CommandLine.Command(
    name = "voluntary-exit",
    hidden = true,
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

  @CommandLine.Mixin(name = "Validator")
  private ValidatorOptions validatorOptions;

  @CommandLine.Mixin(name = "Network")
  private NetworkOptions networkOptions;

  @CommandLine.Mixin(name = "Interop")
  private InteropOptions interopOptions;

  @CommandLine.Mixin(name = "Data")
  private ValidatorClientDataOptions dataOptions;

  @CommandLine.Mixin(name = "Validator Client")
  private ValidatorClientOptions validatorClientOptions;

  @CommandLine.Option(
      names = {"--epoch"},
      paramLabel = "<EPOCH>",
      description = "Earliest epoch that the voluntary exit can be processed.",
      required = true,
      arity = "1")
  private UInt64 epoch;

  @Override
  public void run() {
    initialise();
    getValidatorIndices(blsPublicKeyValidatorMap).forEach(this::submitExitForValidator);
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
    }
  }

  private Optional<Bytes32> getGenesisRoot() {
    return apiClient.getGenesis().map(response -> response.getData().getGenesisValidatorsRoot());
  }

  private Optional<tech.pegasys.teku.datastructures.state.Fork> getFork() {
    return apiClient.getFork().map(Fork::asInternalFork);
  }

  private void initialise() {
    final TekuConfiguration config = tekuConfiguration();
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

    // get genesis time
    final Optional<Bytes32> maybeRoot = getGenesisRoot();
    if (maybeRoot.isEmpty()) {
      SUB_COMMAND_LOG.error("Unable to fetch genesis data, cannot generate an exit.");
      System.exit(1);
    }
    genesisRoot = maybeRoot.get();

    // get the validator
    final Path slashProtectionPath = getSlashingProtectionPath(dataOptions);
    final SlashingProtector slashingProtector =
        new SlashingProtector(new SyncDataAccessor(), slashProtectionPath);
    final ValidatorLoader validatorLoader = new ValidatorLoader(slashingProtector, asyncRunner);

    try {
      blsPublicKeyValidatorMap =
          validatorLoader.initializeValidators(
              config.validatorClient().getValidatorConfig(), config.global());
    } catch (InvalidConfigurationException ex) {
      SUB_COMMAND_LOG.error(ex.getMessage());
      System.exit(1);
    }
  }

  private TekuConfiguration tekuConfiguration() {
    final TekuConfiguration.Builder builder = TekuConfiguration.builder();
    builder.globalConfig(this::buildGlobalConfiguration);
    validatorOptions.configure(builder);

    validatorClientOptions.configure(builder);
    builder.validator(config -> config.validatorKeystoreLockingEnabled(false));
    dataOptions.configure(builder);
    return builder.build();
  }

  private void buildGlobalConfiguration(final GlobalConfigurationBuilder builder) {
    builder
        .setNetwork(NetworkDefinition.fromCliArg(networkOptions.getNetwork()))
        .setInteropGenesisTime(interopOptions.getInteropGenesisTime())
        .setInteropOwnedValidatorStartIndex(interopOptions.getInteropOwnerValidatorStartIndex())
        .setInteropOwnedValidatorCount(interopOptions.getInteropOwnerValidatorCount())
        .setInteropNumberOfValidators(interopOptions.getInteropNumberOfValidators())
        .setInteropEnabled(interopOptions.isInteropEnabled())
        .setMetricsEnabled(false);
  }

  private Path getSlashingProtectionPath(final ValidatorClientDataOptions dataOptions) {
    final DataDirLayout dataDirLayout = DataDirLayout.createFrom(dataOptions.getDataConfig());
    return ValidatorClientService.getSlashingProtectionPath(dataDirLayout);
  }
}
