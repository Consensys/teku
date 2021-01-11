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

package tech.pegasys.teku.networks;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import tech.pegasys.teku.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecConfiguration;
import tech.pegasys.teku.spec.constants.SpecConstants;

public class Eth2NetworkConfiguration {
  public static final String MAINNET = "mainnet";
  public static final String MINIMAL = "minimal";
  public static final String MEDALLA = "medalla";
  public static final String TOLEDO = "toledo";
  public static final String PYRMONT = "pyrmont";

  private static final int DEFAULT_STARTUP_TARGET_PEER_COUNT = 5;
  private static final int DEFAULT_STARTUP_TIMEOUT_SECONDS = 30;

  private final SpecConfiguration specConfig;
  private final String constants;
  private final Optional<String> initialState;
  private final int startupTargetPeerCount;
  private final int startupTimeoutSeconds;
  private final List<String> discoveryBootnodes;
  private final Optional<Eth1Address> eth1DepositContractAddress;
  private final Optional<UInt64> eth1DepositContractDeployBlock;

  private Eth2NetworkConfiguration(
      final SpecConfiguration specConfig,
      final String constants,
      final Optional<String> initialState,
      final int startupTargetPeerCount,
      final int startupTimeoutSeconds,
      final List<String> discoveryBootnodes,
      final Optional<Eth1Address> eth1DepositContractAddress,
      final Optional<UInt64> eth1DepositContractDeployBlock) {
    this.specConfig = specConfig;
    this.constants = constants;
    this.initialState = initialState;
    this.startupTargetPeerCount = startupTargetPeerCount;
    this.startupTimeoutSeconds = startupTimeoutSeconds;
    this.discoveryBootnodes = discoveryBootnodes;
    this.eth1DepositContractAddress = eth1DepositContractAddress;
    this.eth1DepositContractDeployBlock = eth1DepositContractDeployBlock;
  }

  public static Eth2NetworkConfiguration.Builder builder(final String network) {
    return builder().applyNetworkDefaults(network);
  }

  public static Eth2NetworkConfiguration.Builder builder() {
    return new Builder();
  }

  public SpecConfiguration getSpecConfig() {
    return specConfig;
  }

  /**
   * @deprecated Constants should be accessed via {@link Spec}
   * @return The constants resource name or url
   */
  @Deprecated
  public String getConstants() {
    return constants;
  }

  public Optional<String> getInitialState() {
    return initialState;
  }

  public Integer getStartupTargetPeerCount() {
    return startupTargetPeerCount;
  }

  public Integer getStartupTimeoutSeconds() {
    return startupTimeoutSeconds;
  }

  public List<String> getDiscoveryBootnodes() {
    return discoveryBootnodes;
  }

  public Optional<Eth1Address> getEth1DepositContractAddress() {
    return eth1DepositContractAddress;
  }

  public Optional<UInt64> getEth1DepositContractDeployBlock() {
    return eth1DepositContractDeployBlock;
  }

  @Override
  public String toString() {
    return constants;
  }

  public static class Builder {
    private String constants;
    private Optional<String> initialState = Optional.empty();
    private int startupTargetPeerCount = DEFAULT_STARTUP_TARGET_PEER_COUNT;
    private int startupTimeoutSeconds = DEFAULT_STARTUP_TIMEOUT_SECONDS;
    private List<String> discoveryBootnodes = new ArrayList<>();
    private Optional<Eth1Address> eth1DepositContractAddress = Optional.empty();
    private Optional<UInt64> eth1DepositContractDeployBlock = Optional.empty();

    public Eth2NetworkConfiguration build() {
      checkNotNull(constants, "Missing constants");

      final SpecConstants specConstants = ConstantsLoader.loadConstants(constants);
      final SpecConfiguration specConfig =
          SpecConfiguration.builder().constants(specConstants).build();

      return new Eth2NetworkConfiguration(
          specConfig,
          constants,
          initialState,
          startupTargetPeerCount,
          startupTimeoutSeconds,
          discoveryBootnodes,
          eth1DepositContractAddress,
          eth1DepositContractDeployBlock);
    }

