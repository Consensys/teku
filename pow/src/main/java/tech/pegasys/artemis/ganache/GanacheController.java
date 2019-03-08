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
import org.apache.logging.log4j.Level;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import tech.pegasys.artemis.util.alogger.ALogger;

public class GanacheController {

  final String keysPath = System.getProperty("user.dir") + "/pow/src/main/resources/keys.json";
  final String ganachePath = System.getProperty("user.dir") + "/ganache-cli";
  private static final String DEFAULT_HOST_NAME = "http://127.0.0.1";
  private static final String DEFAULT_PORT = "8545";

  private static final String NODE = "node";
  private static final String CLI = "cli.js";
  private static final String HOST_NAME_PARAM = "-h";
  private static final String PORT = "-p";
  private static final String BALANCE_PARAM = "-e";
  private static final String ACCOUNT_SIZE_PARAM = "-a";
  private static final String KEY_PATH_PARAM = "--acctKeys";

  private Thread ganacheThread;

  private List<Account> accounts;
  private String provider;

  private static final ALogger LOG = new ALogger();

  public GanacheController(int accountSize, int balance) {
    this(DEFAULT_HOST_NAME, DEFAULT_PORT, accountSize, balance);
  }

  public GanacheController(String hostName, String port, int accountSize, int balance) {
    provider = hostName + ":" + port;
    cleanUp();
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
            keysPath);
    pb.directory(new File(ganachePath));

    // cleans up process and deletes keys.json
    try {
      ganacheThread =
          new Thread() {
            Process process = pb.start();

            @Override
            public void run() {
              process.destroy();
              new File(keysPath).delete();
            }
          };
    } catch (IOException e) {
      LOG.log(
          Level.FATAL,
          "GanacheController.constructor: IOException thrown when attempting \""
              + pb.command()
              + "\" to start ganache-cli instance\n"
              + e);
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
    new File(keysPath).delete();
    ProcessBuilder pb = new ProcessBuilder("killall", "-9", "node", "cli.js");
    try {
      pb.start();
    } catch (IOException e) {
      LOG.log(
          Level.WARN,
          "GanacheController.cleanUp: IOException thrown when attempting \""
              + pb.command()
              + "\" cleanup of hung ganache-cli instance\n"
              + e);
    }
  }

  // Wait for keys.json file to be copied to the keysPath directory
  @SuppressWarnings({"unchecked", "DefaultCharset"})
  public void initKeys() {
    accounts = new ArrayList<Account>();
    JSONObject accountsJSON = null;
    File keyFile = new File(keysPath);
    try {
      int waitInterval = 0;
      while (waitInterval < 20) {
        if (keyFile.exists()) break;
        Thread.sleep(500);
        waitInterval++;
      }
      JSONParser parser = new JSONParser();
      accountsJSON =
          (JSONObject) ((JSONObject) parser.parse(new FileReader(keysPath))).get("private_keys");
    } catch (Exception e) {
      if (!keyFile.exists())
        LOG.log(
            Level.FATAL,
            "GanacheController.initkeys: Timedout when waiting for keys.json filet\n" + e);
      else
        LOG.log(
            Level.FATAL,
            "GanacheController.initkeys: Exception thrown when reading/parsing keys.json file\n"
                + e);
    }
    Set<String> keys = accountsJSON.keySet();
    for (String key : keys) accounts.add(new Account(key, accountsJSON.get(key).toString()));
  }

  public List<Account> getAccounts() {
    return accounts;
  }

  public String getProvider() {
    return provider;
  }
}
