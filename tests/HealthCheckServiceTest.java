@Test
void performHealthCheck_connectTimeoutException_savesInactiveWithErrorMessage() throws Exception {
    stubControllerForTwsCall();
    when(adminService.findAllControllers()).thenReturn(List.of(controller));
    when(encryptionUtil.decrypt(anyString())).thenThrow(mock(ConnectTimeoutException.class));
    when(twsApplicationValidationService.setFilter(anyString())).thenReturn(null);

    ArgumentCaptor<HealthCheckResultDocument> captor = ArgumentCaptor.forClass(HealthCheckResultDocument.class);
    healthCheckService.performHealthCheck();

    verify(healthCheckResultRepository, times(1)).save(captor.capture());
    assertFalse(captor.getValue().isActive());
    assertEquals("TWS service unavailable", captor.getValue().getErrorMessage());
}

Same shape for the other two single-controller ones, swap the stub and expected message:

@Test
void performHealthCheck_httpClientErrorException_savesInactiveWithErrorMessage() throws Exception {
    stubControllerForTwsCall();
    when(adminService.findAllControllers()).thenReturn(List.of(controller));
    when(encryptionUtil.decrypt(anyString())).thenReturn("decryptedPassword");
    when(twsApplicationValidationService.setFilter(anyString())).thenReturn(null);
    when(twsRequestUtil.callTWS(anyString(), any(), any(), any()))
            .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));

    ArgumentCaptor<HealthCheckResultDocument> captor = ArgumentCaptor.forClass(HealthCheckResultDocument.class);
    healthCheckService.performHealthCheck();

    verify(healthCheckResultRepository, times(1)).save(captor.capture());
    assertFalse(captor.getValue().isActive());
    assertEquals("Invalid request to TWS", captor.getValue().getErrorMessage());
}

@Test
void performHealthCheck_httpServerErrorException_savesInactiveWithErrorMessage() throws Exception {
    stubControllerForTwsCall();
    when(adminService.findAllControllers()).thenReturn(List.of(controller));
    when(encryptionUtil.decrypt(anyString())).thenReturn("decryptedPassword");
    when(twsApplicationValidationService.setFilter(anyString())).thenReturn(null);
    when(twsRequestUtil.callTWS(anyString(), any(), any(), any()))
            .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", null, null, null));

    ArgumentCaptor<HealthCheckResultDocument> captor = ArgumentCaptor.forClass(HealthCheckResultDocument.class);
    healthCheckService.performHealthCheck();

    verify(healthCheckResultRepository, times(1)).save(captor.capture());
    assertFalse(captor.getValue().isActive());
    assertEquals("TWS service error", captor.getValue().getErrorMessage());
}

@Test
void performHealthCheck_unexpectedException_savesInactiveWithErrorMessage() throws Exception {
    stubControllerForTwsCall();
    when(adminService.findAllControllers()).thenReturn(List.of(controller));
    when(encryptionUtil.decrypt(anyString())).thenReturn("decryptedPassword");
    when(twsApplicationValidationService.setFilter(anyString())).thenReturn(null);
    when(twsRequestUtil.callTWS(anyString(), any(), any(), any())).thenThrow(new RuntimeException("boom"));

    ArgumentCaptor<HealthCheckResultDocument> captor = ArgumentCaptor.forClass(HealthCheckResultDocument.class);
    healthCheckService.performHealthCheck();

    verify(healthCheckResultRepository, times(1)).save(captor.capture());
    assertFalse(captor.getValue().isActive());
    assertEquals("Validation service error", captor.getValue().getErrorMessage());
}

Last one, the multi-controller test. Note from your screenshot: your secondController mock is only stubbed for getName(), so once the loop actually reaches it (which it now does), its getUsername()/getPassword() return null, which is exactly the IllegalArgumentException: Username and password cannot be null you're seeing from SecureCredentials. That's not a bug, it's just that this test now needs secondController fully stubbed since it's genuinely processed:

@Test
void performHealthCheck_firstControllerFails_secondControllerStillProcessed() throws Exception {
    stubControllerForTwsCall();
    Controller secondController = mock(Controller.class);
    lenient().when(secondController.getName()).thenReturn("OPCY");
    when(secondController.getUsername()).thenReturn("someUser2");
    when(secondController.getPassword()).thenReturn("encryptedPassword2");
    when(secondController.getTwsURI()).thenReturn("https://tws2.example.com");
    when(secondController.getVersion()).thenReturn("v1");
    when(secondController.getEngineName()).thenReturn("engine2");

    when(adminService.findAllControllers()).thenReturn(List.of(controller, secondController));
    when(encryptionUtil.decrypt(anyString())).thenReturn("decryptedPassword");
    when(twsApplicationValidationService.setFilter(anyString())).thenReturn(null);
    when(twsRequestUtil.callTWS(anyString(), any(), any(), any()))
            .thenThrow(new RuntimeException("boom"))
            .thenReturn(ResponseEntity.status(HttpStatus.OK).body(new Object[0]));

    healthCheckService.performHealthCheck();

    ArgumentCaptor<HealthCheckResultDocument> captor = ArgumentCaptor.forClass(HealthCheckResultDocument.class);
    verify(healthCheckResultRepository, times(2)).save(captor.capture());
    List<HealthCheckResultDocument> saved = captor.getAllValues();
    assertFalse(saved.get(0).isActive());
    assertEquals("Validation service error", saved.get(0).getErrorMessage());
    assertTrue(saved.get(1).isActive());
}