    public Builder constants(final String constants) {
      this.constants = constants;
      return this;
    }

    public Builder initialState(final String initialState) {
      this.initialState = Optional.of(initialState);
      return this;
    }

    public Builder initialStateFromClasspath(final String filename) {
      this.initialState =
          Optional.of(Eth2NetworkConfiguration.class.getResource(filename).toExternalForm());
      return this;
    }

    public Builder startupTargetPeerCount(final int startupTargetPeerCount) {
      this.startupTargetPeerCount = startupTargetPeerCount;
      return this;
    }

    public Builder startupTimeoutSeconds(final int startupTimeoutSeconds) {
      this.startupTimeoutSeconds = startupTimeoutSeconds;
      return this;
    }

    public Builder discoveryBootnodes(final String... discoveryBootnodes) {
      this.discoveryBootnodes = asList(discoveryBootnodes);
      return this;
    }

    public Builder eth1DepositContractAddress(final String eth1Address) {
      this.eth1DepositContractAddress = Optional.of(Eth1Address.fromHexString(eth1Address));
      return this;
    }

    public Builder eth1DepositContractAddress(final Optional<Eth1Address> eth1Address) {
      checkNotNull(eth1Address);
      this.eth1DepositContractAddress = eth1Address;
      return this;
    }

    public Builder eth1DepositContractDeployBlock(final long eth1DepositContractDeployBlock) {
      this.eth1DepositContractDeployBlock =
          Optional.of(UInt64.valueOf(eth1DepositContractDeployBlock));
      return this;
    }

    public Builder applyNetworkDefaults(final String network) {
      final String normalizedNetwork = network.toLowerCase(Locale.US).strip();
      switch (normalizedNetwork) {
        case MAINNET:
          return applyMainnetNetworkDefaults();
        case MINIMAL:
          return applyMinimalNetworkDefaults();
        case MEDALLA:
          return applyMedallaNetworkDefaults();
        case TOLEDO:
          return applyToledoNetworkDefaults();
        case PYRMONT:
          return applyPyrmontNetworkDefaults();
        default:
          return reset().constants(normalizedNetwork);
      }
    }

    private Builder reset() {
      constants = null;
      initialState = Optional.empty();
      startupTargetPeerCount = DEFAULT_STARTUP_TARGET_PEER_COUNT;
      startupTimeoutSeconds = DEFAULT_STARTUP_TIMEOUT_SECONDS;
      discoveryBootnodes = new ArrayList<>();
      eth1DepositContractAddress = Optional.empty();
      eth1DepositContractDeployBlock = Optional.empty();

      return this;
    }

    public Builder applyMinimalNetworkDefaults() {
      return reset().constants(MINIMAL).startupTargetPeerCount(0);
    }

