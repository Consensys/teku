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

package tech.pegasys.teku.ethtests;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TestSet extends ArrayList<TestObject> {

  public Path path;

  public TestSet(Path path) {
    this.path = path;
  }

  public TestSet(TestSet testset) {
    super(testset);
    this.path = testset.getPath();
  }

  // provides a list of fileNames without duplicates
  public List<String> getFileNames() {
    return this.stream().map(TestObject::getFileName).distinct().collect(Collectors.toList());
  }

  public Path getPath() {
    return path;
  }

  public void setPath(Path path) {
    this.path = path;
  }

  public TestObject getTestObjectByFileName1(String fileName) {
    return this.stream()
        .filter(testObject -> testObject.getFileName().equals(fileName))
        .collect(Collectors.toList())
        .get(0);
  }

  public List<TestObject> getTestObjectByFileName(String fileName) {
    return this.stream()
        .filter(testObject -> testObject.getFileName().equals(fileName))
        .collect(Collectors.toList());
  }
}
