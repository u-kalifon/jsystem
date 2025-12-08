package jsystem.extensions.report.simpleHtmlReporter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Execution {

    @JsonProperty("machines")       // FIXME: machine should be a property of the test
    private List<Machine> machines;

    public List<Machine> getMachines() { return machines; }
    public void setMachines(List<Machine> machines) { this.machines = machines; }

    public static class Machine {
        @JsonProperty("type")
        private String type;

        @JsonProperty("name")
        private String name;

        @JsonProperty("children")
        private List<ExecutionScenario> children;

        public Machine() {};  // for Jackson

        public Machine(String type, String name, List<ExecutionScenario> children) {
            this.type = type;
            this.name = name;
            this.children = children;
        }

        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<ExecutionScenario> getChildren() { return children; }
        public void setChildren(List<ExecutionScenario> children) { this.children = children; }
    }

    public static class ExecutionScenario {
        @JsonProperty("type")       // FIXME: remove the use of this field
        private String type;

        @JsonProperty("scenarioProperties")     // FIXME: replace this with just 1 field: sutFile
        private Map<String, String> scenarioProperties;
    
        @JsonProperty("name")
        private String name;

        @JsonProperty("status")
        private String status;

        @JsonProperty("duration")
        private String duration;

        @JsonProperty("timestamp")
        private String timestamp;

        @JsonProperty("uid")
        private String uid;

        public ExecutionScenario() {};  // for Jackson

        public ExecutionScenario(String name, String sutFile, String timestamp, String uid) {
            this.name = name;
            this.scenarioProperties = new HashMap<>();
            this.scenarioProperties.put("sutFile", sutFile);
            this.timestamp = timestamp;
            this.status = Status.RUNNING.name();
            this.duration = "-";
            this.uid = uid;
        }

        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public Map<String, String> getScenarioProperties() { return scenarioProperties; }
        public void setScenarioProperties(Map<String, String> scenarioProperties) { this.scenarioProperties = scenarioProperties; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getDuration() { return duration; }
        public void setDuration(String duration) { this.duration = duration; }

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

        public String getUid() { return uid; }
        public void setUid(String uid) { this.uid = uid; }
    }

    // Method to find scenario by name and timestamp
    public Optional<ExecutionScenario> getScenarioByNameAndTimestamp(String name, String timestamp) {
        for (Machine machine : machines) {
            for (ExecutionScenario scenario : machine.getChildren()) {
                if (scenario.getName().equals(name) && scenario.getTimestamp().equals(timestamp)) {
                    return Optional.of(scenario);
                }
            }
        }
        return Optional.empty();
    }
}