    public Builder applyMainnetNetworkDefaults() {
      return reset()
          .constants(MAINNET)
          .initialStateFromClasspath("mainnet-genesis.ssz")
          .startupTimeoutSeconds(120)
          .eth1DepositContractAddress("0x00000000219ab540356cBB839Cbe05303d7705Fa")
          .eth1DepositContractDeployBlock(11052984)
          .discoveryBootnodes(
              // PegaSys Teku
              "enr:-KG4QJRlj4pHagfNIm-Fsx9EVjW4rviuZYzle3tyddm2KAWMJBDGAhxfM2g-pDaaiwE8q19uvLSH4jyvWjypLMr3TIcEhGV0aDKQ9aX9QgAAAAD__________4JpZIJ2NIJpcIQDE8KdiXNlY3AyNTZrMaEDhpehBDbZjM_L9ek699Y7vhUJ-eAdMyQW_Fil522Y0fODdGNwgiMog3VkcIIjKA",
              "enr:-KG4QDyytgmE4f7AnvW-ZaUOIi9i79qX4JwjRAiXBZCU65wOfBu-3Nb5I7b_Rmg3KCOcZM_C3y5pg7EBU5XGrcLTduQEhGV0aDKQ9aX9QgAAAAD__________4JpZIJ2NIJpcIQ2_DUbiXNlY3AyNTZrMaEDKnz_-ps3UUOfHWVYaskI5kWYO_vtYMGYCQRAR3gHDouDdGNwgiMog3VkcIIjKA",
              // Prysmatic Labs
              "enr:-Ku4QImhMc1z8yCiNJ1TyUxdcfNucje3BGwEHzodEZUan8PherEo4sF7pPHPSIB1NNuSg5fZy7qFsjmUKs2ea1Whi0EBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD1pf1CAAAAAP__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQOVphkDqal4QzPMksc5wnpuC3gvSC8AfbFOnZY_On34wIN1ZHCCIyg",
              "enr:-Ku4QP2xDnEtUXIjzJ_DhlCRN9SN99RYQPJL92TMlSv7U5C1YnYLjwOQHgZIUXw6c-BvRg2Yc2QsZxxoS_pPRVe0yK8Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD1pf1CAAAAAP__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQMeFF5GrS7UZpAH2Ly84aLK-TyvH-dRo0JM1i8yygH50YN1ZHCCJxA",
              "enr:-Ku4QPp9z1W4tAO8Ber_NQierYaOStqhDqQdOPY3bB3jDgkjcbk6YrEnVYIiCBbTxuar3CzS528d2iE7TdJsrL-dEKoBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD1pf1CAAAAAP__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQMw5fqqkw2hHC4F5HZZDPsNmPdB1Gi8JPQK7pRc9XHh-oN1ZHCCKvg",
              // Sigp
              "enr:-IS4QLkKqDMy_ExrpOEWa59NiClemOnor-krjp4qoeZwIw2QduPC-q7Kz4u1IOWf3DDbdxqQIgC4fejavBOuUPy-HE4BgmlkgnY0gmlwhCLzAHqJc2VjcDI1NmsxoQLQSJfEAHZApkm5edTCZ_4qps_1k_ub2CxHFxi-gr2JMIN1ZHCCIyg",
              "enr:-IS4QDAyibHCzYZmIYZCjXwU9BqpotWmv2BsFlIq1V31BwDDMJPFEbox1ijT5c2Ou3kvieOKejxuaCqIcjxBjJ_3j_cBgmlkgnY0gmlwhAMaHiCJc2VjcDI1NmsxoQJIdpj_foZ02MXz4It8xKD7yUHTBx7lVFn3oeRP21KRV4N1ZHCCIyg",
              // EF
              "enr:-Ku4QHqVeJ8PPICcWk1vSn_XcSkjOkNiTg6Fmii5j6vUQgvzMc9L1goFnLKgXqBJspJjIsB91LTOleFmyWWrFVATGngBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpC1MD8qAAAAAP__________gmlkgnY0gmlwhAMRHkWJc2VjcDI1NmsxoQKLVXFOhp2uX6jeT0DvvDpPcU8FWMjQdR4wMuORMhpX24N1ZHCCIyg",
              "enr:-Ku4QG-2_Md3sZIAUebGYT6g0SMskIml77l6yR-M_JXc-UdNHCmHQeOiMLbylPejyJsdAPsTHJyjJB2sYGDLe0dn8uYBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpC1MD8qAAAAAP__________gmlkgnY0gmlwhBLY-NyJc2VjcDI1NmsxoQORcM6e19T1T9gi7jxEZjk_sjVLGFscUNqAY9obgZaxbIN1ZHCCIyg",
              "enr:-Ku4QPn5eVhcoF1opaFEvg1b6JNFD2rqVkHQ8HApOKK61OIcIXD127bKWgAtbwI7pnxx6cDyk_nI88TrZKQaGMZj0q0Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpC1MD8qAAAAAP__________gmlkgnY0gmlwhDayLMaJc2VjcDI1NmsxoQK2sBOLGcUb4AwuYzFuAVCaNHA-dy24UuEKkeFNgCVCsIN1ZHCCIyg",
              "enr:-Ku4QEWzdnVtXc2Q0ZVigfCGggOVB2Vc1ZCPEc6j21NIFLODSJbvNaef1g4PxhPwl_3kax86YPheFUSLXPRs98vvYsoBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpC1MD8qAAAAAP__________gmlkgnY0gmlwhDZBrP2Jc2VjcDI1NmsxoQM6jr8Rb1ktLEsVcKAPa08wCsKUmvoQ8khiOl_SLozf9IN1ZHCCIyg",

              // Nimbus
              "enr:-LK4QLU5_AeUzZEtpK8grqPo4EmX4el3ochu8vNNoXX1PrBjYfn8ksjeQ1eFtbL7ywMau9k_7BBQGmO26DHWgngkBCgBh2F0dG5ldHOI__________-EZXRoMpC1MD8qAAAAAP__________gmlkgnY0gmlwhAN7_O-Jc2VjcDI1NmsxoQKH1zg2Fge8Q6Zf-rLFbjGEtgvVbmDXqFVLxqquJcguFIN0Y3CCI4yDdWRwgiOM",
              "enr:-LK4QLjSKc09WkFZ5Pa1UF3KPkt3ieTZ6B7F6iDL_chyniP5NVDl10aGIu-pL9mbwZ47GM3RN63eGHPsw-MTLSYcz74Bh2F0dG5ldHOI__________-EZXRoMpC1MD8qAAAAAP__________gmlkgnY0gmlwhDQ7fI6Jc2VjcDI1NmsxoQJDU6zzDlUDgUqFSzoIuP9bWu097k2d7X4eHoJTGhbphoN0Y3CCI4yDdWRwgiOM");
    }

