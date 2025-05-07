/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import com.google.common.base.Preconditions;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class TestSpecFactory {

  public static Spec createDefault() {
    return createDefault(__ -> {});
  }

  public static Spec createDefault(final Consumer<SpecConfigBuilder> modifier) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName(), modifier);
    return create(config, SpecMilestone.PHASE0);
  }

  public static Spec createMinimal(final SpecMilestone specMilestone) {
    return switch (specMilestone) {
      case PHASE0 -> createMinimalPhase0();
      case ALTAIR -> createMinimalAltair();
      case BELLATRIX -> createMinimalBellatrix();
      case CAPELLA -> createMinimalCapella();
      case DENEB -> createMinimalDeneb();
      case ELECTRA -> createMinimalElectra();
      case FULU -> createMinimalFulu();
    };
  }

  public static Spec createMainnet(final SpecMilestone specMilestone) {
    return switch (specMilestone) {
      case PHASE0 -> createMainnetPhase0();
      case ALTAIR -> createMainnetAltair();
      case BELLATRIX -> createMainnetBellatrix();
      case CAPELLA -> createMainnetCapella();
      case DENEB -> createMainnetDeneb();
      case ELECTRA -> createMainnetElectra();
      case FULU -> createMainnetFulu();
    };
  }

  public static Spec createMinimalWithAltairAndBellatrixForkEpoch(
      final UInt64 altairEpoch, final UInt64 bellatrixForkEpoch) {
    Preconditions.checkArgument(
        altairEpoch.isLessThan(bellatrixForkEpoch),
        String.format(
            "Altair epoch %s must be less than bellatrix epoch %s",
            altairEpoch, bellatrixForkEpoch));
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getBellatrixSpecConfig(Eth2Network.MINIMAL, altairEpoch, bellatrixForkEpoch);
    return create(specConfig, SpecMilestone.BELLATRIX);
  }

  public static Spec createMinimalBellatrix() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getBellatrixSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.BELLATRIX);
  }

  public static Spec createMinimalBellatrix(final Consumer<SpecConfigBuilder> configAdapter) {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getBellatrixSpecConfig(Eth2Network.MINIMAL, configAdapter);
    return create(specConfig, SpecMilestone.BELLATRIX);
  }

  public static Spec createMinimalAltair() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getAltairSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.ALTAIR);
  }

  public static Spec createMinimalCapella() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getCapellaSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.CAPELLA);
  }

  public static Spec createMinimalDeneb() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getDenebSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.DENEB);
  }

  public static Spec createMinimalDeneb(final Consumer<SpecConfigBuilder> configAdapter) {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getDenebSpecConfig(Eth2Network.MINIMAL, configAdapter);
    return create(specConfig, SpecMilestone.DENEB);
  }

  public static Spec createMinimalElectra() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getElectraSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.ELECTRA);
  }

  public static Spec createMinimalElectra(final Consumer<SpecConfigBuilder> configAdapter) {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getElectraSpecConfig(Eth2Network.MINIMAL, configAdapter);
    return create(specConfig, SpecMilestone.ELECTRA);
  }

  public static Spec createMinimalFulu() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getFuluSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.FULU);
  }

  public static Spec createMinimalFulu(final Consumer<SpecConfigBuilder> configAdapter) {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getFuluSpecConfig(Eth2Network.MINIMAL, configAdapter);
    return create(specConfig, SpecMilestone.FULU);
  }

  /**
   * Create a spec that forks to altair at the provided slot
   *
   * @param altairForkEpoch The altair fork epoch
   * @return A spec with phase0 and altair enabled, forking to altair at the given epoch
   */
  public static Spec createMinimalWithAltairForkEpoch(final UInt64 altairForkEpoch) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getAltairSpecConfig(Eth2Network.MINIMAL, altairForkEpoch);
    return create(config, SpecMilestone.ALTAIR);
  }

  /**
   * Create a spec that forks to bellatrix at the provided slot (altair genesis)
   *
   * @param bellatrixForkEpoch The bellatrix fork epoch
   * @return A spec with altair and bellatrix enabled, forking to bellatrix at the given epoch
   */
  public static Spec createMinimalWithBellatrixForkEpoch(final UInt64 bellatrixForkEpoch) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getBellatrixSpecConfig(Eth2Network.MINIMAL, UInt64.ZERO, bellatrixForkEpoch);
    return create(config, SpecMilestone.BELLATRIX);
  }

  /**
   * Create a spec that forks to Capella at the provided slot (bellatrix genesis)
   *
   * @param capellaForkEpoch The Capella fork epoch
   * @return A spec with Altair, Bellatrix and Capella enabled, forking to Capella at the given
   *     epoch
   */
  public static Spec createMinimalWithCapellaForkEpoch(final UInt64 capellaForkEpoch) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getCapellaSpecConfig(Eth2Network.MINIMAL, capellaForkEpoch);
    return create(config, SpecMilestone.CAPELLA);
  }

  /**
   * Create a spec that forks to Deneb at the provided epoch
   *
   * @param denebForkEpoch The Deneb fork epoch
   * @return A spec with Deneb enabled, forking to Deneb at the given epoch
   */
  public static Spec createMinimalWithDenebForkEpoch(final UInt64 denebForkEpoch) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getDenebSpecConfig(Eth2Network.MINIMAL, UInt64.ZERO, denebForkEpoch);
    return create(config, SpecMilestone.DENEB);
  }

  /**
   * Create a spec that forks to Electra at the provided epoch
   *
   * @param electraForkEpoch The Electra fork epoch
   * @return A spec with Electra enabled, forking to Electra at the given epoch
   */
  public static Spec createMinimalWithElectraForkEpoch(final UInt64 electraForkEpoch) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getElectraSpecConfig(Eth2Network.MINIMAL, UInt64.ZERO, UInt64.ZERO, electraForkEpoch);
    return create(config, SpecMilestone.ELECTRA);
  }

  public static Spec createMinimalWithFuluForkEpoch(final UInt64 fuluForkEpoch) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getFuluSpecConfig(Eth2Network.MINIMAL, ZERO, ZERO, ZERO, fuluForkEpoch);
    return create(config, SpecMilestone.FULU);
  }

  public static Spec createMinimalPhase0() {
    final SpecConfigAndParent<? extends SpecConfig> configAndParent =
        SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName());
    return create(configAndParent, SpecMilestone.PHASE0);
  }

  public static Spec createMainnetPhase0() {
    final SpecConfigAndParent<? extends SpecConfig> configAndParent =
        SpecConfigLoader.loadConfig(Eth2Network.MAINNET.configName());
    return create(configAndParent, SpecMilestone.PHASE0);
  }

  public static Spec createMainnetBellatrix() {
    final SpecConfigAndParent<? extends SpecConfig> configAndParent =
        getBellatrixSpecConfig(Eth2Network.MAINNET);
    return create(configAndParent, SpecMilestone.BELLATRIX);
  }

  public static Spec createMainnetAltair() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getAltairSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.ALTAIR);
  }

  public static Spec createMainnetCapella() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getCapellaSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.CAPELLA);
  }

  public static Spec createMainnetDeneb() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getDenebSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.DENEB);
  }

  public static Spec createMainnetElectra() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getElectraSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.ELECTRA);
  }

  public static Spec createMainnetFulu() {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        getFuluSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.FULU);
  }

  public static Spec createPhase0(final SpecConfigAndParent<? extends SpecConfig> config) {
    return create(config, SpecMilestone.PHASE0);
  }

  public static Spec createAltair(final SpecConfigAndParent<? extends SpecConfig> config) {
    return create(config, SpecMilestone.ALTAIR);
  }

  public static Spec createBellatrix(final SpecConfigAndParent<? extends SpecConfig> config) {
    return create(config, SpecMilestone.BELLATRIX);
  }

  public static Spec create(final SpecMilestone specMilestone, final Eth2Network network) {
    return create(specMilestone, network, builder -> {});
  }

  public static Spec create(
      final SpecMilestone specMilestone,
      final Eth2Network network,
      final Consumer<SpecConfigBuilder> configModifier) {
    final Consumer<SpecConfigBuilder> defaultModifier =
        switch (specMilestone) {
          case PHASE0 -> __ -> {};
          case ALTAIR -> builder -> builder.altairBuilder(a -> a.altairForkEpoch(ZERO));
          case BELLATRIX ->
              builder ->
                  builder
                      .altairBuilder(a -> a.altairForkEpoch(ZERO))
                      .bellatrixBuilder(b -> b.bellatrixForkEpoch(ZERO));
          case CAPELLA ->
              builder ->
                  builder
                      .altairBuilder(a -> a.altairForkEpoch(ZERO))
                      .bellatrixBuilder(b -> b.bellatrixForkEpoch(ZERO))
                      .capellaBuilder(c -> c.capellaForkEpoch(ZERO));
          case DENEB ->
              builder ->
                  builder
                      .altairBuilder(a -> a.altairForkEpoch(ZERO))
                      .bellatrixBuilder(b -> b.bellatrixForkEpoch(ZERO))
                      .capellaBuilder(c -> c.capellaForkEpoch(ZERO))
                      .denebBuilder(d -> d.denebForkEpoch(ZERO));
          case ELECTRA ->
              builder ->
                  builder
                      .altairBuilder(a -> a.altairForkEpoch(ZERO))
                      .bellatrixBuilder(b -> b.bellatrixForkEpoch(ZERO))
                      .capellaBuilder(c -> c.capellaForkEpoch(ZERO))
                      .denebBuilder(d -> d.denebForkEpoch(ZERO))
                      .electraBuilder(e -> e.electraForkEpoch(ZERO));
          case FULU ->
              builder ->
                  builder
                      .altairBuilder(a -> a.altairForkEpoch(ZERO))
                      .bellatrixBuilder(b -> b.bellatrixForkEpoch(ZERO))
                      .capellaBuilder(c -> c.capellaForkEpoch(ZERO))
                      .denebBuilder(d -> d.denebForkEpoch(ZERO))
                      .electraBuilder(e -> e.electraForkEpoch(ZERO))
                      .fuluBuilder(f -> f.fuluForkEpoch(ZERO));
        };
    return create(
        SpecConfigLoader.loadConfig(network.configName(), defaultModifier.andThen(configModifier)),
        specMilestone);
  }

  public static Spec create(
      final SpecConfigAndParent<? extends SpecConfig> config,
      final SpecMilestone highestSupportedMilestone) {
    return Spec.create(config, highestSupportedMilestone);
  }

  private static SpecConfigAndParent<? extends SpecConfig> getAltairSpecConfig(
      final Eth2Network network) {
    return getAltairSpecConfig(network, UInt64.ZERO);
  }

  private static SpecConfigAndParent<? extends SpecConfig> getAltairSpecConfig(
      final Eth2Network network, final UInt64 altairForkEpoch) {
    return requireAltair(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> builder.altairBuilder(a -> a.altairForkEpoch(altairForkEpoch))));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getBellatrixSpecConfig(
      final Eth2Network network) {
    return getBellatrixSpecConfig(network, UInt64.ZERO, UInt64.ZERO);
  }

  private static SpecConfigAndParent<? extends SpecConfig> getBellatrixSpecConfig(
      final Eth2Network network, final UInt64 altairForkEpoch, final UInt64 bellatrixForkEpoch) {
    return getBellatrixSpecConfig(
        network,
        builder ->
            builder
                .altairBuilder(a -> a.altairForkEpoch(altairForkEpoch))
                .bellatrixBuilder(b -> b.bellatrixForkEpoch(bellatrixForkEpoch)));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getBellatrixSpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return requireBellatrix(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder
                  .altairBuilder(a -> a.altairForkEpoch(ZERO))
                  .bellatrixBuilder(b -> b.bellatrixForkEpoch(ZERO));
              configAdapter.accept(builder);
            }));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getCapellaSpecConfig(
      final Eth2Network network) {
    return getCapellaSpecConfig(network, UInt64.ZERO);
  }

  private static SpecConfigAndParent<? extends SpecConfig> getCapellaSpecConfig(
      final Eth2Network network, final UInt64 capellaForkEpoch) {
    return getCapellaSpecConfig(
        network,
        builder ->
            builder
                .altairBuilder(a -> a.altairForkEpoch(ZERO))
                .bellatrixBuilder(b -> b.bellatrixForkEpoch(ZERO))
                .capellaBuilder(c -> c.capellaForkEpoch(capellaForkEpoch)));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getCapellaSpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return requireCapella(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder
                  .altairBuilder(a -> a.altairForkEpoch(ZERO))
                  .bellatrixBuilder(b -> b.bellatrixForkEpoch(ZERO))
                  .capellaBuilder(c -> c.capellaForkEpoch(ZERO));
              configAdapter.accept(builder);
            }));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getDenebSpecConfig(
      final Eth2Network network) {
    return getDenebSpecConfig(network, ZERO, ZERO);
  }

  private static SpecConfigAndParent<? extends SpecConfig> getDenebSpecConfig(
      final Eth2Network network, final UInt64 capellaForkEpoch, final UInt64 denebForkEpoch) {
    return getDenebSpecConfig(
        network,
        builder ->
            builder
                .altairBuilder(a -> a.altairForkEpoch(ZERO))
                .bellatrixBuilder(b -> b.bellatrixForkEpoch(ZERO))
                .capellaBuilder(c -> c.capellaForkEpoch(capellaForkEpoch))
                .denebBuilder(d -> d.denebForkEpoch(denebForkEpoch)));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getDenebSpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return requireDeneb(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder
                  .altairBuilder(a -> a.altairForkEpoch(ZERO))
                  .bellatrixBuilder(b -> b.bellatrixForkEpoch(ZERO))
                  .capellaBuilder(c -> c.capellaForkEpoch(ZERO))
                  .denebBuilder(d -> d.denebForkEpoch(ZERO));
              configAdapter.accept(builder);
            }));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getElectraSpecConfig(
      final Eth2Network network) {
    return getElectraSpecConfig(network, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO);
  }

  private static SpecConfigAndParent<? extends SpecConfig> getElectraSpecConfig(
      final Eth2Network network,
      final UInt64 capellaForkEpoch,
      final UInt64 denebForkEpoch,
      final UInt64 electraForkEpoch) {
    return getElectraSpecConfig(
        network,
        builder ->
            builder
                .altairBuilder(a -> a.altairForkEpoch(ZERO))
                .bellatrixBuilder(b -> b.bellatrixForkEpoch(ZERO))
                .capellaBuilder(c -> c.capellaForkEpoch(capellaForkEpoch))
                .denebBuilder(d -> d.denebForkEpoch(denebForkEpoch))
                .electraBuilder(e -> e.electraForkEpoch(electraForkEpoch)));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getElectraSpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return requireElectra(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder
                  .altairBuilder(a -> a.altairForkEpoch(ZERO))
                  .bellatrixBuilder(b -> b.bellatrixForkEpoch(ZERO))
                  .capellaBuilder(c -> c.capellaForkEpoch(ZERO))
                  .denebBuilder(d -> d.denebForkEpoch(ZERO))
                  .electraBuilder(e -> e.electraForkEpoch(ZERO));
              configAdapter.accept(builder);
            }));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getFuluSpecConfig(
      final Eth2Network network) {
    return getFuluSpecConfig(network, ZERO, ZERO, ZERO, ZERO);
  }

  private static SpecConfigAndParent<? extends SpecConfig> getFuluSpecConfig(
      final Eth2Network network,
      final UInt64 capellaForkEpoch,
      final UInt64 denebForkEpoch,
      final UInt64 electraForkEpoch,
      final UInt64 fuluForkEpoch) {
    return getFuluSpecConfig(
        network,
        builder ->
            builder
                .altairBuilder(a -> a.altairForkEpoch(ZERO))
                .bellatrixBuilder(b -> b.bellatrixForkEpoch(ZERO))
                .capellaBuilder(c -> c.capellaForkEpoch(capellaForkEpoch))
                .denebBuilder(d -> d.denebForkEpoch(denebForkEpoch))
                .electraBuilder(e -> e.electraForkEpoch(electraForkEpoch))
                .fuluBuilder(f -> f.fuluForkEpoch(fuluForkEpoch)));
  }

  private static SpecConfigAndParent<? extends SpecConfig> getFuluSpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return requireFulu(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder
                  .altairBuilder(a -> a.altairForkEpoch(ZERO))
                  .bellatrixBuilder(b -> b.bellatrixForkEpoch(ZERO))
                  .capellaBuilder(c -> c.capellaForkEpoch(ZERO))
                  .denebBuilder(d -> d.denebForkEpoch(ZERO))
                  .electraBuilder(e -> e.electraForkEpoch(ZERO))
                  .fuluBuilder(f -> f.fuluForkEpoch(ZERO));
              configAdapter.accept(builder);
            }));
  }

  public static Spec createMinimalWithCapellaDenebElectraAndFuluForkEpoch(
      final UInt64 capellaForkEpoch,
      final UInt64 denebForkEpoch,
      final UInt64 electraForkEpoch,
      final UInt64 fuluForkEpoch) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        getFuluSpecConfig(
            Eth2Network.MINIMAL, capellaForkEpoch, denebForkEpoch, electraForkEpoch, fuluForkEpoch);
    return create(config, SpecMilestone.FULU);
  }

  // Our current config files contain ELECTRA params.
  // So all specConfigs created from them will be ELECTRA.
  // Here we just want to make sure that a given config supports the given milestone
  // (which useless in theory because they are all ELECTRA)

  private static SpecConfigAndParent<? extends SpecConfig> requireAltair(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    checkArgument(specConfigAndParent.specConfig().toVersionAltair().isPresent());
    return specConfigAndParent;
  }

  private static SpecConfigAndParent<? extends SpecConfig> requireBellatrix(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    checkArgument(specConfigAndParent.specConfig().toVersionBellatrix().isPresent());
    return specConfigAndParent;
  }

  private static SpecConfigAndParent<? extends SpecConfig> requireCapella(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    checkArgument(specConfigAndParent.specConfig().toVersionCapella().isPresent());
    return specConfigAndParent;
  }

  private static SpecConfigAndParent<? extends SpecConfig> requireDeneb(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    checkArgument(specConfigAndParent.specConfig().toVersionDeneb().isPresent());
    return specConfigAndParent;
  }

  private static SpecConfigAndParent<? extends SpecConfig> requireElectra(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    checkArgument(specConfigAndParent.specConfig().toVersionElectra().isPresent());
    return specConfigAndParent;
  }

  private static SpecConfigAndParent<? extends SpecConfig> requireFulu(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    checkArgument(specConfigAndParent.specConfig().toVersionFulu().isPresent());
    return specConfigAndParent;
  }
}
