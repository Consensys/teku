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

package tech.pegasys.teku.validator.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

public class FileBackedGraffitiProviderTest {
  private static final Bytes32 graffiti = Bytes32Parser.toBytes32("myGraffiti");
  private static final Bytes32 graffitiFromSampleFile = Bytes32Parser.toBytes32("123456789");

  @Test
  public void testBothEmpty() {
    var graffitiProvider = new FileBackedGraffitiProvider();
    assertThat(graffitiProvider.get()).isNotPresent();
  }

  @Test
  public void testGraffitiWithoutFile() {
    var graffitiProvider = new FileBackedGraffitiProvider(Optional.of(graffiti), Optional.empty());
    assertThat(graffitiProvider.get()).isEqualTo(Optional.of(graffiti));
  }

  @Test
  public void testEmptyGraffitiWithFile() {
    var graffitiProvider =
        new FileBackedGraffitiProvider(
            Optional.empty(), Optional.of(Path.of("src/test/resources/graffitiSample.txt")));
    assertThat(graffitiProvider.get()).isEqualTo(Optional.of(graffitiFromSampleFile));
  }

  @Test
  public void testGraffitiWithFile() {
    var graffitiProvider =
        new FileBackedGraffitiProvider(
            Optional.of(graffiti), Optional.of(Path.of("src/test/resources/graffitiSample.txt")));
    assertThat(graffitiProvider.get()).isEqualTo(Optional.of(graffitiFromSampleFile));
  }

  @Test
  public void testDefaultGraffitiIsUsedIfFileReadFails() {
    var graffitiProvider =
        new FileBackedGraffitiProvider(
            Optional.of(graffiti), Optional.of(Path.of("src/test/resources/noGraffitiFound.txt")));
    assertThat(graffitiProvider.get()).isEqualTo(Optional.of(graffiti));
  }
}
