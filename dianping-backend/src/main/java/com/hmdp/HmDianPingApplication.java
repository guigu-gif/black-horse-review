package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.awt.*;
import java.io.IOException;
import java.net.URI;

@MapperScan("com.hmdp.mapper")
@SpringBootApplication
public class HmDianPingApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(HmDianPingApplication.class, args);

        // 获取端口号
        String port = context.getEnvironment().getProperty("server.port", "8081");

        // 启动后自动打开浏览器
        openBrowser("http://localhost:8080");

        System.out.println("\n========================================");
        System.out.println("后端服务启动成功！");
        System.out.println("后端地址: http://localhost:" + port);
        System.out.println("前端地址: http://localhost:8080");
        System.out.println("========================================\n");
    }

    /**
     * 自动打开浏览器
     */
    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI(url));
                    System.out.println("正在打开浏览器: " + url);
                }
            } else {
                // 如果不支持 Desktop，尝试使用命令行
                String os = System.getProperty("os.name").toLowerCase();
                Runtime runtime = Runtime.getRuntime();
                if (os.contains("win")) {
                    runtime.exec("rundll32 url.dll,FileProtocolHandler " + url);
                } else if (os.contains("mac")) {
                    runtime.exec("open " + url);
                } else if (os.contains("nix") || os.contains("nux")) {
                    runtime.exec("xdg-open " + url);
                }
            }
        } catch (Exception e) {
            System.err.println("无法自动打开浏览器，请手动访问: " + url);
            e.printStackTrace();
        }
    }

}
