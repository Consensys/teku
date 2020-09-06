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

package tech.pegasys.teku.infrastructure.io.resource;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class ClasspathResourceLoaderTest {

  public static final String TEST_FILE_1 = "testFile1";
  public static final String TEST_FILE_2 = "testFile2";
  public static final Bytes TEST_FILE_1_CONTENT =
      Bytes.wrap("Test file 1".getBytes(StandardCharsets.UTF_8));
  public static final Bytes TEST_FILE_2_CONTENT =
      Bytes.wrap("Test file 2".getBytes(StandardCharsets.UTF_8));

  @Test
  public void shouldLoadAllowedResources() throws Exception {
    final ResourceLoader loader =
        new ClasspathResourceLoader(
            ClasspathResourceLoaderTest.class, name -> name + ".txt", TEST_FILE_1, TEST_FILE_2);
    assertThat(loader.loadBytes(TEST_FILE_1)).contains(TEST_FILE_1_CONTENT);
    assertThat(loader.loadBytes(TEST_FILE_2)).contains(TEST_FILE_2_CONTENT);
  }

  @Test
  public void shouldNotLoadDisallowedResources() throws Exception {
    final ResourceLoader loader =
        new ClasspathResourceLoader(
            ClasspathResourceLoaderTest.class, name -> name + ".txt", TEST_FILE_1);

    // Because only testFile1 is allowed.
    assertThat(loader.loadBytes(TEST_FILE_2)).isEmpty();
  }

  @Test
  public void shouldReturnEmptyWhenResourceIsNotFound() throws Exception {
    final ResourceLoader loader =
        new ClasspathResourceLoader(
            ClasspathResourceLoaderTest.class, name -> name + ".txt", "foo");

    // Because only testFile1 is allowed.
    assertThat(loader.loadBytes("foo")).isEmpty();
  }
}
