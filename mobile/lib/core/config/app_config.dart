class AppConfig {
  const AppConfig._();

  static const apiBaseUrl = String.fromEnvironment(
    'HADIVO_API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );

  static const tenantId = String.fromEnvironment(
    'HADIVO_TENANT_ID',
    defaultValue: '11111111-1111-1111-1111-111111111111',
  );

  static const useDemoLocation = bool.fromEnvironment(
    'HADIVO_USE_DEMO_LOCATION',
    defaultValue: true,
  );

  static const demoLatitude = -6.2;
  static const demoLongitude = 106.816666;
  static const demoEmployeeEmail = 'employee@hadivo.local';
}
