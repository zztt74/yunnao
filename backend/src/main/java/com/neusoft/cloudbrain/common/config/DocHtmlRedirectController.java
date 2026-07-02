package com.neusoft.cloudbrain.common.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * /doc.html 兼容入口
 *
 * 课程任务一要求 Knife4j 可通过 /doc.html 访问 API 文档。
 * 当前项目使用 springdoc-openapi-starter-webmvc-ui，主入口为 /swagger-ui.html。
 * 引入 Knife4j 4.x 会与 springdoc-openapi-starter-webmvc-ui 产生资源注册冲突
 * （两者都注册 Swagger UI 静态资源），因此采用重定向方案：
 * - /doc.html 重定向到 /swagger-ui.html
 * - /swagger-ui.html 保留不变
 * - OpenAPI JSON (/v3/api-docs) 保留不变
 *
 * 这样满足课程展示口径，同时不破坏现有验证脚本依赖的入口。
 */
@Controller
public class DocHtmlRedirectController {

    @GetMapping("/doc.html")
    public String redirect() {
        return "redirect:/swagger-ui.html";
    }
}
