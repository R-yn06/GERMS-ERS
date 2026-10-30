package com.gudo.germs.models;

import java.time.Instant;

/** One line in an incident's audit trail. */
public record TimelineEntry(Instant at, String actor, String text) { }
