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

package tech.pegasys.artemis.reference.framework;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/*
 * TODO: Move outside of framework package once implementation is complete.
 * The runner for the Eth2 spec shuffling reference tests.
 */

class ShufflingTestRunner implements ReferenceTestRunner {
  private static final TestRunnerType runnerType = TestRunnerType.SHUFFLING;

  private List<ReferenceTestHandler> handlers;

  public void init() {
    handlers = loadTestHandlers();
  }

  @Override
  public TestRunnerType getTestRunnerType() {
    return runnerType;
  }

  @Override
  public List<ReferenceTestHandler> loadTestHandlers() {
    try (Stream<Path> paths = Files.walk(Paths.get("**/shuffling/"))) {
    } catch (IOException e) {
      // Fail the test, log, and move on.
    }
    return new ArrayList<>();
  }

  public void getBasePath() {}
}
