package jsystem.extensions.report.simpleHtmlReporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import jsystem.extensions.report.simpleHtmlReporter.dto.ReportElementDto;
import jsystem.extensions.report.simpleHtmlReporter.dto.TestReportDto;

import java.io.File;
import java.io.IOException;

/**
 * Utility class for serializing and deserializing test reports using Jackson.
 * Provides convenient methods for converting between TestReportDto objects and JSON.
 */
public class JsonReportSerializer {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Deserializes a JSON string into a TestReportDto object.
     *
     * @param json the JSON string to deserialize
     * @return the TestReportDto object
     * @throws IOException if deserialization fails
     */
    public static TestReportDto fromJson(String json) throws IOException {
        return objectMapper.readValue(json, TestReportDto.class);
    }

    /**
     * Deserializes a JSON file into a TestReportDto object.
     *
     * @param file the JSON file to deserialize
     * @return the TestReportDto object
     * @throws IOException if deserialization fails
     */
    public static TestReportDto fromJsonFile(File file) throws IOException {
        return objectMapper.readValue(file, TestReportDto.class);
    }

    /**
     * Serializes a TestReportDto object into a JSON string.
     *
     * @param report the TestReportDto object to serialize
     * @return the JSON string representation
     * @throws IOException if serialization fails
     */
    public static String toJson(TestReportDto report) throws IOException {
        return objectMapper.writeValueAsString(report);
    }

    /**
     * Serializes a ReportElementDto object into a JSON string.
     *
     * @param element the ReportElementDto object to serialize
     * @return the JSON string representation
     * @throws IOException if serialization fails
     */
    public static String toJson(ReportElementDto element) throws IOException {
        return objectMapper.writeValueAsString(element);
    }

    /**
     * Returns the ObjectMapper instance used by this utility class.
     * Can be used for custom configuration or advanced operations.
     *
     * @return the ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}

