package com.wyb.sseat.service;

import com.wyb.sseat.common.BizException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码：用 Java 自带画图功能生成 4 位字符图片。
 * 答案存在服务器内存里（Map），5 分钟过期、一次性使用。
 * 之所以不用 Redis：本项目单机部署，内存存储足够，也少装一个中间件。
 */
@Component
public class CaptchaService {

    /** 去掉了 0/O/1/I 这类容易看错的字符 */
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final long EXPIRE_MS = 5 * 60 * 1000;

    /** key=验证码编号, value=[答案, 过期时间戳] */
    private final Map<String, Object[]> store = new ConcurrentHashMap<>();

    public record CaptchaImage(String captchaKey, String imgBase64, String devCode) {}

    /** 生成验证码图片，返回 编号 + base64 图片（前端直接当 <img src> 用）。
     *  devCode：仅开发模式(debug-reveal=true)返回明文答案，方便脚本测试；上线前置 false */
    public CaptchaImage generate(boolean reveal) {
        StringBuilder code = new StringBuilder();
        BufferedImage img = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(240, 245, 255));
        g.fillRect(0, 0, 120, 40);
        // 干扰线
        for (int i = 0; i < 6; i++) {
            g.setColor(randomColor(150, 220));
            g.drawLine((int) (Math.random() * 120), (int) (Math.random() * 40),
                       (int) (Math.random() * 120), (int) (Math.random() * 40));
        }
        // 字符：随机颜色、随机倾斜（画完每个字符恢复画笔状态）
        g.setFont(new Font("Arial", Font.BOLD, 28));
        for (int i = 0; i < 4; i++) {
            char c = CHARS.charAt((int) (Math.random() * CHARS.length()));
            code.append(c);
            g.setColor(randomColor(20, 120));
            AffineTransform old = g.getTransform();
            g.rotate(Math.random() * 0.4 - 0.2, 15 + i * 28, 30);
            g.drawString(String.valueOf(c), 12 + i * 28, 30);
            g.setTransform(old);
        }
        g.dispose();

        String key = UUID.randomUUID().toString().replace("-", "");
        store.put(key, new Object[]{code.toString(), System.currentTimeMillis() + EXPIRE_MS});
        cleanExpired();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            String b64 = Base64.getEncoder().encodeToString(out.toByteArray());
            return new CaptchaImage(key, "data:image/png;base64," + b64, reveal ? code.toString() : null);
        } catch (Exception e) {
            throw new BizException("验证码生成失败");
        }
    }

    /** 校验验证码：忽略大小写；无论对错都立即删除（防止暴力重试同一个码） */
    public void verify(String captchaKey, String input) {
        Object[] entry = store.remove(captchaKey);
        if (entry == null || (long) entry[1] < System.currentTimeMillis()) {
            throw new BizException("验证码已过期，请点击图片刷新");
        }
        if (!((String) entry[0]).equalsIgnoreCase(input == null ? "" : input.trim())) {
            throw new BizException("验证码错误");
        }
    }

    private Color randomColor(int min, int max) {
        int r = min + (int) (Math.random() * (max - min));
        int g = min + (int) (Math.random() * (max - min));
        int b = min + (int) (Math.random() * (max - min));
        return new Color(r, g, b);
    }

    /** 顺手清掉过期条目，防止 Map 无限增长 */
    private void cleanExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> (long) e.getValue()[1] < now);
    }
}
