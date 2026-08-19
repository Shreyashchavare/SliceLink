package com.slicelink.urls;

import com.slicelink.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for URL creation and management operations.
 */
@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create a shortened URL",
            description = "Creates a new shortened URL for the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "URL shortened successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public UrlResponse createUrl(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateUrlRequest request
    ) {
        Url url = urlService.create(principal.id(), request.originalUrl());
        return UrlResponse.from(url);
    }

    @GetMapping
    @Operation(
            summary = "List user's shortened URLs",
            description = "Lists all shortened URLs owned by the authenticated user, ordered newest first.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "URLs retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public List<UrlResponse> listUrls(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return urlService.listByOwner(principal.id())
                .stream()
                .map(UrlResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get shortened URL details",
            description = "Retrieves details of a shortened URL owned by the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "URL retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "URL not found")
    })
    public UrlResponse getUrl(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("id") Long id
    ) {
        Url url = urlService.getByIdAndOwner(id, principal.id());
        return UrlResponse.from(url);
    }

    @RequestMapping(path = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @Operation(
            summary = "Update destination URL",
            description = "Updates the original destination URL of a shortened URL owned by the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "URL updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "URL not found")
    })
    public UrlResponse updateUrl(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateUrlRequest request
    ) {
        Url url = urlService.updateOriginalUrl(id, principal.id(), request.originalUrl());
        return UrlResponse.from(url);
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Update URL lifecycle status",
            description = "Enables or disables a shortened URL owned by the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "URL status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "URL not found")
    })
    public UrlResponse updateStatus(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateUrlStatusRequest request
    ) {
        Url url = urlService.updateStatus(id, principal.id(), request.status());
        return UrlResponse.from(url);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete shortened URL",
            description = "Deletes a shortened URL owned by the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "URL deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "URL not found")
    })
    public void deleteUrl(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("id") Long id
    ) {
        urlService.delete(id, principal.id());
    }
}
