package com.aiproxy.common.constants;

import java.util.HashMap;
import java.util.Map;

public class ClaudeErrorCode {
    
    private static final Map<String, String> ERROR_DESCRIPTIONS = new HashMap<>();
    
    static {
        // 4xx 客户端错误
        ERROR_DESCRIPTIONS.put("400", "请求格式错误：您的请求格式或内容存在问题，可能是JSON格式错误、缺少必需字段或参数值不符合要求");
        ERROR_DESCRIPTIONS.put("invalid_request_error", "无效请求：请求格式或内容不符合API要求");
        ERROR_DESCRIPTIONS.put("401", "认证失败：您的API密钥无效或已过期");
        ERROR_DESCRIPTIONS.put("authentication_error", "认证错误：API密钥存在问题");
        ERROR_DESCRIPTIONS.put("403", "权限不足：您的API密钥没有使用指定资源的权限");
        ERROR_DESCRIPTIONS.put("permission_error", "权限错误：API密钥没有使用指定资源的权限");
        ERROR_DESCRIPTIONS.put("404", "资源未找到：未找到请求的资源");
        ERROR_DESCRIPTIONS.put("not_found_error", "未找到请求的资源");
        ERROR_DESCRIPTIONS.put("413", "请求过大：请求超过了允许的最大字节数（标准API端点的最大请求大小为32MB）");
        ERROR_DESCRIPTIONS.put("request_too_large", "请求过大：超过了允许的最大字节数");
        ERROR_DESCRIPTIONS.put("429", "请求频率超限：您的账户已达到了速率限制");
        ERROR_DESCRIPTIONS.put("rate_limit_error", "速率限制：请求频率超过了限制");
        
        // 5xx 服务器错误
        ERROR_DESCRIPTIONS.put("500", "服务器内部错误：Anthropic系统内部发生了意外错误");
        ERROR_DESCRIPTIONS.put("api_error", "API错误：Anthropic系统内部发生了意外错误");
        ERROR_DESCRIPTIONS.put("529", "服务过载：Anthropic的API暂时过载");
        ERROR_DESCRIPTIONS.put("overloaded_error", "服务过载：API暂时过载，请稍后重试");
        
        // 其他常见错误
        ERROR_DESCRIPTIONS.put("invalid_api_key", "无效的API密钥：提供的API密钥格式不正确或不存在");
        ERROR_DESCRIPTIONS.put("model_not_found", "模型未找到：请求的模型不存在或您没有访问权限");
        ERROR_DESCRIPTIONS.put("context_length_exceeded", "上下文长度超限：消息内容超过了模型的最大上下文长度");
        ERROR_DESCRIPTIONS.put("timeout", "请求超时：请求处理时间过长");
        ERROR_DESCRIPTIONS.put("invalid_json", "JSON格式错误：请求体不是有效的JSON格式");
        ERROR_DESCRIPTIONS.put("missing_required_parameter", "缺少必需参数：请求中缺少必需的参数");
        ERROR_DESCRIPTIONS.put("invalid_parameter_value", "参数值无效：某个参数的值不符合要求");
    }
    
    public static String getDescription(String errorCode) {
        if (errorCode == null) {
            return "未知错误";
        }
        
        // 先尝试直接匹配
        String description = ERROR_DESCRIPTIONS.get(errorCode.toLowerCase());
        if (description != null) {
            return description;
        }
        
        // 尝试提取HTTP状态码
        if (errorCode.matches("\\d{3}.*")) {
            String statusCode = errorCode.substring(0, 3);
            description = ERROR_DESCRIPTIONS.get(statusCode);
            if (description != null) {
                return description;
            }
        }
        
        // 尝试匹配错误类型
        for (Map.Entry<String, String> entry : ERROR_DESCRIPTIONS.entrySet()) {
            if (errorCode.toLowerCase().contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return "未知错误：" + errorCode;
    }
    
    public static String formatErrorMessage(int statusCode, String errorType, String errorMessage, String requestBody) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n==================== Claude API 错误详情 ====================\n");
        sb.append("状态码: ").append(statusCode).append("\n");
        sb.append("错误类型: ").append(errorType != null ? errorType : "未知").append("\n");
        sb.append("错误说明: ").append(getDescription(String.valueOf(statusCode))).append("\n");
        sb.append("错误消息: ").append(errorMessage != null ? errorMessage : "无").append("\n");
        sb.append("时间戳: ").append(new java.util.Date()).append("\n");
        sb.append("请求体: ").append(requestBody != null ? requestBody : "无").append("\n");
        sb.append("=============================================================\n");
        return sb.toString();
    }
}