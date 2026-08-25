package com.limiteddrop.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InfoResponse {
    private Long dropId;
    private String status;       // SCHEDULED / OPEN / ENDED
    private Long remaining;      // OPEN 时有效
    private Integer stock;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
