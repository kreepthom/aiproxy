package com.aiproxy.common.utils;

import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * 错误分类器，用于判断错误是否应该重试
 */
public class ErrorClassifier {
    
    /**
     * 判断错误是否可以通过更换账号来解决（可重试）
     * 
     * @param error 错误对象
     * @return true 如果错误可能因账号问题导致，可以重试；false 如果错误与账号无关，不应重试
     */
    public static boolean isRetryableError(Throwable error) {
        // 如果不是 WebClient 响应异常，可能是网络问题，应该重试
        if (!(error instanceof WebClientResponseException)) {
            return true;  // 网络错误、超时等问题，可以重试
        }
        
        WebClientResponseException responseException = (WebClientResponseException) error;
        int statusCode = responseException.getStatusCode().value();
        
        // 根据状态码判断是否应该重试
        switch (statusCode) {
            // 可重试的错误（可能因账号问题导致）
            case 401:  // 认证失败 - 可能是账号 token 失效
            case 403:  // 权限不足 - 可能是账号权限问题
            case 429:  // 频率限制 - 换账号可能解决
            case 500:  // 服务器内部错误 - 可能是临时问题
            case 502:  // 网关错误 - 临时问题
            case 503:  // 服务不可用 - 临时问题
            case 504:  // 网关超时 - 临时问题
            case 529:  // 服务过载 - 换账号可能减轻负载
                return true;
                
            // 不应重试的错误（与账号无关）
            case 400:  // 请求格式错误 - 换账号也无法解决
            case 404:  // 资源未找到 - 换账号也找不到
            case 405:  // 方法不允许 - API 端点问题
            case 406:  // 不可接受 - 请求头问题
            case 409:  // 冲突 - 业务逻辑冲突
            case 410:  // 已删除 - 资源永久删除
            case 413:  // 请求过大 - 换账号也无法解决
            case 415:  // 不支持的媒体类型 - 请求格式问题
            case 422:  // 无法处理的实体 - 业务逻辑错误
                return false;
                
            default:
                // 其他 4xx 错误通常是客户端问题，不应重试
                if (statusCode >= 400 && statusCode < 500) {
                    return false;
                }
                // 其他 5xx 错误通常是服务器问题，可以重试
                if (statusCode >= 500 && statusCode < 600) {
                    return true;
                }
                // 未知错误，保守起见不重试
                return false;
        }
    }
    
    /**
     * 获取错误的中文描述
     */
    public static String getErrorDescription(Throwable error) {
        if (!(error instanceof WebClientResponseException)) {
            return "网络连接错误或超时";
        }
        
        WebClientResponseException responseException = (WebClientResponseException) error;
        int statusCode = responseException.getStatusCode().value();
        
        switch (statusCode) {
            case 400: return "请求格式错误（换账号无法解决）";
            case 401: return "认证失败（可能是账号问题）";
            case 403: return "权限不足（可能是账号权限问题）";
            case 404: return "资源未找到（换账号无法解决）";
            case 413: return "请求内容过大（换账号无法解决）";
            case 429: return "请求频率超限（可以尝试其他账号）";
            case 500: return "服务器内部错误（可以重试）";
            case 529: return "服务过载（可以尝试其他账号）";
            default: return "HTTP " + statusCode + " 错误";
        }
    }
}