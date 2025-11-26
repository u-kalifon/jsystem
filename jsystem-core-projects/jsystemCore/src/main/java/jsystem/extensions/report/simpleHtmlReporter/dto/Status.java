package jsystem.extensions.report.simpleHtmlReporter.dto;

public enum Status {
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
}

