package dev.pppp.model;

/** status: QUEUED, EXECUTED, DROPPED or OUTVOTED. position (only while QUEUED): 0 = executing now, 1 = next, ... */
public record InputStatus(String id, String status, String executedAt, Long position) {}
