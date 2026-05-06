package jsystem.extensions.report.simpleHtmlReporter.dto;

public enum Status {
    UNKNOWN("unknown"),     // steps initially have a status of UNKNOWN
    RUNNING("running"),     // containers initially have a status of RUNNING
    SUCCESS("success"),
    WARNING("warning"),
    ERROR("error"),
    FAILURE("failure");

    private final String value;

    Status(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public Status updateStatus(Status newStatus) {
        if (newStatus.ordinal() > ordinal()) {
            return newStatus;
        }
        return this;
    }
}