package com.aisa.gitlab.main;


import com.google.gson.Gson;
import io.restassured.RestAssured;

import java.util.Set;
import java.util.logging.Logger;

public class GitLabGroupValidator {

    private static final Logger LOGGER = Logger.getLogger(GitLabGroupValidator.class.getName());
    private static final String GITLAB_API_BASE_URL = "https://gitlab.com/api/v4";
    private static final String PRIVATE_TOKEN = "your_personal_access_token";

    public void validateGroups(Set<Integer> groupIds) {
        for (int groupId : groupIds) {
            LOGGER.info("Validating group with ID: " + groupId);
            validateGroup(groupId);
        }
    }

    private void validateGroup(int groupId) {
        RestAssured.baseURI = GITLAB_API_BASE_URL;
        Gson gson = new Gson();

        LOGGER.info("Validating epics under the group...");
        EpicValidator epicValidator = new EpicValidator();
        epicValidator.validateEpicsUnderGroup(groupId, gson);

        LOGGER.info("Validating issues in projects under the group...");
        IssueValidator issueValidator = new IssueValidator();
        issueValidator.validateIssuesInProjectsUnderGroup(groupId, gson);
    }
}