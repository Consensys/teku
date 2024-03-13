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

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;

/**
 * Based on <a href="https://github.com/ethereum/execution-apis/pull/517">Specify Client Versions on
 * Engine API</a>. This provider will return EL & Teku client version graffiti when the EL supports
 * version specification and {@link ExecutionLayerChannel#engineGetClientVersion(ClientVersion)} has
 * been called. Otherwise, it will return an only Teku version graffiti fallback.
 */
public class DefaultGraffitiProviderImpl
    implements DefaultGraffitiProvider, ExecutionClientEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionLayerChannel executionLayerChannel;
  private final ClientVersion tekuClientVersion;
  private final AtomicReference<Bytes32> defaultGraffiti;

  public DefaultGraffitiProviderImpl(final ExecutionLayerChannel executionLayerChannel) {
    this.executionLayerChannel = executionLayerChannel;
    this.tekuClientVersion = createTekuClientVersion();
    this.defaultGraffiti = new AtomicReference<>(getGraffitiFallback());
  }

  @Override
  public Bytes32 getDefaultGraffiti() {
    return defaultGraffiti.get();
  }

  @Override
  public void onAvailabilityUpdated(final boolean isAvailable) {
    if (isAvailable) {
      updateDefaultGraffiti();
    }
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

  private Bytes32 getGraffitiFallback() {
    final String graffiti =
        VersionProvider.CLIENT_IDENTITY + "/" + VersionProvider.IMPLEMENTATION_VERSION;
    return convertToBytes32(graffiti);
  }

  private void updateDefaultGraffiti() {
    executionLayerChannel
        .engineGetClientVersion(tekuClientVersion)
        .thenAccept(
            clientVersions -> {
              final ClientVersion executionClientVersion = clientVersions.get(0);
              EVENT_LOG.logExecutionClientVersion(
                  executionClientVersion.name(), executionClientVersion.version());
              defaultGraffiti.set(getExecutionAndTekuGraffiti(executionClientVersion));
            })
        .finish(ex -> LOG.debug("Exception while calling engine_getClientVersion", ex));
  }

  private Bytes32 getExecutionAndTekuGraffiti(final ClientVersion executionClientVersion) {
    final String graffiti =
        executionClientVersion.code()
            + executionClientVersion.commit().toUnprefixedHexString()
            + tekuClientVersion.code()
            + tekuClientVersion.commit().toUnprefixedHexString();
    return convertToBytes32(graffiti);
  }

  private Bytes32 convertToBytes32(final String graffiti) {
    final Bytes graffitiBytes = Bytes.wrap(graffiti.getBytes(StandardCharsets.UTF_8));
    if (graffitiBytes.size() <= Bytes32.SIZE) {
      return Bytes32.rightPad(graffitiBytes);
    } else {
      return Bytes32.wrap(graffitiBytes.slice(0, Bytes32.SIZE));
    }
  }
}
