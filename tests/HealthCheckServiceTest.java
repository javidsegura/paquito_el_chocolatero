package com.citi.ci.batch.batchcmdb.twshealthcheck.service;

// very likely need to adjust:
//   - Controller getter names (getName/getUsername/getPassword/getTwsURI/getVersion/getEngineName
//     are assumed; fix any that don't match your real Controller class)
//   - CustomException's constructor (assumed CustomException(String, Throwable))
//   - QueryFilterModel / JobStreamHeader (assumed simple types with no special constructor needs)
//   - the exact package of Controller, CustomException, SecureCredentials, EncryptionUtil,
//     TWSRequestUtil, AdminService, TwsApplicationValidationService, QueryFilterModel,
//     JobStreamHeader (import statements below are best guesses at com.citi.ci.batch.batchcmdb.*)
//
// Move this file to:
//   src/test/java/com/citi/ci/batch/batchcmdb/twshealthcheck/service/HealthCheckServiceTest.java

import com.citi.ci.batch.batchcmdb.entities.Controller;
import com.citi.ci.batch.batchcmdb.exeptions.CustomException;
import com.citi.ci.batch.batchcmdb.services.AdminService;
import com.citi.ci.batch.batchcmdb.twshealthcheck.model.HealthCheckResultDocument;
import com.citi.ci.batch.batchcmdb.twshealthcheck.repository.HealthCheckResultRepository;
import com.citi.ci.batch.batchcmdb.util.EncryptionUtil;
import com.citi.ci.batch.batchcmdb.util.TWSRequestUtil;