    public Builder applyMedallaNetworkDefaults() {
      return reset()
          .constants(MEDALLA)
          .initialStateFromClasspath("medalla-genesis.ssz")
          .startupTimeoutSeconds(120)
          .eth1DepositContractAddress("0x07b39F4fDE4A38bACe212b546dAc87C58DfE3fDC")
          .eth1DepositContractDeployBlock(3085928)
          .discoveryBootnodes(
              // PegaSys Teku
              "enr:-KG4QFuKQ9eeXDTf8J4tBxFvs3QeMrr72mvS7qJgL9ieO6k9Rq5QuGqtGK4VlXMNHfe34Khhw427r7peSoIbGcN91fUDhGV0aDKQD8XYjwAAAAH__________4JpZIJ2NIJpcIQDhMExiXNlY3AyNTZrMaEDESplmV9c2k73v0DjxVXJ6__2bWyP-tK28_80lf7dUhqDdGNwgiMog3VkcIIjKA",

              // Sigp Lighthouse
              "enr:-LK4QKWk9yZo258PQouLshTOEEGWVHH7GhKwpYmB5tmKE4eHeSfman0PZvM2Rpp54RWgoOagAsOfKoXgZSbiCYzERWABh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAAAAAAAAAAAAAAAAAAAAAAgmlkgnY0gmlwhDQlA5CJc2VjcDI1NmsxoQOYiWqrQtQksTEtS3qY6idxJE5wkm0t9wKqpzv2gCR21oN0Y3CCIyiDdWRwgiMo",
              "enr:-LK4QEnIS-PIxxLCadJdnp83VXuJqgKvC9ZTIWaJpWqdKlUFCiup2sHxWihF9EYGlMrQLs0mq_2IyarhNq38eoaOHUoBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAAAAAAAAAAAAAAAAAAAAAAgmlkgnY0gmlwhA37LMaJc2VjcDI1NmsxoQJ7k0mKtTd_kdEq251flOjD1HKpqgMmIETDoD-Msy_O-4N0Y3CCIyiDdWRwgiMo",

              // Prysmatic
              "enr:-Ku4QLglCMIYAgHd51uFUqejD9DWGovHOseHQy7Od1SeZnHnQ3fSpE4_nbfVs8lsy8uF07ae7IgrOOUFU0NFvZp5D4wBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAYrkzLAAAAAf__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQJxCnE6v_x2ekgY_uoE1rtwzvGy40mq9eD66XfHPBWgIIN1ZHCCD6A",
              "enr:-Ku4QOzU2MY51tYFcoByfULugCu2mepfqAbB0DajbRzg8xlILLfi5Iv_Wx-ARn8SiFoZZb3yp2x05cnUDYSoDYZupjIBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAYrkzLAAAAAf__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQLEq16KLm1vPjUKYGkHq296D60i7y209NYPUpwZPXDVgYN1ZHCCD6A",
              "enr:-Ku4QOYFmi2BW_YPDew_CKdfMvsrcRY1ARA-ImtcqFl-lgoxOFbxte4PU44-1M3uRNSRM-6rVa8USGohmWwtgwalEt8Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAYrkzLAAAAAf__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQKH3lxnglLqrA7L6sl5r7XFnckr3XCnlZMaBTYSdE8SHIN1ZHCCD6A",

              // Proto
              "enr:-Ku4QFVactU18ogiqPPasKs3jhUm5ISszUrUMK2c6SUPbGtANXVJ2wFapsKwVEVnVKxZ7Gsr9yEc4PYF-a14ahPa1q0Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAYrkzLAAAAAf__________gmlkgnY0gmlwhGQbAHyJc2VjcDI1NmsxoQILF-Ya2i5yowVkQtlnZLjG0kqC4qtwmSk8ha7tKLuME4N1ZHCCIyg",

              // Lighthouse v5.1
              "enr:-LK4QCGFeQXjpQkgOfLHsbTjD65IOtSqV7Qo-Qdqv6SrL8lqFY7INPMMGP5uGKkVDcJkeXimSeNeypaZV3MHkcJgr9QCh2F0dG5ldHOIAAAAAAAAAACEZXRoMpDnp11aAAAAAf__________gmlkgnY0gmlwhA37LMaJc2VjcDI1NmsxoQJ7k0mKtTd_kdEq251flOjD1HKpqgMmIETDoD-Msy_O-4N0Y3CCIyiDdWRwgiMo",
              "enr:-LK4QCpyWmMLYwC2umMJ_g0c9VY7YOFwZyaR80_tuQNTWOzJbaR82DDhVQYqmE_0gvN6Du5jwnxzIaaNRZQlVXzfIK0Dh2F0dG5ldHOIAAAAAAAAAACEZXRoMpDnp11aAAAAAf__________gmlkgnY0gmlwhCLR2xuJc2VjcDI1NmsxoQOYiWqrQtQksTEtS3qY6idxJE5wkm0t9wKqpzv2gCR21oN0Y3CCIyiDdWRwgiMo",

              // Prysm v5.1
              "enr:-Ku4QHWezvidY_m0dWEwERrNrqjEQWrlIx7b8K4EIxGgTrLmUxHCZPW5-t8PsS8nFxAJ8k8YacKP5zPRk5gbsTSsRTQBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAYrkzLAAAAAf__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQMypP_ODwTuBq2v0oIdjPGCEyu9Hb_jHDbuIX_iNvBRGoN1ZHCCGWQ",
              "enr:-Ku4QOnVSyvzS3VbF87J8MubaRuTyfPi6B67XQg6-5eAV_uILAhn9geTTQmfqDIOcIeAxWHUUajQp6lYniAXPWncp6UBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAYrkzLAAAAAf__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQKekYKqUtwbaJKKCct_srE5-g7tBUm68mj_jpeSb7CCqYN1ZHCCC7g");
    }

