package com.wyb.sseat.service;

import com.wyb.sseat.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信验证码（模拟实现）。
 * 配置 sseat.sms.mock=true 时不真正发短信：
 *   验证码固定为配置的 mock-code（默认 8888），同时打印到后端控制台，
 *   答辩演示时输入 8888 即可通过，全流程与真实短信一致。
 * 若要接真实短信（阿里云/腾讯云），只需替换 send() 方法内部实现，业务代码不用动。
 */
@Slf4j
@Component
public class SmsService {

    @Value("${sseat.sms.mock}")
    private boolean mock;

    @Value("${sseat.sms.mock-code}")
    private String mockCode;

    /** 已发送未过期的码：key=手机号 */
    private final Map<String, Object[]> store = new ConcurrentHashMap<>();
    private static final long EXPIRE_MS = 5 * 60 * 1000;

    /** 发送：真实项目在这里调云厂商 API；模拟模式只记日志并把码存入内存 */
    public void send(String phone) {
        if (phone == null || !phone.matches("1\\d{10}")) {
            throw new BizException("手机号格式不正确");
        }
        String code = mock ? mockCode : String.valueOf((int) ((Math.random() * 9 + 1) * 1000));
        store.put(phone, new Object[]{code, System.currentTimeMillis() + EXPIRE_MS});
        log.info("【模拟短信】发送验证码到 {}: {}（有效期5分钟）", phone, code);
    }

    /** 校验：正确即删除，一码一次性 */
    public void verify(String phone, String code) {
        if (!mock) {
            throw new BizException("未配置真实短信服务，请在 application.yml 开启 sseat.sms.mock");
        }
        Object[] entry = store.remove(phone);
        if (entry == null || (long) entry[1] < System.currentTimeMillis()) {
            throw new BizException("短信验证码未发送或已过期");
        }
        if (!((String) entry[0]).equals(code)) {
            throw new BizException("短信验证码错误");
        }
    }
}
