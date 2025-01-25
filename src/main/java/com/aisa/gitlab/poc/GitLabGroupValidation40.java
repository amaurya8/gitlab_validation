//package com.aisa.gitlab.main;
//
//// Main class
//package com.aisa.gitlab;
//
//import com.aisa.gitlab.utils.ExcelReportGenerator;
//import com.aisa.gitlab.utils.GitLabValidator;
//
//import java.io.IOException;
//import java.util.Arrays;
//import java.util.HashSet;
//import java.util.Set;
//import java.util.logging.Logger;
//
//public class GitLabGroupValidation {
//
//    private static final Logger LOGGER = Logger.getLogger(GitLabGroupValidation.class.getName());
//
//    public static void main(String[] args) throws IOException {
//        LOGGER.info("Starting GitLab Group Validation...");
//
//        // Set of group IDs to validate
//        Set<Integer> groupIds = new HashSet<>(Arrays.asList(12345, 67890)); // Replace with actual group IDs
//
//        // Perform validation for all groups
//        for (int groupId : groupIds) {
//            LOGGER.info("Validating group with ID: " + groupId);
//            GitLabValidator4o.validateGroup(groupId);
//        }
//
//        // Generate the Excel report
//        ExcelReportGenerator.generateReport();
//        LOGGER.info("GitLab Group Validation completed.");
//    }
//}