    public Builder applyToledoNetworkDefaults() {
      return reset()
          .constants(TOLEDO)
          .startupTimeoutSeconds(120)
          .eth1DepositContractAddress("0x47709dC7a8c18688a1f051761fc34ac253970bC0")
          .eth1DepositContractDeployBlock(3702432)
          .discoveryBootnodes(
              // discv5.1-only bootnode @protolambda
              "enr:-Ku4QL5E378NT4-vqP6v1mZ7kHxiTHJvuBvQixQsuTTCffa0PJNWMBlG3Mduvsvd6T2YP1U3l5tBKO5H-9wyX2SCtPkBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpC4EvfsAHAe0P__________gmlkgnY0gmlwhDaetEeJc2VjcDI1NmsxoQKtGC2CAuba7goLLdle899M3esUmoWRvzi7GBVhq6ViCYN1ZHCCIyg",

              // lighthouse (Canada) @protolambda
              "enr:-LK4QHLujdDjOwm2siyFJ2XGz19_ip-qTtozG3ceZ3_56G-LMWb4um67gTSYRJg0WsSkyvRMBEpz8uuIYl-7HfWvktgBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCXm69nAHAe0P__________gmlkgnY0gmlwhCO3C5OJc2VjcDI1NmsxoQKXw9BLDY6YwmqTtfkzUnlJQb82UrlX4lIAnSSYWHFRlYN0Y3CCIyiDdWRwgiMo",

              // lighthouse (Sao Paulo) @protolambda
              "enr:-LK4QMxmk7obupScBebKFaasSH3QmYUg-HaEmMAljfmGQCLbKwdOhszzx-VfVPvlH7bZZbOmg3-SNWbJsFfytdjD7a4Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCXm69nAHAe0P__________gmlkgnY0gmlwhBLkdWuJc2VjcDI1NmsxoQOwYsJyLOjJcDIqiQSSZtDi_EwwSaUjPBSnLVY_PYu-HoN0Y3CCIyiDdWRwgiMo",

              // Teku @protolambda
              "enr:-KG4QKqo0mG4C35ntJg8icO54wd973aZ7aBiAnC2t1XkGvgqNDOEHwNe2ykxYVUj9AWjm_lKD7brlhXKCZEskGbie2cDhGV0aDKQl5uvZwBwHtD__________4JpZIJ2NIJpcIQNOThwiXNlY3AyNTZrMaECn1dwC8MRt8rk2VUT8RjzEBaceF09d4CEQI20O_SWYcqDdGNwgiMog3VkcIIjKA",

              // Prysm @protolambda
              "enr:-LK4QAhU5smiLgU0AgrdFv8eCKmDPCBkXCMCIy8Aktaci5qvCYOsW98xVqJS6OoPWt4Sz_YoTdLQBWxd-RZ756vmGPMBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCXm69nAHAe0P__________gmlkgnY0gmlwhDTTDL2Jc2VjcDI1NmsxoQOmSJ0mKsQjab7Zralm1Hi0AEReZ2SEqYdKoOPmoA98DoN0Y3CCIyiDdWRwgiMo");
    }

