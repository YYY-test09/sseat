package com.wyb.sseat.common;

/**
 * 业务异常：代码里遇到"密码错误""验证码错误"等情况就抛出它，
 * 由 GlobalExceptionHandler 统一捕获并转成友好提示返回给前端。
 */
public class BizException extends RuntimeException {
    public BizException(String message) {
        super(message);
    }
}
