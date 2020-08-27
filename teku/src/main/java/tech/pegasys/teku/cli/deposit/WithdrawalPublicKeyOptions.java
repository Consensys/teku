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

package tech.pegasys.teku.cli.deposit;

import picocli.CommandLine.Option;

import java.io.File;

class WithdrawalPublicKeyOptions {

  @Option(
      names = {"--withdrawal-public-key"},
      paramLabel = "<PUBLIC_KEY>",
      required = true,
      description = "Public withdrawal key for the validator")
  String withdrawalKey;

    @Option(
        names = {"--withdrawal-keystore-file"},
        required = true,
        paramLabel = "<FILE>",
        description = "Path to encrypted (V3) keystore containing withdrawal key for the validator")
    File withdrawalKeystoreFile;
}