    public Builder applyPyrmontNetworkDefaults() {
      return reset()
          .constants(PYRMONT)
          .startupTimeoutSeconds(120)
          .eth1DepositContractAddress("0x8c5fecdC472E27Bc447696F431E425D02dd46a8c")
          .eth1DepositContractDeployBlock(3743587)
          .initialStateFromClasspath("pyrmont-genesis.ssz")
          .discoveryBootnodes(
              // @protolambda bootnode 1
              "enr:-Ku4QOA5OGWObY8ep_x35NlGBEj7IuQULTjkgxC_0G1AszqGEA0Wn2RNlyLFx9zGTNB1gdFBA6ZDYxCgIza1uJUUOj4Dh2F0dG5ldHOIAAAAAAAAAACEZXRoMpDVTPWXAAAgCf__________gmlkgnY0gmlwhDQPSjiJc2VjcDI1NmsxoQM6yTQB6XGWYJbI7NZFBjp4Yb9AYKQPBhVrfUclQUobb4N1ZHCCIyg",
              // @protolambda bootnode 2
              "enr:-Ku4QOksdA2tabOGrfOOr6NynThMoio6Ggka2oDPqUuFeWCqcRM2alNb8778O_5bK95p3EFt0cngTUXm2H7o1jkSJ_8Dh2F0dG5ldHOIAAAAAAAAAACEZXRoMpDVTPWXAAAgCf__________gmlkgnY0gmlwhDaa13aJc2VjcDI1NmsxoQKdNQJvnohpf0VO0ZYCAJxGjT0uwJoAHbAiBMujGjK0SoN1ZHCCIyg");
    }
  }
}
