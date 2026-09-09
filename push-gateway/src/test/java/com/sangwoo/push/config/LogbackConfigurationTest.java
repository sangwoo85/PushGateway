package com.sangwoo.push.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.helper.FileNamePattern;
import ch.qos.logback.core.status.Status;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogbackConfigurationTest {
    @TempDir Path directory;

    @Test
    void rollsDailyToGzipAndPreservesCurrentFileAndEventId() throws Exception {
        LoggerContext context = configure();
        try {
            var appender = fileAppender(context);
            var policy = (TimeBasedRollingPolicy<ILoggingEvent>) appender.getRollingPolicy();
            var clock = policy.getTimeBasedFileNamingAndTriggeringPolicy();
            String archivedPath = clock.getCurrentPeriodsFileNameWithoutCompressionSuffix() + ".gz";
            appender.doAppend(event(context, "이전 날짜 로그", "event-before"));
            long nextDay = Instant.now().atZone(ZoneId.of("Asia/Seoul"))
                    .toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul"))
                    .toInstant().toEpochMilli() + 1000;
            clock.setCurrentTime(nextDay);
            appender.doAppend(event(context, "새 날짜 로그", "event-after"));
            // stop waits for the asynchronous gzip job to finish.
            policy.stop();
            appender.stop();

            assertThat(Path.of(archivedPath)).exists();
            try (var gzip = new GZIPInputStream(Files.newInputStream(Path.of(archivedPath)))) {
                assertThat(new String(gzip.readAllBytes(), StandardCharsets.UTF_8))
                        .contains("이전 날짜 로그", "[eventId:event-before]")
                        .doesNotContain("새 날짜 로그");
            }
            assertThat(Files.readString(directory.resolve("push-gateway.log")))
                    .contains("새 날짜 로그", "[eventId:event-after]")
                    .doesNotContain("이전 날짜 로그");
            assertThat(Path.of(archivedPath.substring(0, archivedPath.length() - 3))).doesNotExist();
            assertThat(policy.getMaxHistory()).isZero();
            assertThat(context.getStatusManager().getCopyOfStatusList())
                    .noneMatch(status -> status.getEffectiveLevel() >= Status.ERROR);
        } finally {
            context.stop();
        }
    }

    @Test
    void archiveFoldersFollowKoreanDateAcrossMonthAndYearBoundaries() throws Exception {
        LoggerContext context = configure();
        try {
            var policy = (TimeBasedRollingPolicy<ILoggingEvent>) fileAppender(context).getRollingPolicy();
            var pattern = new FileNamePattern(policy.getFileNamePattern(), context);
            assertThat(pattern.convert(Date.from(Instant.parse("2026-12-31T14:59:59Z"))))
                    .endsWith("/backup/2026/12/push-gateway.2026-12-31.log.gz");
            assertThat(pattern.convert(Date.from(Instant.parse("2026-12-31T15:00:00Z"))))
                    .endsWith("/backup/2027/01/push-gateway.2027-01-01.log.gz");
            assertThat(pattern.getPrimaryDateTokenConverter().getDatePattern()).isEqualTo("yyyy-MM-dd");
        } finally {
            context.stop();
        }
    }

    private LoggerContext configure() throws Exception {
        var context = new LoggerContext();
        context.putProperty("PUSH_LOG_DIR", directory.toString());
        var configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(getClass().getResource("/logback-spring.xml"));
        return context;
    }

    @SuppressWarnings("unchecked")
    private RollingFileAppender<ILoggingEvent> fileAppender(LoggerContext context) {
        return (RollingFileAppender<ILoggingEvent>) context.getLogger("ROOT").getAppender("GATEWAY_FILE");
    }

    private LoggingEvent event(LoggerContext context, String message, String eventId) {
        var event = new LoggingEvent(getClass().getName(), context.getLogger("test"),
                Level.INFO, message, null, null);
        event.setMDCPropertyMap(Map.of("eventId", eventId));
        return event;
    }
}
