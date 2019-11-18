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

package tech.pegasys.artemis.ganache;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class GanacheController {
  private static final Logger LOG = LogManager.getLogger();

  final String ganachePath = System.getProperty("user.dir") + "/ganache-cli";
  private static final String DEFAULT_HOST_NAME = "http://127.0.0.1";
  private static final String DEFAULT_PORT = "8545";

  private static final String NODE = "node";
  private static final String CLI = "cli.js";
  private static final String BALANCE_PARAM = "-e";
  private static final String ACCOUNT_SIZE_PARAM = "-a";
  private static final String KEY_PATH_PARAM = "--acctKeys";

  private Thread ganacheThread;
  private List<SECP256K1.KeyPair> accounts;
  private String provider;
  private int waitIterations;

  private volatile File keyFile;

  public GanacheController(int accountSize, int balance) {
    this(DEFAULT_HOST_NAME, DEFAULT_PORT, accountSize, balance);
  }

  public GanacheController(String hostName, String port, int accountSize, int balance) {
    provider = hostName + ":" + port;
    cleanUp();
    waitIterations = Math.max(accountSize / 200, 20);
    try {
      keyFile = File.createTempFile("keys", ".json");
    } catch (final IOException e) {
      LOG.fatal("Failed to create key file", e);
    }

    // starts a child process of ganache-cli and generates a keys.json file
    ProcessBuilder pb =
        new ProcessBuilder(
            NODE,
            CLI,
            ACCOUNT_SIZE_PARAM,
            "" + accountSize,
            BALANCE_PARAM,
            "" + balance,
            KEY_PATH_PARAM,
            keyFile.getAbsolutePath());
    pb.directory(new File(ganachePath));

    // cleans up process and deletes keys.json
    try {
      ganacheThread =
          new Thread() {
            Process process = pb.start();

            @Override
            public void run() {
              process.destroy();
              keyFile.delete();
            }
          };
    } catch (IOException e) {
      LOG.fatal(
          "GanacheController.constructor: IOException thrown when attempting \""
              + pb.command()
              + "\" to start ganache-cli instance",
          e);
    }
    // calls the cleanup thread after a shutdown signal is detected
    Runtime.getRuntime().addShutdownHook(ganacheThread);
    // waits for keys.json for 10 second and then reads in keys.json from a file
    initKeys();
  }

  // work around for a bug documented about the shutdown vs stop process
  // https://youtrack.jetbrains.com/issue/IDEA-75946
  // removes any remaining files and cleanup orphaned processes
  public void cleanUp() {
    if (keyFile != null) {
      keyFile.delete();
    }
    ProcessBuilder pb = new ProcessBuilder("killall", "-9", "node", "cli.js");
    try {
      pb.start();
    } catch (IOException e) {
      LOG.warn(
          "GanacheController.cleanUp: IOException thrown when attempting \""
              + pb.command()
              + "\" cleanup of hung ganache-cli instance",
          e);
    }
  }

  // Wait for keys.json file to be copied to the keysPath directory
  @SuppressWarnings({"unchecked", "DefaultCharset"})
  public void initKeys() {
    accounts = new ArrayList<>();
    JSONObject accountsJSON = null;
    try {
      int waitInterval = 0;
      while (waitInterval < waitIterations) {
        if (keyFile.exists() && keyFile.length() > 0) break;
        Thread.sleep(500);
        waitInterval++;
      }
      JSONParser parser = new JSONParser();
      accountsJSON =
          (JSONObject) ((JSONObject) parser.parse(new FileReader(keyFile))).get("private_keys");
    } catch (Exception e) {
      if (!keyFile.exists())
        LOG.fatal("GanacheController.initkeys: Timedout when waiting for keys.json filet", e);
      else
        LOG.fatal(
            "GanacheController.initkeys: Exception thrown when reading/parsing keys.json file", e);
    }
    Set<String> keys = accountsJSON.keySet();
    // SECP256K1.SecretKey.fromBytes(Bytes32.fromHexString(accountsJSON.get(key).toString())))

    for (String key : keys) {
      SECP256K1.KeyPair keyPair =
          SECP256K1.KeyPair.fromSecretKey(
              SECP256K1.SecretKey.fromBytes(Bytes32.fromHexString((String) accountsJSON.get(key))));
      accounts.add(keyPair);
    }
  }

  public List<SECP256K1.KeyPair> getAccounts() {
    return accounts;
  }

  public String getProvider() {
    return provider;
  }
}
