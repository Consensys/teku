/*
 * Copyright Consensys Software Inc., 2022
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

import com.google.common.base.Preconditions;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class TestSpecFactory {

  public static Spec createDefault() {
    return createDefault(__ -> {});
  }

  public static Spec createDefault(final Consumer<SpecConfigBuilder> modifier) {
    final SpecConfig config =
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
      case EIP7594 -> createMinimalEip7594();
    };
  }

  public static Spec createMainnet(final SpecMilestone specMilestone) {
    return switch (specMilestone) {
      case PHASE0 -> createMainnetPhase0();
      case ALTAIR -> createMainnetAltair();
      case BELLATRIX -> createMainnetBellatrix();
      case CAPELLA -> createMainnetCapella();
      case DENEB -> createMainnetDeneb();
      case EIP7594 -> createMainnetEip7594();
    };
  }

  public static Spec createMinimalWithAltairAndBellatrixForkEpoch(
      final UInt64 altairEpoch, final UInt64 bellatrixForkEpoch) {
    Preconditions.checkArgument(
        altairEpoch.isLessThan(bellatrixForkEpoch),
        String.format(
            "Altair epoch %s must be less than bellatrix epoch %s",
            altairEpoch, bellatrixForkEpoch));
    final SpecConfigBellatrix config =
        getBellatrixSpecConfig(Eth2Network.MINIMAL, altairEpoch, bellatrixForkEpoch);
    return create(config, SpecMilestone.BELLATRIX);
  }

  public static Spec createMinimalBellatrix() {
    final SpecConfigBellatrix specConfig = getBellatrixSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.BELLATRIX);
  }

  public static Spec createMinimalBellatrix(final Consumer<SpecConfigBuilder> configAdapter) {
    final SpecConfigBellatrix specConfig =
        getBellatrixSpecConfig(Eth2Network.MINIMAL, configAdapter);
    return create(specConfig, SpecMilestone.BELLATRIX);
  }

  public static Spec createMinimalAltair() {
    final SpecConfigAltair specConfig = getAltairSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.ALTAIR);
  }

  public static Spec createMinimalCapella() {
    final SpecConfigCapella specConfig = getCapellaSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.CAPELLA);
  }

  public static Spec createMinimalDeneb() {
    final SpecConfigDeneb specConfig = getDenebSpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.DENEB);
  }

  public static Spec createMinimalDeneb(final Consumer<SpecConfigBuilder> configAdapter) {
    final SpecConfigDeneb specConfig = getDenebSpecConfig(Eth2Network.MINIMAL, configAdapter);
    return create(specConfig, SpecMilestone.DENEB);
  }

  public static Spec createMinimalEip7594() {
    final SpecConfigEip7594 specConfig = getEip7594SpecConfig(Eth2Network.MINIMAL);
    return create(specConfig, SpecMilestone.EIP7594);
  }

  public static Spec createMinimalEip7594(final Consumer<SpecConfigBuilder> configAdapter) {
    final SpecConfigEip7594 specConfig = getEip7594SpecConfig(Eth2Network.MINIMAL, configAdapter);
    return create(specConfig, SpecMilestone.EIP7594);
  }

  /**
   * Create a spec that forks to altair at the provided slot
   *
   * @param altairForkEpoch The altair fork epoch
   * @return A spec with phase0 and altair enabled, forking to altair at the given epoch
   */
  public static Spec createMinimalWithAltairForkEpoch(final UInt64 altairForkEpoch) {
    final SpecConfigAltair config = getAltairSpecConfig(Eth2Network.MINIMAL, altairForkEpoch);
    return create(config, SpecMilestone.ALTAIR);
  }

  /**
   * Create a spec that forks to bellatrix at the provided slot (altair genesis)
   *
   * @param bellatrixForkEpoch The bellatrix fork epoch
   * @return A spec with altair and bellatrix enabled, forking to bellatrix at the given epoch
   */
  public static Spec createMinimalWithBellatrixForkEpoch(final UInt64 bellatrixForkEpoch) {
    final SpecConfigBellatrix config =
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
    final SpecConfigCapella config = getCapellaSpecConfig(Eth2Network.MINIMAL, capellaForkEpoch);
    return create(config, SpecMilestone.CAPELLA);
  }

  /**
   * Create a spec that forks to Deneb at the provided epoch
   *
   * @param denebForkEpoch The Deneb fork epoch
   * @return A spec with Deneb enabled, forking to Deneb at the given epoch
   */
  public static Spec createMinimalWithDenebForkEpoch(final UInt64 denebForkEpoch) {
    final SpecConfigDeneb config =
        getDenebSpecConfig(Eth2Network.MINIMAL, UInt64.ZERO, denebForkEpoch);
    return create(config, SpecMilestone.DENEB);
  }

  /**
   * Create a spec that forks to EIP7594 at the provided epoch
   *
   * @param eip7594ForkEpoch The EIP7594 fork epoch
   * @return A spec with EIP7594 enabled, forking to EIP7594 at the given epoch
   */
  public static Spec createMinimalWithEip7594ForkEpoch(final UInt64 eip7594ForkEpoch) {
    final SpecConfigEip7594 config =
        getEip7594SpecConfig(Eth2Network.MINIMAL, UInt64.ZERO, UInt64.ZERO, eip7594ForkEpoch);
    return create(config, SpecMilestone.EIP7594);
  }

  public static Spec createMinimalPhase0() {
    final SpecConfig specConfig = SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName());
    return create(specConfig, SpecMilestone.PHASE0);
  }

  public static Spec createMainnetPhase0() {
    final SpecConfig specConfig = SpecConfigLoader.loadConfig(Eth2Network.MAINNET.configName());
    return create(specConfig, SpecMilestone.PHASE0);
  }

  public static Spec createMainnetBellatrix() {
    final SpecConfigBellatrix specConfig = getBellatrixSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.BELLATRIX);
  }

  public static Spec createMainnetAltair() {
    final SpecConfigAltair specConfig = getAltairSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.ALTAIR);
  }

  public static Spec createMainnetCapella() {
    final SpecConfigCapella specConfig = getCapellaSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.CAPELLA);
  }

  public static Spec createMainnetDeneb() {
    final SpecConfigDeneb specConfig = getDenebSpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.DENEB);
  }

  public static Spec createMainnetEip7594() {
    final SpecConfigEip7594 specConfig = getEip7594SpecConfig(Eth2Network.MAINNET);
    return create(specConfig, SpecMilestone.EIP7594);
  }

  public static Spec createPhase0(final SpecConfig config) {
    return create(config, SpecMilestone.PHASE0);
  }

  public static Spec createAltair(final SpecConfig config) {
    return create(config, SpecMilestone.ALTAIR);
  }

  public static Spec createBellatrix(final SpecConfig config) {
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
          case ALTAIR -> builder -> builder.altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO));
          case BELLATRIX -> builder ->
              builder
                  .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                  .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO));
          case CAPELLA -> builder ->
              builder
                  .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                  .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO))
                  .capellaBuilder(c -> c.capellaForkEpoch(UInt64.ZERO));
          case DENEB -> builder ->
              builder
                  .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                  .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO))
                  .capellaBuilder(c -> c.capellaForkEpoch(UInt64.ZERO))
                  .denebBuilder(d -> d.denebForkEpoch(UInt64.ZERO));
          case EIP7594 -> builder ->
              builder
                  .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                  .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO))
                  .capellaBuilder(c -> c.capellaForkEpoch(UInt64.ZERO))
                  .denebBuilder(d -> d.denebForkEpoch(UInt64.ZERO))
                  .eip7594Builder(e -> e.eip7594ForkEpoch(UInt64.ZERO));
        };
    return create(
        SpecConfigLoader.loadConfig(network.configName(), defaultModifier.andThen(configModifier)),
        specMilestone);
  }

  public static Spec create(
      final SpecConfig config, final SpecMilestone highestSupportedMilestone) {
    return Spec.create(config, highestSupportedMilestone);
  }

  private static SpecConfigAltair getAltairSpecConfig(final Eth2Network network) {
    return getAltairSpecConfig(network, UInt64.ZERO);
  }

  private static SpecConfigAltair getAltairSpecConfig(
      final Eth2Network network, final UInt64 altairForkEpoch) {
    return SpecConfigAltair.required(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> builder.altairBuilder(a -> a.altairForkEpoch(altairForkEpoch))));
  }

  private static SpecConfigBellatrix getBellatrixSpecConfig(final Eth2Network network) {
    return getBellatrixSpecConfig(network, UInt64.ZERO, UInt64.ZERO);
  }

  private static SpecConfigBellatrix getBellatrixSpecConfig(
      final Eth2Network network, final UInt64 altairForkEpoch, UInt64 bellatrixForkEpoch) {
    return getBellatrixSpecConfig(
        network,
        builder ->
            builder
                .altairBuilder(a -> a.altairForkEpoch(altairForkEpoch))
                .bellatrixBuilder(b -> b.bellatrixForkEpoch(bellatrixForkEpoch)));
  }

  private static SpecConfigBellatrix getBellatrixSpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return SpecConfigBellatrix.required(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder
                  .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                  .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO));
              configAdapter.accept(builder);
            }));
  }

  private static SpecConfigCapella getCapellaSpecConfig(final Eth2Network network) {
    return getCapellaSpecConfig(network, UInt64.ZERO);
  }

  private static SpecConfigCapella getCapellaSpecConfig(
      final Eth2Network network, final UInt64 capellaForkEpoch) {
    return getCapellaSpecConfig(
        network,
        builder ->
            builder
                .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO))
                .capellaBuilder(c -> c.capellaForkEpoch(capellaForkEpoch)));
  }

  private static SpecConfigCapella getCapellaSpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return SpecConfigCapella.required(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder
                  .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                  .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO))
                  .capellaBuilder(c -> c.capellaForkEpoch(UInt64.ZERO));
              configAdapter.accept(builder);
            }));
  }

  private static SpecConfigDeneb getDenebSpecConfig(final Eth2Network network) {
    return getDenebSpecConfig(network, UInt64.ZERO, UInt64.ZERO);
  }

  private static SpecConfigDeneb getDenebSpecConfig(
      final Eth2Network network, final UInt64 capellaForkEpoch, final UInt64 denebForkEpoch) {
    return getDenebSpecConfig(
        network,
        builder ->
            builder
                .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO))
                .capellaBuilder(c -> c.capellaForkEpoch(capellaForkEpoch))
                .denebBuilder(d -> d.denebForkEpoch(denebForkEpoch)));
  }

  private static SpecConfigDeneb getDenebSpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return SpecConfigDeneb.required(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder
                  .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                  .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO))
                  .capellaBuilder(c -> c.capellaForkEpoch(UInt64.ZERO))
                  .denebBuilder(d -> d.denebForkEpoch(UInt64.ZERO));
              configAdapter.accept(builder);
            }));
  }

  private static SpecConfigEip7594 getEip7594SpecConfig(final Eth2Network network) {
    return getEip7594SpecConfig(network, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO);
  }

  private static SpecConfigEip7594 getEip7594SpecConfig(
      final Eth2Network network,
      final UInt64 capellaForkEpoch,
      final UInt64 denebForkEpoch,
      final UInt64 eip7594ForkEpoch) {
    return getEip7594SpecConfig(
        network,
        builder ->
            builder
                .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO))
                .capellaBuilder(c -> c.capellaForkEpoch(capellaForkEpoch))
                .denebBuilder(d -> d.denebForkEpoch(denebForkEpoch))
                .eip7594Builder(e -> e.eip7594ForkEpoch(eip7594ForkEpoch)));
  }

  private static SpecConfigEip7594 getEip7594SpecConfig(
      final Eth2Network network, final Consumer<SpecConfigBuilder> configAdapter) {
    return SpecConfigEip7594.required(
        SpecConfigLoader.loadConfig(
            network.configName(),
            builder -> {
              builder
                  .altairBuilder(a -> a.altairForkEpoch(UInt64.ZERO))
                  .bellatrixBuilder(b -> b.bellatrixForkEpoch(UInt64.ZERO))
                  .capellaBuilder(c -> c.capellaForkEpoch(UInt64.ZERO))
                  .denebBuilder(d -> d.denebForkEpoch(UInt64.ZERO))
                  .eip7594Builder(e -> e.eip7594ForkEpoch(UInt64.ZERO));
              configAdapter.accept(builder);
            }));
  }

  public static Spec createMinimalWithCapellaDenebAndEip7594ForkEpoch(
      final UInt64 capellaForkEpoch, final UInt64 denebForkEpoch, final UInt64 eip7594ForkEpoch) {
    final SpecConfigBellatrix config =
        getEip7594SpecConfig(
            Eth2Network.MINIMAL, capellaForkEpoch, denebForkEpoch, eip7594ForkEpoch);
    return create(config, SpecMilestone.EIP7594);
  }
}
