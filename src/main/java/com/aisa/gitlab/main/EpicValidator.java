package com.aisa.gitlab.main;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EpicValidator {

    private static final Logger LOGGER = Logger.getLogger(EpicValidator.class.getName());
    private static final String PRIVATE_TOKEN = "your_personal_access_token";
    public static final List<Map<String, String>> epicFailures = new ArrayList<>();
    public static final List<Map<String, String>> crewDeliveryEpics = new ArrayList<>();
    private static final LocalDate DATE_THRESHOLD = LocalDate.parse("2025-01-01");

    public void validateEpicsUnderGroup(int groupId, Gson gson) {
        int currentPage = 1;
        int perPage = 50;
        boolean hasMorePages = true;

        // Map to store all epics across pages
        Map<Integer, JsonObject> allEpics = new HashMap<>();

        // Step 1: Fetch all epics and accumulate them in allEpics
        while (hasMorePages) {
            LOGGER.log(Level.INFO, "Fetching epics - Page: {0}", currentPage);
            RequestSpecification epicRequest = RestAssured.given()
                    .header("PRIVATE-TOKEN", PRIVATE_TOKEN)
                    .queryParam("page", currentPage)
                    .queryParam("per_page", perPage);

            Response epicResponse = epicRequest.get("/groups/" + groupId + "/epics");

            if (epicResponse.getStatusCode() != 200) {
                LOGGER.log(Level.SEVERE, "Failed to fetch epics. HTTP Error Code: {0}", epicResponse.getStatusCode());
                return;
            }

            JsonArray epics = gson.fromJson(epicResponse.getBody().asString(), JsonArray.class);
            LOGGER.log(Level.INFO, "Number of epics fetched: {0}", epics.size());

            if (epics.size() == 0) {
                hasMorePages = false;
                break;
            }

            // Accumulate epics from the current page into the map
            for (JsonElement epicElement : epics) {
                JsonObject epic = epicElement.getAsJsonObject();
                int epicId = epic.get("id").getAsInt();
                allEpics.put(epicId, epic); // Store epic from the current page
            }

            // Check if there's another page of results
            String nextPageLink = epicResponse.getHeader("X-Next-Page");
            hasMorePages = (nextPageLink != null && !nextPageLink.isEmpty());
            currentPage++;
        }

        // Step 2: Perform validation after accumulating all epics
        for (Map.Entry<Integer, JsonObject> entry : allEpics.entrySet()) {
            JsonObject epic = entry.getValue();
            int epicId = epic.get("id").getAsInt();
            String epicLink = epic.get("web_url").getAsString();
            String state = epic.get("state").getAsString(); // Open or Closed
            String closedAt = epic.has("closed_at") && !epic.get("closed_at").isJsonNull()
                    ? epic.get("closed_at").getAsString() : null;
            String epicCreatedBy = epic.has("author") && epic.get("author").getAsJsonObject().has("name")
                    ? epic.get("author").getAsJsonObject().get("name").getAsString()
                    : "Unknown"; // Fetch the creator's name, default to "Unknown" if not present

            // Perform start and due date check
            boolean hasStartDate = epic.has("start_date") && !epic.get("start_date").isJsonNull();
            boolean hasDueDate = epic.has("due_date") && !epic.get("due_date").isJsonNull();

//            if ("opened".equalsIgnoreCase(state) & !hasStartDate || !hasDueDate) {
//                logEpicFailure(epicId, epicLink, "Open Epic Missing start and/or due date",epicCreatedBy);
//            }

            if (state.equalsIgnoreCase("opened")) {
                if (!hasStartDate || !hasDueDate) {
                    logEpicFailure(epicId, epicLink, "Open Epic Missing start and/or due date", epicCreatedBy);
                }
            }

            // Perform Crew Delivery Epic check based on state
            if ("opened".equalsIgnoreCase(state)) {
                validateCrewDeliveryEpic(epic, epicId, epicLink, allEpics);
            } else if ("closed".equalsIgnoreCase(state) && isClosedOnOrAfterThreshold(closedAt)) {
                validateCrewDeliveryEpic(epic, epicId, epicLink, allEpics);
            }
        }
    }

    private boolean isClosedOnOrAfterThreshold(String closedAt) {
        if (closedAt == null) {
            return false;
        }
        LocalDate closedDate = LocalDate.parse(closedAt, DateTimeFormatter.ISO_DATE_TIME);
        return closedDate.isAfter(DATE_THRESHOLD.minusDays(1)); // After or on 2025-01-01
    }

    private void validateCrewDeliveryEpic(JsonObject epic, int epicId, String epicLink, Map<Integer, JsonObject> allEpics) {
        // Check if the epic has the label "Crew Delivery Epic"
        boolean isCrewDeliveryEpic = epic.has("labels") && containsLabel(epic.getAsJsonArray("labels"), "Crew Delivery Epic");
        if (isCrewDeliveryEpic) {
            logCrewDeliveryEpic(epicId, epicLink);
        } else {
            String epicCreatedBy = epic.has("author") && epic.get("author").getAsJsonObject().has("name")
                    ? epic.get("author").getAsJsonObject().get("name").getAsString()
                    : "Unknown"; // Fetch the creator's name, fallback to "Unknown" if not present
            logEpicFailure(epicId, epicLink, "Neither labeled as Crew Delivery Epic nor part of a Crew Delivery Epic hierarchy",epicCreatedBy);
        }
    }

    private boolean containsLabel(JsonArray labels, String targetLabel) {
        for (JsonElement label : labels) {
            if (label.getAsString().equalsIgnoreCase(targetLabel)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPartOfCrewDeliveryEpic(JsonObject epic, Map<Integer, JsonObject> allEpics) {
        while (epic != null) {
            // Check if the current epic has the "Crew Delivery Epic" label
            if (epic.has("labels") && containsLabel(epic.getAsJsonArray("labels"), "Crew Delivery Epic")) {
                return true;
            }

            // Move to the parent epic if available
            if (epic.has("parent_id") && !epic.get("parent_id").isJsonNull()) {
                int parentId = epic.get("parent_id").getAsInt();
                epic = allEpics.get(parentId); // Fetch the parent epic from the map
            } else {
                epic = null; // No parent, end the loop
            }
        }

        return false; // No "Crew Delivery Epic" label found in the hierarchy
    }

    private void logCrewDeliveryEpic(int epicId, String epicLink) {
        Map<String, String> crewEpic = new HashMap<>();
        crewEpic.put("epic_id", String.valueOf(epicId));
        crewEpic.put("epic_link", epicLink);
        crewDeliveryEpics.add(crewEpic);
        LOGGER.log(Level.INFO, "Crew Delivery Epic found: {0}", epicLink);
    }

    private void logEpicFailure(int epicId, String epicLink, String message, String epicCreatedBy) {
        String pod = extractPod(epicLink); // Extract POD from the link

        Map<String, String> failure = new HashMap<>();
        failure.put("epic_id", String.valueOf(epicId));
        failure.put("epic_link", epicLink);
        failure.put("failure_message", message);
        failure.put("epic_created_by", epicCreatedBy);
        failure.put("pod", pod); // Add POD information

        epicFailures.add(failure);

        LOGGER.log(Level.WARNING, "Epic validation failure: {0} - {1} - Created By: {2} - POD: {3}",
                new Object[]{epicLink, message, epicCreatedBy, pod});
    }

    private String extractPod(String epicLink) {
        String prefix = "https://www.aisa-automations.com/xyx/mr123rg5/"; // Common prefix
        if (epicLink.startsWith(prefix)) {
            String remainingPath = epicLink.substring(prefix.length()); // Remove prefix
            String[] parts = remainingPath.split("/"); // Split by '/'
            if (parts.length > 1) {
                return parts[1]; // Second segment after the common prefix
            }
        }
        return "Unknown"; // Default if no match
    }
}