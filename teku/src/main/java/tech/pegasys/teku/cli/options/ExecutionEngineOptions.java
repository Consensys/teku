/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.cli.options;

import static tech.pegasys.teku.config.TekuConfiguration.Builder;

import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Option;

public class ExecutionEngineOptions {

  @Option(
      names = {"--Xee-endpoints", "--Xee-endpoint"},
      paramLabel = "<NETWORK>",
      description = "URLs for Execution Engine nodes.",
      split = ",",
      arity = "0..*",
      hidden = true)
  private List<String> eeEndpoints = new ArrayList<>();

  @Option(
      names = {"--Xee-fee-recipient-address"},
      paramLabel = "<ADDRESS>",
      description =
          "Suggested fee recipient sent to the execution engine, which could use it as coinbase when producing a new execution block.",
      arity = "0..1",
      hidden = true)
  private String feeRecipient = null;

  public void configure(final Builder builder) {
    builder.executionEngine(b -> b.endpoints(eeEndpoints).feeRecipient(feeRecipient));
  }
}