import org.apache.http.conn.ConnectTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthCheckServiceTest {

    @InjectMocks
    private HealthCheckService healthCheckService;

    @Mock
    private AdminService adminService;
    @Mock
    private TWSRequestUtil twsRequestUtil;
    @Mock
    private HealthCheckResultRepository healthCheckResultRepository;
    @Mock
    private com.citi.ci.batch.batchcmdb.services.TwsApplicationValidationService twsApplicationValidationService;
    @Mock
    private EncryptionUtil encryptionUtil;

    private Controller controller;

    @BeforeEach
    void setUp() {
        controller = mock(Controller.class);
        when(controller.getName()).thenReturn("TWCB");
        when(controller.getUsername()).thenReturn("someUser");
        when(controller.getPassword()).thenReturn("encryptedPassword");
        when(controller.getTwsURI()).thenReturn("https://tws.example.com");
        when(controller.getVersion()).thenReturn("v1");
        when(controller.getEngineName()).thenReturn("engine1");
    }

    // ---- no controllers ----

    @Test
    void performHealthCheck_noControllers_savesNothing() {
        // Arrange
        when(adminService.findAllControllers()).thenReturn(Collections.emptyList());

        // Act
        healthCheckService.performHealthCheck();

        // Assert
        verify(healthCheckResultRepository, never()).save(any(HealthCheckResultDocument.class));
    }

    // ---- happy path ----

    @Test
    void performHealthCheck_successfulResponse_savesActiveDocumentWithExpectedFields() throws Exception {
        // Arrange
        when(adminService.findAllControllers()).thenReturn(List.of(controller));
        when(encryptionUtil.decrypt("encryptedPassword")).thenReturn("decryptedPassword");
        when(twsApplicationValidationService.setFilter(anyString())).thenReturn(null);
        when(twsRequestUtil.callTWS(anyString(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(HttpStatus.OK).body(new Object[0]));

        ArgumentCaptor<HealthCheckResultDocument> captor =
                ArgumentCaptor.forClass(HealthCheckResultDocument.class);

        // Act
        healthCheckService.performHealthCheck();

        // Assert
        verify(healthCheckResultRepository, times(1)).save(captor.capture());
        HealthCheckResultDocument saved = captor.getValue();

        assertEquals("TWCB", saved.getControllerName());
        assertTrue(saved.isActive());
        assertNotNull(saved.getCheckTriggeredAt());
        assertNotNull(saved.getResponseReceivedAt());
        assertTrue(saved.getRequestTimeMs() >= 0);
        assertEquals(InetAddress.getLocalHost().getHostName(), saved.getFromServer());
    }

    @Test
    void performHealthCheck_nonSuccessStatusCode_savesInactiveWithErrorMessage() {
        // Arrange
        when(adminService.findAllControllers()).thenReturn(List.of(controller));
        when(encryptionUtil.decrypt("encryptedPassword")).thenReturn("decryptedPassword");
        when(twsApplicationValidationService.setFilter(anyString())).thenReturn(null);
        when(twsRequestUtil.callTWS(anyString(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new Object[0]));

        ArgumentCaptor<HealthCheckResultDocument> captor =
                ArgumentCaptor.forClass(HealthCheckResultDocument.class);

        // Act
        healthCheckService.performHealthCheck();

        // Assert
        verify(healthCheckResultRepository, times(1)).save(captor.capture());
        HealthCheckResultDocument saved = captor.getValue();

        assertFalse(saved.isActive());
        assertNotNull(saved.getErrorMessage());
        assertTrue(saved.getErrorMessage().contains("503"));
    }

    // ---- null response handling ----
    // performHealthCheckOnController now null-checks the TWS response before touching
    // getStatusCode(), setting active=false + an error message instead of letting an NPE
    // escape. Unlike the exception branches below, this path does NOT throw, so the loop
    // continues and the document still gets saved normally.

    @Test
    void performHealthCheck_nullResponse_savesInactiveWithErrorMessageAndDoesNotThrow() {
        // Arrange
        when(adminService.findAllControllers()).thenReturn(List.of(controller));
        when(encryptionUtil.decrypt("encryptedPassword")).thenReturn("decryptedPassword");
        when(twsApplicationValidationService.setFilter(anyString())).thenReturn(null);
        when(twsRequestUtil.callTWS(anyString(), any(), any(), any())).thenReturn(null);

        ArgumentCaptor<HealthCheckResultDocument> captor =
                ArgumentCaptor.forClass(HealthCheckResultDocument.class);

        // Act
        healthCheckService.performHealthCheck();

        // Assert
        verify(healthCheckResultRepository, times(1)).save(captor.capture());
        HealthCheckResultDocument saved = captor.getValue();

        assertFalse(saved.isActive());
        assertNotNull(saved.getErrorMessage());
        // TODO: tighten this to your exact error message text, e.g.:
        // assertEquals("No response received from TWS API", saved.getErrorMessage());
    }

    // ---- exception branches on performHealthCheckOnController ----

    @Test
    void performHealthCheck_connectTimeoutException_throwsCustomExceptionAndDoesNotSave() {
        // Arrange
        when(adminService.findAllControllers()).thenReturn(List.of(controller));
        when(encryptionUtil.decrypt("encryptedPassword")).thenReturn("decryptedPassword");
        when(twsApplicationValidationService.setFilter(anyString())).thenReturn(null);
        when(twsRequestUtil.callTWS(anyString(), any(), any(), any()))
                .thenThrow(mock(ConnectTimeoutException.class));

        // Act / Assert
        CustomException thrown = assertThrows(CustomException.class,
                () -> healthCheckService.performHealthCheck());
        assertEquals("TWS service unavailable", thrown.getMessage());
        verify(healthCheckResultRepository, never()).save(any(HealthCheckResultDocument.class));
    }

    @Test
    void performHealthCheck_httpClientErrorException_throwsCustomExceptionAndDoesNotSave() {
        // Arrange
        when(adminService.findAllControllers()).thenReturn(List.of(controller));
        when(encryptionUtil.decrypt("encryptedPassword")).thenReturn("decryptedPassword");
        when(twsApplicationValidationService.setFilter(anyString())).thenReturn(null);
        when(twsRequestUtil.callTWS(anyString(), any(), any(), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST,
                        "Bad Request", null, null, null));

        // Act / Assert
        CustomException thrown = assertThrows(CustomException.class,
                () -> healthCheckService.performHealthCheck());
        assertEquals("Invalid request to TWS", thrown.getMessage());
        verify(healthCheckResultRepository, never()).save(any(HealthCheckResultDocument.class));
    }

    @Test
    void performHealthCheck_httpServerErrorException_throwsCustomExceptionAndDoesNotSave() {
        // Arrange
        when(adminService.findAllControllers()).thenReturn(List.of(controller));
        when(encryptionUtil.decrypt("encryptedPassword")).thenReturn("decryptedPassword");
        when(twsApplicationValidationService.setFilter(anyString())).thenReturn(null);
        when(twsRequestUtil.callTWS(anyString(), any(), any(), any()))
                .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Server Error", null, null, null));

        // Act / Assert
        CustomException thrown = assertThrows(CustomException.class,
                () -> healthCheckService.performHealthCheck());
        assertEquals("TWS service error", thrown.getMessage());
        verify(healthCheckResultRepository, never()).save(any(HealthCheckResultDocument.class));
    }

    @Test
    void performHealthCheck_unexpectedException_throwsCustomExceptionAndDoesNotSave() {
        // Arrange
        when(adminService.findAllControllers()).thenReturn(List.of(controller));
        when(encryptionUtil.decrypt("encryptedPassword")).thenReturn("decryptedPassword");
        when(twsApplicationValidationService.setFilter(anyString())).thenReturn(null);
        when(twsRequestUtil.callTWS(anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        // Act / Assert
        CustomException thrown = assertThrows(CustomException.class,
                () -> healthCheckService.performHealthCheck());
        assertEquals("Validation service error", thrown.getMessage());
        verify(healthCheckResultRepository, never()).save(any(HealthCheckResultDocument.class));
    }

    // ---- multi-controller behavior ----
    // Documents CURRENT behavior for genuine thrown exceptions (timeouts/HTTP errors/etc,
    // as opposed to the null-response case above which is now handled without throwing):
    // a thrown exception on one controller still aborts the whole run, so later
    // controllers never get checked or saved. If a future refactor makes the loop
    // continue past a failing controller instead, update/replace this test to match.

    @Test
    void performHealthCheck_firstControllerFails_secondControllerNeverProcessed() {
        // Arrange
        Controller secondController = mock(Controller.class);
        when(secondController.getName()).thenReturn("OPCY");

        when(adminService.findAllControllers()).thenReturn(List.of(controller, secondController));
        when(encryptionUtil.decrypt(anyString())).thenReturn("decryptedPassword");
        when(twsApplicationValidationService.setFilter(anyString())).thenReturn(null);
        when(twsRequestUtil.callTWS(anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        // Act / Assert
        assertThrows(CustomException.class, () -> healthCheckService.performHealthCheck());
        verify(healthCheckResultRepository, never()).save(any(HealthCheckResultDocument.class));
        // secondController's TWS call is never reached because the loop aborts on the first failure
    }
}
