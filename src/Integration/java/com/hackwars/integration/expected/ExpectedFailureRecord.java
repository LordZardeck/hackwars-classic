package com.hackwars.integration.expected;

public final class ExpectedFailureRecord {
    private final String status;
    private final String testName;
    private final String reason;
    private final String detail;

    public ExpectedFailureRecord(String status, String testName, String reason, String detail) {
        this.status = status;
        this.testName = testName;
        this.reason = reason;
        this.detail = detail;
    }

    public String toTsv() {
        return escape(status) + "\t" + escape(testName) + "\t" + escape(reason) + "\t" + escape(detail);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ');
    }
}
