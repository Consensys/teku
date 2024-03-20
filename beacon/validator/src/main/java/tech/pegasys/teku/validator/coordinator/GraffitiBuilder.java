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

import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;

import com.google.common.annotations.VisibleForTesting;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.ExecutionClientVersionChannel;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;
import tech.pegasys.teku.validator.api.Bytes32Parser;
import tech.pegasys.teku.validator.api.ClientGraffitiAppendFormat;

/**
 * Generates graffiti by combining user-supplied graffiti with information from CL/EL clients,
 * according to the clientGraffitiAppendFormat configuration.
 */
public class GraffitiBuilder implements ExecutionClientVersionChannel {
  private static final Logger LOG = LogManager.getLogger();

  private static final String SPACE = " ";

  private final ClientGraffitiAppendFormat clientGraffitiAppendFormat;
  private final ClientVersion consensusClientVersion;
  private final AtomicReference<ClientVersion> executionClientVersion;
  private final Optional<Bytes32> defaultUserGraffiti;

  public GraffitiBuilder(
      final ClientGraffitiAppendFormat clientGraffitiAppendFormat,
      final Optional<Bytes32> defaultUserGraffiti) {
    this.clientGraffitiAppendFormat = clientGraffitiAppendFormat;
    this.consensusClientVersion = createTekuClientVersion();
    this.executionClientVersion = new AtomicReference<>(ClientVersion.UNKNOWN);
    this.defaultUserGraffiti = defaultUserGraffiti;
  }

  private ClientVersion createTekuClientVersion() {
    return new ClientVersion(
        ClientVersion.TEKU_CLIENT_CODE,
        VersionProvider.CLIENT_IDENTITY,
        VersionProvider.IMPLEMENTATION_VERSION,
        VersionProvider.COMMIT_HASH
            .map(commitHash -> Bytes4.fromHexString(commitHash.substring(0, 8)))
            .orElse(Bytes4.ZERO));
  }

  public ClientVersion getConsensusClientVersion() {
    return consensusClientVersion;
  }

  @Override
  public void onExecutionClientVersion(final ClientVersion executionClientVersion) {
    this.executionClientVersion.set(executionClientVersion);
    final Optional<Bytes32> defaultGraffiti = Optional.of(buildGraffiti(defaultUserGraffiti));
    EVENT_LOG.logDefaultGraffiti(
        extractGraffiti(defaultGraffiti, calculateGraffitiLength(defaultGraffiti)));
  }

  public Bytes32 buildGraffiti(final Optional<Bytes32> userGraffiti) {
    try {
      final int userGraffitiLength = calculateGraffitiLength(userGraffiti);

      return switch (clientGraffitiAppendFormat) {
        case AUTO -> {
          final int clientInfoLength = Bytes32.SIZE - 1 - userGraffitiLength;
          yield joinNonEmpty(
              SPACE,
              extractGraffiti(userGraffiti, userGraffitiLength),
              formatClientsInfo(clientInfoLength));
        }
        case CLIENT_NAMES -> {
          final int clientInfoLength = Integer.min(Bytes32.SIZE - 1 - userGraffitiLength, 4);
          yield joinNonEmpty(
              SPACE,
              extractGraffiti(userGraffiti, userGraffitiLength),
              formatClientsInfo(clientInfoLength));
        }
        case DISABLED -> userGraffiti.orElse(Bytes32.ZERO);
      };
    } catch (final Exception ex) {
      LOG.error("Unexpected error when preparing block graffiti", ex);
      return userGraffiti.orElse(Bytes32.ZERO);
    }
  }

  @VisibleForTesting
  protected String extractGraffiti(final Optional<Bytes32> graffiti, final int length) {
    return graffiti
        .map(Bytes::toArray)
        .map(bytes -> Arrays.copyOf(bytes, length))
        .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
        .orElse("");
  }

  @VisibleForTesting
  protected int calculateGraffitiLength(final Optional<Bytes32> graffiti) {
    return Bytes32.SIZE - graffiti.map(Bytes::numberOfTrailingZeroBytes).orElse(Bytes32.SIZE);
  }

  @VisibleForTesting
  protected Bytes32 joinNonEmpty(final String delimiter, final String... parts) {
    final String graffiti =
        Arrays.stream(parts).filter(part -> !part.isEmpty()).collect(Collectors.joining(delimiter));
    return Bytes32Parser.toBytes32(graffiti);
  }

  @VisibleForTesting
  protected String formatClientsInfo(final int length) {
    final String safeConsensusCode = extractClientCodeSafely(consensusClientVersion);
    final String safeExecutionCode = extractClientCodeSafely(executionClientVersion.get());
    // LH1be52536BU0f91a674
    if (length >= 20) {
      return String.format(
          "%s%s%s%s",
          safeConsensusCode,
          consensusClientVersion.commit().toUnprefixedHexString(),
          safeExecutionCode,
          executionClientVersion.get().commit().toUnprefixedHexString());
    }
    // LH1be5BU0f91
    if (length >= 12) {
      return String.format(
          "%s%s%s%s",
          safeConsensusCode,
          consensusClientVersion.commit().toUnprefixedHexString().substring(0, 4),
          safeExecutionCode,
          executionClientVersion.get().commit().toUnprefixedHexString().substring(0, 4));
    }
    // LH1bBU0f
    if (length >= 8) {
      return String.format(
          "%s%s%s%s",
          safeConsensusCode,
          consensusClientVersion.commit().toUnprefixedHexString().substring(0, 2),
          safeExecutionCode,
          executionClientVersion.get().commit().toUnprefixedHexString().substring(0, 2));
    }
    // LHBU
    if (length >= 4) {
      return String.format("%s%s", safeConsensusCode, safeExecutionCode);
    }

    return "";
  }

  protected String extractClientCodeSafely(final ClientVersion clientVersion) {
    final boolean isValid =
        clientVersion.code() != null
            && clientVersion.code().length() >= 2
            && clientVersion.code().substring(0, 2).matches("[a-zA-Z]{2}");
    return isValid
        ? clientVersion.code().substring(0, 2).toUpperCase(Locale.ROOT)
        : ClientVersion.UNKNOWN.code();
  }
}
