package jsystem.extensions.report.simpleHtmlReporter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO for individual report element within a test report.
 * Represents a single step, log entry, or level marker in the test execution.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportElementDto {

    @JsonProperty("title")
    private String title;

    @JsonProperty("message")
    private String message;

    @JsonProperty("status")
    private Status status;

    @JsonProperty("type")
    private String type;

    @JsonProperty("properties")
    private Map<String, String> properties;

    @JsonProperty("time")
    private String time;

    @JsonProperty("userDoc")
    private String userDoc;

    public ReportElementDto() {
    }

    public static ReportElementDto newStep(String timeStamp, String title, Map<String, String> properties) {
        ReportElementDto element = new ReportElementDto();
        element.setType("step");
        element.setStatus(Status.SUCCESS);
        element.setTime(timeStamp);
        element.setTitle(title);
        element.setProperties(properties);
        return element;
    }

    public static ReportElementDto newLogLevelStart(String timeStamp, String title) {
        ReportElementDto element = new ReportElementDto();
        element.setType("startLevel");
        element.setStatus(Status.SUCCESS);
        element.setTime(timeStamp);
        element.setTitle(title);
        return element;
    }

    public static ReportElementDto newLogLevelStop(String timeStamp) {
        ReportElementDto element = new ReportElementDto();
        element.setType("stopLevel");
        element.setStatus(Status.SUCCESS);
        element.setTime(timeStamp);
        return element;
    }

    public static ReportElementDto newReportEntry(String timeStamp, String message, Status status) {
        ReportElementDto element = new ReportElementDto();
        element.setType("regular");
        element.setStatus(status);
        element.setTime(timeStamp);
        element.setTitle(message);      // TODO: use setMessage?
        return element;
    }

    public static ReportElementDto newFailureReport(String timeStamp, String message) {
        return newReportEntry(timeStamp, message, Status.FAILURE);
    }

    public static ReportElementDto newErrorReport(String timeStamp, String message) {
        return newReportEntry(timeStamp, message, Status.ERROR);
    }

    public void addProperty(String key, String value) {
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.put(key, value);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getUserDoc() {
        return userDoc;
    }

    public void setUserDoc(String userDoc) {
        this.userDoc = userDoc;
    }

    @Override
    public String toString() {
        return "ReportElementDto{" +
                "title='" + title + '\'' +
                ", message='" + message + '\'' +
                ", status='" + status + '\'' +
                ", type='" + type + '\'' +
                ", properties=" + properties +
                ", time='" + time + '\'' +
                ", userDoc='" + userDoc + '\'' +
                '}';
    }
}


