/*
 * Copyright 2026 - 2026 Dmitri Snytkine. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.snytkine.apitester.api_tester_cli.config;

import java.util.Properties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;

/**
 * Fallback auto-configuration that provides a {@link BuildProperties} bean when
 * {@code META-INF/build-info.properties} is absent from the classpath.
 *
 * <p>The {@code spring-boot-maven-plugin}'s {@code build-info} goal writes that file into
 * {@code target/classes} during the Maven {@code generate-resources} phase. Spring Boot's
 * {@link ProjectInfoAutoConfiguration} reads it to create the primary {@link BuildProperties} bean.
 * When the file is absent (e.g. when the Maven plugin goal was never run), no {@link
 * BuildProperties} bean is created and any component that requires it will prevent the context from
 * starting.
 *
 * <p>This auto-configuration is ordered <em>after</em> {@link ProjectInfoAutoConfiguration} so
 * that its {@link ConditionalOnMissingBean} condition evaluates only when the primary bean was not
 * created. When {@code build-info.properties} exists, the primary bean takes precedence and this
 * fallback is never registered.
 *
 * <p>Thread-safety: this class is a stateless Spring configuration and is safe for concurrent use.
 */
@AutoConfiguration(after = ProjectInfoAutoConfiguration.class)
public class BuildInfoConfiguration {

    /**
     * Fallback {@link BuildProperties} bean used only when {@code META-INF/build-info.properties} is
     * absent from the classpath (e.g. during {@code mvn spring-boot:run} without a prior build).
     * Reports the version as {@code "unknown"}.
     *
     * @return a {@link BuildProperties} instance with version set to {@code "unknown"}
     */
    @Bean
    @ConditionalOnMissingBean(BuildProperties.class)
    public BuildProperties fallbackBuildProperties() {
        Properties props = new Properties();
        props.setProperty("version", "unknown");
        return new BuildProperties(props);
    }
}
