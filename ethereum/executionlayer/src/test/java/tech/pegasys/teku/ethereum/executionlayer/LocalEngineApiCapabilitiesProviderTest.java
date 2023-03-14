/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.ethereum.executionlayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineJsonRpcMethod;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

class LocalEngineApiCapabilitiesProviderTest {

  private final Spec spec = TestSpecFactory.createMinimalCapella();
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);

  LocalEngineApiCapabilitiesProvider factory;

  @BeforeEach
  public void setUp() {
    factory = new LocalEngineApiCapabilitiesProvider(spec, executionEngineClient);
  }

  @Test
  public void getAllSupportedMethodsShouldReturnEveryImplementedMethod() throws Exception {
    final List<Class<?>> classesImplementingEngineJsonRpcInterface =
        ClassPath.from(ClassLoader.getSystemClassLoader()).getAllClasses().stream()
            .filter(clazz -> clazz.getPackageName().contains("tech.pegasys.teku"))
            .map(ClassInfo::load)
            .collect(Collectors.toList())
            .stream()
            .filter(EngineJsonRpcMethod.class::isAssignableFrom)
            .filter(c -> !c.isInterface())
            .filter(c -> !Modifier.isAbstract(c.getModifiers()))
            .collect(Collectors.toList());

    final List<? extends Class<?>> methodsClasses =
        factory.supportedMethods().stream()
            .map(method -> method.getClass())
            .collect(Collectors.toList());

    assertThat(classesImplementingEngineJsonRpcInterface)
        .containsExactlyInAnyOrderElementsOf(methodsClasses);
  }
}
