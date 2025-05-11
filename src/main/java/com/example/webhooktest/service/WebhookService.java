package com.example.webhooktest.service;

import com.example.webhooktest.model.WebhookRequest;
import com.example.webhooktest.model.WebhookResponse;
import com.example.webhooktest.model.SolutionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WebhookService {

    @Value("${webhook.generate.url}")
    private String generateWebhookUrl;

    @Value("${webhook.test.url}")
    private String testWebhookUrl;

    @Autowired
    private RestTemplate restTemplate;

    public void initializeWebhook() {
        try {
            // Create initial request
            WebhookRequest request = new WebhookRequest();
            request.setName("John Doe");
            request.setRegNo("REG12347");
            request.setEmail("john@example.com");

            // Send POST request to generate webhook
            WebhookResponse response = restTemplate.postForObject(
                generateWebhookUrl,
                new HttpEntity<>(request, createHeaders()),
                WebhookResponse.class
            );

            if (response != null) {
                // Get the SQL query based on registration number
                String sqlQuery = getSqlQuery(request.getRegNo());

                // Send solution to webhook URL
                sendSolutionToWebhook(response.getWebhook(), response.getAccessToken(), sqlQuery);
            }
        } catch (Exception e) {
            log.error("Error in webhook initialization: ", e);
        }
    }

    private String getSqlQuery(String regNo) {
        // Extract last two digits of registration number
        String lastTwoDigits = regNo.substring(regNo.length() - 2);
        int number = Integer.parseInt(lastTwoDigits);
        return """
                WITH EmployeePayments AS (
                    SELECT 
                        e.EMP_ID,
                        e.FIRST_NAME,
                        e.LAST_NAME,
                        e.DOB,
                        e.GENDER,
                        d.DEPARTMENT_NAME,
                        COUNT(p.PAYMENT_ID) as PAYMENT_COUNT,
                        AVG(p.AMOUNT) as AVG_SALARY
                    FROM EMPLOYEE e
                    JOIN DEPARTMENT d ON e.DEPARTMENT = d.DEPARTMENT_ID
                    LEFT JOIN PAYMENTS p ON e.EMP_ID = p.EMP_ID
                    GROUP BY e.EMP_ID, e.FIRST_NAME, e.LAST_NAME, e.DOB, e.GENDER, d.DEPARTMENT_NAME
                )
                SELECT 
                    CONCAT(FIRST_NAME, ' ', LAST_NAME) as EMPLOYEE_NAME,
                    TIMESTAMPDIFF(YEAR, DOB, CURRENT_DATE) as AGE,
                    GENDER,
                    DEPARTMENT_NAME,
                    PAYMENT_COUNT,
                    ROUND(AVG_SALARY, 2) as AVERAGE_SALARY
                FROM EmployeePayments
                WHERE PAYMENT_COUNT >= 2
                ORDER BY AVG_SALARY DESC
                LIMIT 3;
                """;
        
        } 
    

    private void sendSolutionToWebhook(String webhookUrl, String token, String sqlQuery) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        SolutionRequest request = new SolutionRequest();
        request.setFinalQuery(sqlQuery);

        HttpEntity<SolutionRequest> entity = new HttpEntity<>(request, headers);
        restTemplate.postForObject(webhookUrl, entity, String.class);
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
} 