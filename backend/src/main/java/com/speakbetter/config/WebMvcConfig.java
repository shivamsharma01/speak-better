package com.speakbetter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String FALLBACK_HTML = """
        <!DOCTYPE html>
        <html><head><meta charset="utf-8"><title>Speak Better</title></head>
        <body style="font-family:sans-serif;max-width:520px;margin:4rem auto;padding:1rem;">
        <h1>Frontend not built</h1>
        <p>Build the Angular app so this server can serve it on one port:</p>
        <pre style="background:#eee;padding:1rem;border-radius:8px;">cd frontend
        npm install
        npm run build
        ./build.sh</pre>
        <p>Production build is copied to <code>backend/src/main/resources/static/</code>.</p>
        <p>API is still available at <code>/api/transcribe</code> and <code>/api/rephrase</code>.</p>
        </body></html>
        """;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/static/browser/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        if (isApiLikePath(resourcePath)) {
                            return null;
                        }

                        Resource requestedResource = location.createRelative(resourcePath);
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }

                        if (resourcePath.contains(".")) {
                            return null;
                        }

                        Resource indexHtml = new ClassPathResource("/static/index.html");
                        if (!indexHtml.exists() || !indexHtml.isReadable()) {
                            indexHtml = new ClassPathResource("/static/browser/index.html");
                        }
                        if (indexHtml.exists() && indexHtml.isReadable()) {
                            return indexHtml;
                        }

                        if (resourcePath.isEmpty() || "/".equals(resourcePath) || "index.html".equals(resourcePath)) {
                            return new InMemoryHtmlResource(FALLBACK_HTML.getBytes(StandardCharsets.UTF_8));
                        }
                        return null;
                    }
                });
    }

    private static boolean isApiLikePath(String path) {
        return path.startsWith("api/") || path.equals("api");
    }

    private static final class InMemoryHtmlResource extends ByteArrayResource {
        InMemoryHtmlResource(byte[] bytes) {
            super(bytes);
        }

        @Override
        public String getFilename() {
            return "index.html";
        }
    }
}
