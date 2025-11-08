package io.github.hexlodev.ui.monitor.dto;

import lombok.Data;

/**
 * 统一 API 响应格式
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private Integer code;

    public static <T> ApiResponse<T> success(T data) {
        return success(data, "操作成功");
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setData(data);
        response.setMessage(message);
        response.setCode(200);
        return response;
    }

    public static <T> ApiResponse<T> error(String message) {
        return error(500, message);
    }

    public static <T> ApiResponse<T> error(Integer code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage(message);
        response.setCode(code);
        return response;
    }
}

