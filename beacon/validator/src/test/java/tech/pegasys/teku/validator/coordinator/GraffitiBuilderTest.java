/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.validator.api.ClientGraffitiAppendFormat.AUTO;
import static tech.pegasys.teku.validator.api.ClientGraffitiAppendFormat.NAME;
import static tech.pegasys.teku.validator.api.ClientGraffitiAppendFormat.NONE;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;
import tech.pegasys.teku.validator.api.Bytes32Parser;
import tech.pegasys.teku.validator.api.ClientGraffitiAppendFormat;

public class GraffitiBuilderTest {
  private ClientGraffitiAppendFormat clientGraffitiAppendFormat = AUTO;
  private Optional<Bytes32> userGraffiti = Optional.empty();
  private GraffitiBuilder graffitiBuilder =
      new GraffitiBuilder(clientGraffitiAppendFormat, userGraffiti);

  private static final ClientVersion TEKU_CLIENT_VERSION =
      new GraffitiBuilder(NONE, Optional.empty()).getConsensusClientVersion();
  private static final ClientVersion BESU_CLIENT_VERSION =
      new ClientVersion("BU", "Besu", "23.4.1", Bytes4.fromHexString("abcdef12"));

  private final String asciiGraffiti0 = "";
  private static final String ASCII_GRAFFITI_20 = "I've proposed ablock";
  private final String asciiGraffiti32 = "I've proposed a good Teku block!";

  private static final String UTF_8_GRAFFITI_4 = "\uD83D\uDE80";
  private final String utf8Graffiti20 = "\uD83D\uDE80 !My block! \uD83D\uDE80";

  @BeforeEach
  public void setup() {
    this.clientGraffitiAppendFormat = AUTO;
    this.userGraffiti = Optional.empty();
    this.graffitiBuilder = new GraffitiBuilder(clientGraffitiAppendFormat, userGraffiti);
  }

