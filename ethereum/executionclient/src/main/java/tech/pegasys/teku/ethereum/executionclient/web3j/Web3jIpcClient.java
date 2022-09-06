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

package tech.pegasys.teku.ethereum.executionclient.web3j;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.ipc.IpcService;
import org.web3j.protocol.ipc.UnixIpcService;
import org.web3j.protocol.ipc.WindowsIpcService;
import tech.pegasys.teku.ethereum.executionclient.auth.JwtConfig;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

class Web3jIpcClient extends Web3JClient {
  private static final Logger LOG = LogManager.getLogger();

  Web3jIpcClient(
      final EventLogger eventLog,
      final URI endpoint,
      final TimeProvider timeProvider,
      final Optional<JwtConfig> jwtConfig) {
    super(eventLog, timeProvider);
    if (jwtConfig.isPresent()) {
      LOG.warn("JWT configuration is ignored with IPC endpoint URI");
    }
    final String ipcPath = Path.of(endpoint).toString();
    final IpcService ipcService;
    if (SystemUtils.IS_OS_WINDOWS) {
      ipcService = new WindowsIpcService(ipcPath);
    } else if (SystemUtils.IS_OS_UNIX) {
      ipcService = new UnixIpcService(ipcPath);
    } else {
      throw new InvalidConfigurationException(
          "IPC is supported only on Windows and UNIX-compliant operating systems");
    }
    initWeb3jService(ipcService);
  }
}
