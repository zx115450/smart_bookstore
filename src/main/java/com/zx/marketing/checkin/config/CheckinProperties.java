package com.zx.marketing.checkin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "checkin")
public class CheckinProperties {

    private String frontendBaseUrl = "http://localhost:5173";
    private int streakRewardDays = 7;
    private int windowMinutesBefore = 15;
    private int windowMinutesAfter = 15;
}
