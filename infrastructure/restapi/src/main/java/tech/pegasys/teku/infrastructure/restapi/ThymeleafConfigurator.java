/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.restapi;

import io.javalin.rendering.template.JavalinThymeleaf;
import java.util.concurrent.atomic.AtomicBoolean;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

/**
 * ThymeLeaf templates configuration for Javalin. Note that Javalin renderers are configured using a
 * singleton so only the first call to this class will have any effect.
 */
class ThymeleafConfigurator {
  private static final AtomicBoolean REGISTERED = new AtomicBoolean();

  public static void enableThymeleafTemplates(final String templatePath) {
    if (!REGISTERED.compareAndSet(false, true)) {
      return;
    }
    TemplateEngine templateEngine = new TemplateEngine();
    templateEngine.addTemplateResolver(templateResolver(TemplateMode.HTML, templatePath, ".html"));
    JavalinThymeleaf.init(templateEngine);
  }

  private static ITemplateResolver templateResolver(
      final TemplateMode templateMode, final String prefix, final String suffix) {
    ClassLoaderTemplateResolver templateResolver =
        new ClassLoaderTemplateResolver(Thread.currentThread().getContextClassLoader());
    templateResolver.setTemplateMode(templateMode);
    templateResolver.setPrefix(prefix);
    templateResolver.setSuffix(suffix);
    templateResolver.setCharacterEncoding("UTF-8");
    return templateResolver;
  }
}
