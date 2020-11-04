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

package tech.pegasys.teku.cli.subcommand.internal.validator.options;

import java.io.File;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

class Eth1PrivateKeyOptions {
  @Option(
      names = {"--eth1-private-key"},
      required = true,
      paramLabel = "<KEY>",
      description = "Ethereum 1 private key to use to send transactions")
  String eth1PrivateKey;

  @ArgGroup(exclusive = false, multiplicity = "1")
  Eth1EncryptedKeystoreOptions keystoreOptions;

  static class Eth1EncryptedKeystoreOptions {
    @Option(
        names = {"--eth1-keystore-file"},
        required = true,
        paramLabel = "<FILE>",
        description =
            "Path to encrypted (V3) keystore containing Ethereum 1 private key to use to send transactions")
    File eth1KeystoreFile;

    @Option(
        names = {"--eth1-keystore-password-file"},
        required = true,
        paramLabel = "<FILE>",
        description = "Path to file containing password to decrypt Ethereum 1 keystore")
    File eth1KeystorePasswordFile;
  }
}
