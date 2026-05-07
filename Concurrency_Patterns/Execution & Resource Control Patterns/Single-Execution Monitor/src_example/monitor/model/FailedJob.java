package com.monitor.model;

public record FailedJob(Integer ordinalNumber, Integer instanceId, String reason) {

}
