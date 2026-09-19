package com.eshoppingzone.profile.audit;

import com.eshoppingzone.profile.audit.controller.AuditLogController;
import com.eshoppingzone.profile.audit.entity.AuditLog;
import com.eshoppingzone.profile.audit.repository.AuditLogRepository;
import com.eshoppingzone.profile.audit.service.AuditLogService;
import com.eshoppingzone.profile.dto.AddressDto;
import com.eshoppingzone.profile.dto.AddressRequest;
import com.eshoppingzone.profile.dto.UpdateProfileRequest;
import com.eshoppingzone.profile.dto.UserProfileDto;
import com.eshoppingzone.profile.entity.Address;
import com.eshoppingzone.profile.entity.UserProfile;
import com.eshoppingzone.profile.repository.AddressRepository;
import com.eshoppingzone.profile.repository.UserProfileRepository;
import com.eshoppingzone.profile.service.ProfileServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileAuditTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private AuditLogService auditLogService;
    private ProfileServiceImpl profileService;
    private AuditLogController auditLogController;

    private UserProfile testProfile;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository, new ObjectMapper(), rabbitTemplate);
        profileService = new ProfileServiceImpl(userProfileRepository, addressRepository, auditLogService);
        auditLogController = new AuditLogController(auditLogService);

        testProfile = new UserProfile();
        testProfile.setId(1L);
        testProfile.setUserId(101L);
        testProfile.setUsername("testuser");
        testProfile.setEmail("test@user.com");
        testProfile.setFullName("Test User");
    }

    @Test
    @DisplayName("1. Profile update creates audit event with PROFILE_UPDATED and SUCCESS outcome")
    void testUpdateProfileAudited() {
        when(userProfileRepository.findByUserId(101L)).thenReturn(Optional.of(testProfile));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Updated Name");
        request.setPhoneNumber("9876543210");
        request.setDateOfBirth(LocalDate.of(1995, 5, 20));
        request.setGender("OTHER");

        UserProfileDto dto = profileService.updateMyProfile(101L, request);
        assertNotNull(dto);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertEquals("PROFILE_UPDATED", log.getAction());
        assertEquals("PROFILE", log.getResourceType());
        assertEquals("101", log.getResourceId());
        assertEquals(101L, log.getActorUserId());
        assertEquals("SUCCESS", log.getOutcome());
        assertEquals("PROFILE_SERVICE", log.getServiceName());
    }

    @Test
    @DisplayName("2. Address creation creates audit event with ADDRESS_CREATED")
    void testAddressCreationAudited() {
        when(addressRepository.countByUserId(101L)).thenReturn(0L);
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> {
            Address a = invocation.getArgument(0);
            a.setId(55L);
            return a;
        });
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressRequest request = new AddressRequest();
        request.setStreet("123 Main St");
        request.setCity("Metropolis");
        request.setState("NY");
        request.setPostalCode("10001");
        request.setCountry("USA");
        request.setAddressType("HOME");

        AddressDto dto = profileService.addAddress(101L, request);
        assertNotNull(dto);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertEquals("ADDRESS_CREATED", log.getAction());
        assertEquals("ADDRESS", log.getResourceType());
        assertEquals("55", log.getResourceId());
        assertEquals(101L, log.getActorUserId());
        assertEquals("SUCCESS", log.getOutcome());
    }

    @Test
    @DisplayName("3. Address deletion creates audit event with ADDRESS_DELETED")
    void testAddressDeletionAudited() {
        Address address = new Address();
        address.setId(55L);
        address.setUserId(101L);
        address.setDefault(false);

        when(addressRepository.findByIdAndUserId(55L, 101L)).thenReturn(Optional.of(address));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        profileService.deleteAddress(55L, 101L);

        verify(addressRepository).delete(address);
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertEquals("ADDRESS_DELETED", log.getAction());
        assertEquals("ADDRESS", log.getResourceType());
        assertEquals("55", log.getResourceId());
        assertEquals(101L, log.getActorUserId());
    }

    @Test
    @DisplayName("4. Audit log entity immutability: updates and deletes are rejected")
    void testImmutability() {
        AuditLog auditLog = new AuditLog();
        assertThrows(UnsupportedOperationException.class, auditLog::preUpdate);
        assertThrows(UnsupportedOperationException.class, auditLog::preRemove);
    }

    @Test
    @DisplayName("5. Admin can query profile audit logs with pagination")
    void testAdminQueryAuditLogs() {
        AuditLog auditLog = new AuditLog();
        auditLog.setEventId("evt-profile-1");
        auditLog.setAction("PROFILE_UPDATED");
        auditLog.setOutcome("SUCCESS");
        auditLog.setTimestamp(LocalDateTime.now());
        auditLog.setServiceName("PROFILE_SERVICE");

        Page<AuditLog> paged = new PageImpl<>(List.of(auditLog));
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(paged);

        ResponseEntity<?> response = auditLogController.searchAuditLogs(
                101L, "PROFILE_UPDATED", "PROFILE", "101", "PROFILE_SERVICE", "SUCCESS", null, null, null, 0, 20, "timestamp", "desc"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
