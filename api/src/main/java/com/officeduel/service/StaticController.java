package com.officeduel.service;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

@RestController
public class StaticController {
    
    @GetMapping("/")
    public ResponseEntity<Resource> serveIndex() {
        try {
            // Serve from web/ directory
            File webFile = new File("../web/index.html");
            if (webFile.exists()) {
                Resource resource = new FileSystemResource(webFile);
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                    .body(resource);
            } else {
                // Fallback to static resources
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