  @Test
  public void onExecutionClientVersion_shouldLogDefaultGraffiti() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(EventLogger.class)) {
      graffitiBuilder.onExecutionClientVersion(BESU_CLIENT_VERSION);
      logCaptor.assertInfoLog(
          "Default graffiti to use when building block without external VC: \"TK"
              + TEKU_CLIENT_VERSION.commit().toUnprefixedHexString()
              + "BUabcdef12\". "
              + "To change check validator graffiti options.");
    }
  }

  @Test
  public void onExecutionClientVersion_shouldLogDefaultMergedGraffiti() {
    this.graffitiBuilder =
        new GraffitiBuilder(
            clientGraffitiAppendFormat, Optional.of(Bytes32Parser.toBytes32(ASCII_GRAFFITI_20)));
    try (final LogCaptor logCaptor = LogCaptor.forClass(EventLogger.class)) {
      graffitiBuilder.onExecutionClientVersion(BESU_CLIENT_VERSION);
      logCaptor.assertInfoLog(
          "Default graffiti to use when building block without external VC: \"I've proposed ablock TK"
              + TEKU_CLIENT_VERSION.commit().toUnprefixedHexString().substring(0, 2)
              + "BUab\". "
              + "To change check validator graffiti options.");
    }
  }

  @Test
  public void buildGraffiti_shouldNotFail() {
    this.graffitiBuilder =
        new GraffitiBuilder(clientGraffitiAppendFormat, userGraffiti) {
          @Override
          protected int calculateGraffitiLength(Optional<Bytes32> graffiti) {
            throw new RuntimeException("");
          }
        };
    assertThat(graffitiBuilder.buildGraffiti(Optional.empty())).isEqualTo(Bytes32.ZERO);
    final Bytes32 graffiti = Bytes32Parser.toBytes32(ASCII_GRAFFITI_20);
    assertThat(graffitiBuilder.buildGraffiti(Optional.of(graffiti))).isEqualTo(graffiti);
  }

  @ParameterizedTest()
  @MethodSource("getBuildGraffitiFixtures")
  public void buildGraffiti_shouldProvideCorrectOutput(
      final ClientGraffitiAppendFormat clientGraffitiAppendFormat,
      final Optional<String> maybeUserGraffiti,
      final String expectedGraffiti) {
    this.graffitiBuilder = new GraffitiBuilder(clientGraffitiAppendFormat, userGraffiti);
    graffitiBuilder.onExecutionClientVersion(BESU_CLIENT_VERSION);
    final Bytes32 expectedGraffitiBytes = Bytes32Parser.toBytes32(expectedGraffiti);
    assertThat(
            new String(
                Arrays.copyOfRange(
                    expectedGraffitiBytes.toArray(),
                    0,
                    32 - expectedGraffitiBytes.numberOfTrailingZeroBytes()),
                StandardCharsets.UTF_8))
        .isEqualTo(expectedGraffiti);
    assertThat(graffitiBuilder.buildGraffiti(maybeUserGraffiti.map(Bytes32Parser::toBytes32)))
        .isEqualTo(expectedGraffitiBytes);
  }

  @Test
  public void extractGraffiti_shouldReturnCorrectGraffiti() {
    assertThat(graffitiBuilder.extractGraffiti(Optional.empty(), 0)).isEqualTo("");
    assertThat(
            graffitiBuilder.extractGraffiti(
                Optional.of(Bytes32Parser.toBytes32(asciiGraffiti0)), 0))
        .isEqualTo("");
    assertThat(
            graffitiBuilder.extractGraffiti(
                Optional.of(Bytes32Parser.toBytes32(ASCII_GRAFFITI_20)), 20))
        .isEqualTo(ASCII_GRAFFITI_20);
    assertThat(
            graffitiBuilder.extractGraffiti(
                Optional.of(Bytes32Parser.toBytes32(asciiGraffiti32)), 32))
        .isEqualTo(asciiGraffiti32);
    assertThat(
            graffitiBuilder.extractGraffiti(
                Optional.of(Bytes32Parser.toBytes32(asciiGraffiti32)), 16))
        .isEqualTo(asciiGraffiti32.substring(0, 16));
    assertThat(
            graffitiBuilder.extractGraffiti(
                Optional.of(Bytes32Parser.toBytes32(UTF_8_GRAFFITI_4)), 4))
        .isEqualTo(UTF_8_GRAFFITI_4);
    assertThat(
            graffitiBuilder.extractGraffiti(
                Optional.of(Bytes32Parser.toBytes32(utf8Graffiti20)), 20))
        .isEqualTo(utf8Graffiti20);
    assertThat(
            graffitiBuilder.extractGraffiti(
                Optional.of(Bytes32Parser.toBytes32(utf8Graffiti20)), 24))
        .isEqualTo(utf8Graffiti20 + "\u0000\u0000\u0000\u0000");
  }

  @Test
  public void calculateGraffitiLength_shouldReturnCorrectLength() {
    assertThat(graffitiBuilder.calculateGraffitiLength(Optional.empty())).isEqualTo(0);
    assertThat(
            graffitiBuilder.calculateGraffitiLength(
                Optional.of(Bytes32Parser.toBytes32(asciiGraffiti0))))
        .isEqualTo(0);
    assertThat(
            graffitiBuilder.calculateGraffitiLength(
                Optional.of(Bytes32Parser.toBytes32(ASCII_GRAFFITI_20))))
        .isEqualTo(20);
    assertThat(
            graffitiBuilder.calculateGraffitiLength(
                Optional.of(Bytes32Parser.toBytes32(asciiGraffiti32))))
        .isEqualTo(32);
    assertThat(
            graffitiBuilder.calculateGraffitiLength(
                Optional.of(Bytes32Parser.toBytes32(UTF_8_GRAFFITI_4))))
        .isEqualTo(4);
    assertThat(
            graffitiBuilder.calculateGraffitiLength(
                Optional.of(Bytes32Parser.toBytes32(utf8Graffiti20))))
        .isEqualTo(20);
  }

  @Test
  public void join_shouldJoinStringsSkippingEmpty() {
    assertThat(graffitiBuilder.joinNonEmpty("", "aa", "bb", "cc"))
        .isEqualTo(Bytes32Parser.toBytes32("aabbcc"));
    assertThat(graffitiBuilder.joinNonEmpty(" ", "aa", "", "cc"))
        .isEqualTo(Bytes32Parser.toBytes32("aa cc"));
    assertThat(graffitiBuilder.joinNonEmpty(" ", "", "bb", "cc"))
        .isEqualTo(Bytes32Parser.toBytes32("bb cc"));
    assertThat(graffitiBuilder.joinNonEmpty(" ", "", "", "")).isEqualTo(Bytes32.ZERO);
  }

  @Test
  public void formatClientInfo_shouldFitInDesiredLength() {
    graffitiBuilder.onExecutionClientVersion(BESU_CLIENT_VERSION);

    // 20: LH1be52536BU0f91a674
    assertThat(graffitiBuilder.formatClientInfo(30))
        .isEqualTo(
            TEKU_CLIENT_VERSION.code()
                + TEKU_CLIENT_VERSION.commit().toUnprefixedHexString()
                + BESU_CLIENT_VERSION.code()
                + BESU_CLIENT_VERSION.commit().toUnprefixedHexString())
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(20));
    assertThat(graffitiBuilder.formatClientInfo(20))
        .isEqualTo(
            TEKU_CLIENT_VERSION.code()
                + TEKU_CLIENT_VERSION.commit().toUnprefixedHexString()
                + BESU_CLIENT_VERSION.code()
                + BESU_CLIENT_VERSION.commit().toUnprefixedHexString())
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(20));

    // 12: LH1be5BU0f91
    assertThat(graffitiBuilder.formatClientInfo(19))
        .isEqualTo(
            TEKU_CLIENT_VERSION.code()
                + TEKU_CLIENT_VERSION.commit().toUnprefixedHexString().substring(0, 4)
                + BESU_CLIENT_VERSION.code()
                + BESU_CLIENT_VERSION.commit().toUnprefixedHexString().substring(0, 4))
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(12));
    assertThat(graffitiBuilder.formatClientInfo(12))
        .isEqualTo(
            TEKU_CLIENT_VERSION.code()
                + TEKU_CLIENT_VERSION.commit().toUnprefixedHexString().substring(0, 4)
                + BESU_CLIENT_VERSION.code()
                + BESU_CLIENT_VERSION.commit().toUnprefixedHexString().substring(0, 4))
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(12));

    // 8: LH1bBU0f
    assertThat(graffitiBuilder.formatClientInfo(11))
        .isEqualTo(
            TEKU_CLIENT_VERSION.code()
                + TEKU_CLIENT_VERSION.commit().toUnprefixedHexString().substring(0, 2)
                + BESU_CLIENT_VERSION.code()
                + BESU_CLIENT_VERSION.commit().toUnprefixedHexString().substring(0, 2))
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(8));
    assertThat(graffitiBuilder.formatClientInfo(8))
        .isEqualTo(
            TEKU_CLIENT_VERSION.code()
                + TEKU_CLIENT_VERSION.commit().toUnprefixedHexString().substring(0, 2)
                + BESU_CLIENT_VERSION.code()
                + BESU_CLIENT_VERSION.commit().toUnprefixedHexString().substring(0, 2))
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(8));

    // 4: LHBU
    assertThat(graffitiBuilder.formatClientInfo(7))
        .isEqualTo(TEKU_CLIENT_VERSION.code() + BESU_CLIENT_VERSION.code())
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(4));
    assertThat(graffitiBuilder.formatClientInfo(4))
        .isEqualTo(TEKU_CLIENT_VERSION.code() + BESU_CLIENT_VERSION.code())
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(4));

    // Empty
    assertThat(graffitiBuilder.formatClientInfo(3))
        .isEqualTo("")
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(0));
    assertThat(graffitiBuilder.formatClientInfo(0))
        .isEqualTo("")
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(0));
    assertThat(graffitiBuilder.formatClientInfo(-1))
        .isEqualTo("")
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(0));
  }

  @ParameterizedTest()
  @MethodSource("getClientCodes")
  public void formatClientInfo_shouldHandleBadCode(final String code, final String expectedCode) {
    graffitiBuilder.onExecutionClientVersion(
        new ClientVersion(
            code,
            BESU_CLIENT_VERSION.name(),
            BESU_CLIENT_VERSION.version(),
            BESU_CLIENT_VERSION.commit()));

    // 20: LH1be52536BU0f91a674
    assertThat(graffitiBuilder.formatClientInfo(20))
        .isEqualTo(
            TEKU_CLIENT_VERSION.code()
                + TEKU_CLIENT_VERSION.commit().toUnprefixedHexString()
                + expectedCode
                + BESU_CLIENT_VERSION.commit().toUnprefixedHexString())
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(20));

    // 12: LH1be5BU0f91
    assertThat(graffitiBuilder.formatClientInfo(12))
        .isEqualTo(
            TEKU_CLIENT_VERSION.code()
                + TEKU_CLIENT_VERSION.commit().toUnprefixedHexString().substring(0, 4)
                + expectedCode
                + BESU_CLIENT_VERSION.commit().toUnprefixedHexString().substring(0, 4))
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(12));

    // 8: LH1bBU0f
    assertThat(graffitiBuilder.formatClientInfo(8))
        .isEqualTo(
            TEKU_CLIENT_VERSION.code()
                + TEKU_CLIENT_VERSION.commit().toUnprefixedHexString().substring(0, 2)
                + expectedCode
                + BESU_CLIENT_VERSION.commit().toUnprefixedHexString().substring(0, 2))
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(8));

    // 4: LHBU
    assertThat(graffitiBuilder.formatClientInfo(4))
        .isEqualTo(TEKU_CLIENT_VERSION.code() + expectedCode)
        .satisfies(s -> assertThat(s.getBytes(StandardCharsets.UTF_8).length).isEqualTo(4));
  }

  private static Stream<Arguments> getClientCodes() {
    return Stream.of(
        Arguments.of("bu", "BU"),
        Arguments.of("bU", "BU"),
        Arguments.of("bur", "BU"),
        Arguments.of("b", "NA"),
        Arguments.of("12", "NA"),
        Arguments.of("", "NA"),
        Arguments.of(null, "NA"));
  }

  private static Stream<Arguments> getBuildGraffitiFixtures() {
    return Stream.of(
        Arguments.of(
            AUTO,
            Optional.empty(),
            TEKU_CLIENT_VERSION.code()
                + TEKU_CLIENT_VERSION.commit().toUnprefixedHexString()
                + BESU_CLIENT_VERSION.code()
                + BESU_CLIENT_VERSION.commit().toUnprefixedHexString()),
        Arguments.of(
            AUTO,
            Optional.of("small"),
            "small "
                + TEKU_CLIENT_VERSION.code()
                + TEKU_CLIENT_VERSION.commit().toUnprefixedHexString()
                + BESU_CLIENT_VERSION.code()
                + BESU_CLIENT_VERSION.commit().toUnprefixedHexString()),
        Arguments.of(
            AUTO,
            Optional.of(UTF_8_GRAFFITI_4),
            UTF_8_GRAFFITI_4
                + " "
                + TEKU_CLIENT_VERSION.code()
                + TEKU_CLIENT_VERSION.commit().toUnprefixedHexString()
                + BESU_CLIENT_VERSION.code()
                + BESU_CLIENT_VERSION.commit().toUnprefixedHexString()),
        Arguments.of(
            AUTO,
            Optional.of(ASCII_GRAFFITI_20),
            ASCII_GRAFFITI_20
                + " "
                + TEKU_CLIENT_VERSION.code()
                + TEKU_CLIENT_VERSION.commit().toUnprefixedHexString().substring(0, 2)
                + BESU_CLIENT_VERSION.code()
                + BESU_CLIENT_VERSION.commit().toUnprefixedHexString().substring(0, 2)),
        Arguments.of(
            NAME, Optional.empty(), TEKU_CLIENT_VERSION.code() + BESU_CLIENT_VERSION.code()),
        Arguments.of(
            NAME,
            Optional.of("small"),
            "small " + TEKU_CLIENT_VERSION.code() + BESU_CLIENT_VERSION.code()),
        Arguments.of(
            NAME,
            Optional.of(UTF_8_GRAFFITI_4),
            UTF_8_GRAFFITI_4 + " " + TEKU_CLIENT_VERSION.code() + BESU_CLIENT_VERSION.code()),
        Arguments.of(
            NAME,
            Optional.of(ASCII_GRAFFITI_20),
            ASCII_GRAFFITI_20 + " " + TEKU_CLIENT_VERSION.code() + BESU_CLIENT_VERSION.code()),
        Arguments.of(NONE, Optional.empty(), ""),
        Arguments.of(NONE, Optional.of("small"), "small"),
        Arguments.of(NONE, Optional.of(UTF_8_GRAFFITI_4), UTF_8_GRAFFITI_4),
        Arguments.of(NONE, Optional.of(ASCII_GRAFFITI_20), ASCII_GRAFFITI_20));
  }
}
