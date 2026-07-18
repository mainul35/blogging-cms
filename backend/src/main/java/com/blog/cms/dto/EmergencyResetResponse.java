package com.blog.cms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EmergencyResetResponse {
    private String email;
    private String newPassword;
}
