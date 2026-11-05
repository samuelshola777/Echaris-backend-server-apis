package com.echaris.api.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@EnableScheduling
public class KeepRenderAwakeService {



    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Runs every 10 minutes.
     */
    @Scheduled(cron = "0 */10 * * * *")
    public void pingRenderHealthEndpoint() {
//        try {
//            String response = restTemplate.getForObject(RENDER_HEALTH_URL, String.class);
//
//            log.info("✅ Render keep-alive ping successful: {}", response);
//
//        } catch (Exception exception) {
//            log.error("❌ Render keep-alive ping failed: {}", exception.getMessage());
//        }
    }
}