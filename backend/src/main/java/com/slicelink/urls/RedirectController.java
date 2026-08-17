package com.slicelink.urls;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public controller for short URL redirection.
 */
@RestController
public class RedirectController {

    private final UrlService urlService;

    public RedirectController(UrlService urlService) {
        this.urlService = urlService;
    }

    @GetMapping("/{shortCode}")
    @Operation(
            summary = "Redirect to original URL",
            description = "Resolves a shortened code and redirects the client to the target URL."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirect to original target URL"),
            @ApiResponse(responseCode = "404", description = "Short URL not found"),
            @ApiResponse(responseCode = "410", description = "Short URL is disabled")
    })
    public ResponseEntity<Void> redirect(@PathVariable("shortCode") String shortCode) {
        String originalUrl = urlService.getRedirectUrl(shortCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }
}
