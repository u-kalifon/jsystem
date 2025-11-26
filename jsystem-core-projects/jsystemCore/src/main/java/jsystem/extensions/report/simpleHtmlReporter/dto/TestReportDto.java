package jsystem.extensions.report.simpleHtmlReporter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * DTO for test report data serialization/deserialization using Jackson.
 * Represents the complete test execution report.
 */
public class TestReportDto {

    @JsonProperty("uid")
    private String uid;

    @JsonProperty("name")
    private String name;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("description")
    private String description;

    @JsonProperty("scenarioProperties")
    private Map<String, String> scenarioProperties;

    @JsonProperty("status")
    private Status status;

    @JsonProperty("reportElements")
    private List<ReportElementDto> reportElements;

    public TestReportDto() {
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, String> getScenarioProperties() {
        return scenarioProperties;
    }

    public void setScenarioProperties(Map<String, String> scenarioProperties) {
        this.scenarioProperties = scenarioProperties;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public List<ReportElementDto> getReportElements() {
        return reportElements;
    }

    public void setReportElements(List<ReportElementDto> reportElements) {
        this.reportElements = reportElements;
    }

    @Override
    public String toString() {
        return "TestReportDto{" +
                "uid='" + uid + '\'' +
                ", name='" + name + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", description='" + description + '\'' +
                ", scenarioProperties=" + scenarioProperties +
                ", status='" + status + '\'' +
                ", reportElements=" + reportElements +
                '}';
    }
}