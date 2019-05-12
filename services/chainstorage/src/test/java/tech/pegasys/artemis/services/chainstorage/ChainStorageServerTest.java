/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.services.chainstorage;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import picocli.CommandLine;
import tech.pegasys.artemis.data.RawRecord;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.services.ServiceConfig;
import tech.pegasys.artemis.util.cli.CommandLineArguments;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

@ExtendWith(BouncyCastleExtension.class)
class ChainStorageServerTest {
  private final ExecutorService threadPool =
      Executors.newCachedThreadPool(
          r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
          });

  public static RawRecord createRawRecord() {
    return new RawRecord(
        1L,
        new BeaconStateWithCache(),
        BeaconBlock.createGenesis(Bytes32.random()),
        new BeaconState(),
        BeaconBlock.createGenesis(Bytes32.random()),
        new BeaconState(),
        BeaconBlock.createGenesis(Bytes32.random()),
        new Date());
  }

  @Test
  void testRecordPosting() throws IOException, InterruptedException {
    CommandLineArguments cliArgs = new CommandLineArguments();
    CommandLine commandLine = new CommandLine(cliArgs);
    commandLine.parse("");

    // Read all lines from a file
    String contents = "";
    contents =
        new String(
            Files.readAllBytes(Paths.get("../../config/config.toml")), StandardCharsets.UTF_8);

    ArtemisConfiguration config = ArtemisConfiguration.fromString(contents);

    EventBus eventBus = new AsyncEventBus(threadPool);

    ChainStorageService service = new ChainStorageService();
    service.init(new ServiceConfig(eventBus, config, cliArgs));
    eventBus.post(createRawRecord());
  }
}
