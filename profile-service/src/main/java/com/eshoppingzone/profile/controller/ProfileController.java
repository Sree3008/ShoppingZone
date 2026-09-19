package com.eshoppingzone.profile.controller;

import com.eshoppingzone.profile.dto.*;
import com.eshoppingzone.profile.exception.UnauthorizedException;
import com.eshoppingzone.profile.security.UserPrincipal;
import com.eshoppingzone.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profiles")
@Tag(name = "Profile Management", description = "APIs for user profile and address management")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    private UserPrincipal getPrincipal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        throw new UnauthorizedException("User is not authenticated");
    }

    @GetMapping("/me")
    @Operation(summary = "Get My Profile", description = "Retrieve current authenticated user's profile")
    public ResponseEntity<ApiResponse<UserProfileDto>> getMyProfile(Authentication authentication) {
        UserPrincipal principal = getPrincipal(authentication);
        Long userId = principal.getUserId();
        String username = principal.getUsername();
        String email = principal.getEmail();

        UserProfileDto profile = profileService.getMyProfile(userId, username, email);
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully", profile));
    }

    @PutMapping("/me")
    @Operation(summary = "Update My Profile", description = "Update current user's profile details")
    public ResponseEntity<ApiResponse<UserProfileDto>> updateMyProfile(Authentication authentication,
                                                                       @Valid @RequestBody UpdateProfileRequest request) {
        UserPrincipal principal = getPrincipal(authentication);
        Long userId = principal.getUserId();
        UserProfileDto profile = profileService.updateMyProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", profile));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get Profile by User ID", description = "Endpoint to get a user profile by ID")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('INTERNAL', 'ADMIN', 'CUSTOMER')")
    public ResponseEntity<ApiResponse<UserProfileDto>> getProfileByUserId(Authentication authentication, @PathVariable Long userId) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            boolean isInternalOrAdmin = principal.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_INTERNAL") || a.getAuthority().equals("ROLE_ADMIN"));
            if (!isInternalOrAdmin && !userId.equals(principal.getUserId())) {
                throw new org.springframework.security.access.AccessDeniedException("Access denied: You can only view your own profile");
            }
        }
        UserProfileDto profile = profileService.getProfileByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully", profile));
    }

    @PostMapping("/addresses")
    @Operation(summary = "Add Address", description = "Add a new shipping address for authenticated user")
    public ResponseEntity<ApiResponse<AddressDto>> addAddress(Authentication authentication,
                                                              @Valid @RequestBody AddressRequest request) {
        UserPrincipal principal = getPrincipal(authentication);
        Long userId = principal.getUserId();
        AddressDto address = profileService.addAddress(userId, request);
        return new ResponseEntity<>(ApiResponse.success("Address added successfully", address), HttpStatus.CREATED);
    }

    @GetMapping("/addresses")
    @Operation(summary = "Get My Addresses", description = "List all addresses saved for authenticated user")
    public ResponseEntity<ApiResponse<List<AddressDto>>> getMyAddresses(Authentication authentication) {
        UserPrincipal principal = getPrincipal(authentication);
        Long userId = principal.getUserId();
        List<AddressDto> addresses = profileService.getMyAddresses(userId);
        return ResponseEntity.ok(ApiResponse.success("Addresses retrieved successfully", addresses));
    }

    @GetMapping("/addresses/{id}")
    @Operation(summary = "Get Address by ID", description = "Retrieve a specific address by ID")
    public ResponseEntity<ApiResponse<AddressDto>> getAddressById(Authentication authentication,
                                                                  @PathVariable Long id) {
        UserPrincipal principal = getPrincipal(authentication);
        Long userId = principal.getUserId();
        AddressDto address = profileService.getAddressById(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Address retrieved successfully", address));
    }

    @PutMapping("/addresses/{id}")
    @Operation(summary = "Update Address", description = "Update an existing address")
    public ResponseEntity<ApiResponse<AddressDto>> updateAddress(Authentication authentication,
                                                                 @PathVariable Long id,
                                                                 @Valid @RequestBody AddressRequest request) {
        UserPrincipal principal = getPrincipal(authentication);
        Long userId = principal.getUserId();
        AddressDto address = profileService.updateAddress(id, userId, request);
        return ResponseEntity.ok(ApiResponse.success("Address updated successfully", address));
    }

    @DeleteMapping("/addresses/{id}")
    @Operation(summary = "Delete Address", description = "Delete an address")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(Authentication authentication,
                                                           @PathVariable Long id) {
        UserPrincipal principal = getPrincipal(authentication);
        Long userId = principal.getUserId();
        profileService.deleteAddress(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Address deleted successfully", null));
    }

    @PatchMapping("/addresses/{id}/default")
    @Operation(summary = "Set Default Address", description = "Mark an address as the default address")
    public ResponseEntity<ApiResponse<AddressDto>> setDefaultAddress(Authentication authentication,
                                                                     @PathVariable Long id) {
        UserPrincipal principal = getPrincipal(authentication);
        Long userId = principal.getUserId();
        AddressDto address = profileService.setDefaultAddress(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Default address updated successfully", address));
    }

    @GetMapping("/addresses/{id}/internal")
    @Operation(summary = "Get Address Internal", description = "Internal endpoint for other microservices")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('INTERNAL', 'ADMIN')")
    public ResponseEntity<ApiResponse<AddressDto>> getAddressInternal(@PathVariable Long id) {
        AddressDto address = profileService.getAddressInternal(id);
        return ResponseEntity.ok(ApiResponse.success("Address retrieved successfully", address));
    }
}
