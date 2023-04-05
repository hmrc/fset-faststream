import java.time.{Instant, ZoneId, ZoneOffset}

Instant.now
Instant.now.atOffset(ZoneOffset.ofHours(1))
Instant.now.atZone(ZoneId.of("Europe/London"))
Instant.now.atZone(ZoneId.of("Europe/London")).toOffsetDateTime
