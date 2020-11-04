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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import picocli.CommandLine;

public class WithdrawalPasswordOptions implements KeystorePasswordOptions {
  @CommandLine.Option(
      names = {"--encrypted-keystore-withdrawal-password-file"},
      paramLabel = "<FILE>",
      description = "Read password from the file to encrypt the withdrawal keys")
  File withdrawalPasswordFile;

  @CommandLine.Option(
      names = {"--encrypted-keystore-withdrawal-password-env"},
      paramLabel = "<ENV_VAR>",
      description = "Read password from environment variable to encrypt the withdrawal keys")
  String withdrawalPasswordEnv;

  @Override
  public File getPasswordFile() {
    return withdrawalPasswordFile;
  }

  @Override
  public String getPasswordEnvironmentVariable() {
    return withdrawalPasswordEnv;
  }

  public void setWithdrawalPasswordEnv(final String withdrawalPasswordEnv) {
    checkNotNull(withdrawalPasswordEnv);
    this.withdrawalPasswordEnv = withdrawalPasswordEnv;
  }

  public void setWithdrawalPasswordFile(final File withdrawalPasswordFile) {
    checkNotNull(withdrawalPasswordFile);
    this.withdrawalPasswordFile = withdrawalPasswordFile;
  }
}
