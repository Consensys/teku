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

package tech.pegasys.teku.testutils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SpecTestInvocationContextProvider implements TestTemplateInvocationContextProvider {
  @Override
  public boolean supportsTestTemplate(ExtensionContext extensionContext) {
    return true;
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      ExtensionContext extensionContext) {
    return Arrays.stream(SpecMilestone.values())
        .map(specMilestone -> generateContext(new SpecContext(specMilestone)));
  }

  private TestTemplateInvocationContext generateContext(SpecContext specContext) {
    return new TestTemplateInvocationContext() {
      @Override
      public String getDisplayName(int invocationIndex) {
        return specContext.getDisplayName();
      }

      @Override
      public List<Extension> getAdditionalExtensions() {
        return List.of(new GenericTypedParameterResolver(specContext));
      }
    };
  }

  public static class SpecContext {
    private final String displayName;
    private final Spec spec;
    private final DataStructureUtil dataStructureUtil;

    SpecContext(SpecMilestone specMilestone) {
      spec = TestSpecFactory.createMinimal(specMilestone);
      dataStructureUtil = new DataStructureUtil(spec);
      displayName = specMilestone.name();
    }

    public String getDisplayName() {
      return displayName;
    }

    public Spec getSpec() {
      return spec;
    }

    public DataStructureUtil getDataStructureUtil() {
      return dataStructureUtil;
    }
  }

  public static class GenericTypedParameterResolver<T> implements ParameterResolver {
    T data;

    public GenericTypedParameterResolver(T data) {
      this.data = data;
    }

    @Override
    public boolean supportsParameter(
        ParameterContext parameterContext, ExtensionContext extensionContext)
        throws ParameterResolutionException {
      return parameterContext.getParameter().getType().isInstance(data);
    }

    @Override
    public Object resolveParameter(
        ParameterContext parameterContext, ExtensionContext extensionContext)
        throws ParameterResolutionException {
      return data;
    }
  }
}
