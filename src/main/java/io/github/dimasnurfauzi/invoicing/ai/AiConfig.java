package io.github.dimasnurfauzi.invoicing.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {}
