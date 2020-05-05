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

public class TestObject {
  @SuppressWarnings("rawtypes")
  Class className;

  String fileName;
  Path path;

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  @SuppressWarnings("rawtypes")
  public Class getClassName() {
    return className;
  }

  @SuppressWarnings("rawtypes")
  public void setClassName(Class className) {
    this.className = className;
  }

  public Path getPath() {
    return path;
  }

  public void setPath(Path path) {
    this.path = path;
  }

  @SuppressWarnings("rawtypes")
  public TestObject(String fileName, Class className, Path path) {
    this.fileName = fileName;
    this.className = className;
    this.path = path;
  }
}
