package com.parsernews.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecretConfigurationFilesTest {
    @Test
    void envExampleContainsEmptySecretPlaceholdersOnly() throws Exception {
        String envExample = Files.readString(Path.of(".env.example"));

        assertThat(envExample)
                .contains("APP_ADMIN_USERNAME=")
                .contains("APP_ADMIN_PASSWORD=")
                .contains("OPENAI_API_KEY=")
                .contains("TELEGRAM_BOT_TOKEN=")
                .contains("TELEGRAM_CHAT_ID=")
                .doesNotContain("PARSERNEWS_AUTH_PASSWORD=change-me")
                .doesNotContain("PARSERNEWS_TELEGRAM_BOT_TOKEN=")
                .doesNotContain("PARSERNEWS_TELEGRAM_CHAT_ID=");
    }

    @Test
    void gitignoreKeepsEnvFilesOutOfGitExceptExample() throws Exception {
        String gitignore = Files.readString(Path.of(".gitignore"));

        assertThat(gitignore)
                .contains(".env")
                .contains(".env.*")
                .contains("!.env.example");
    }

    @Test
    void dockerComposeUsesEnvironmentInterpolationForAppSecrets() throws Exception {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose)
                .contains("APP_ADMIN_USERNAME: ${APP_ADMIN_USERNAME:-admin}")
                .contains("APP_ADMIN_PASSWORD: ${APP_ADMIN_PASSWORD:-change-me}")
                .contains("POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-parsernews}")
                .contains("SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-parsernews}")
                .contains("OPENAI_API_KEY: ${OPENAI_API_KEY:-}")
                .contains("TELEGRAM_BOT_TOKEN: ${TELEGRAM_BOT_TOKEN:-}")
                .contains("TELEGRAM_CHAT_ID: ${TELEGRAM_CHAT_ID:-}")
                .doesNotContain("PARSERNEWS_AUTH_PASSWORD: change-me")
                .doesNotContain("PARSERNEWS_TELEGRAM_BOT_TOKEN");
    }
}
