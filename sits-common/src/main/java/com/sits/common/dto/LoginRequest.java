package com.sits.common.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login request DTO.
 */
public class LoginRequest {

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotBlank(message = "密码不能为空")
    private String password;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
