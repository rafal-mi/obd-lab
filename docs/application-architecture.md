# Application navigation

Home exposes Connection, Diagnostic console and Accelerator diagnostics even offline.
Connection supports USB, Bluetooth and a synthetic in-process simulator.
Choose Connect simulator, Home, Accelerator diagnostics, Start. Watch the curves,
Stop, copy CSV or raw log, return Home and reopen to inspect the last recording.
The global status identifies simulation; exports identify their source.
No vehicle or permissions are required for simulator testing.

The Activity owns a DiagnosticSessionManager with StateFlow connection state
and an exclusive operation lock, including the complete accelerator sampling
session. No two instruments may send commands simultaneously. The last recording
is retained across screen navigation, not process death or Activity recreation.
Only adapter connectivity is claimed, not verified ECU connectivity.
Navigation uses a simple enum, no new dependencies. Future screens can be added
before adopting Navigation 3 or DI. No additional unimplemented instruments shown.

Run ./gradlew :app:testDebugUnitTest :app:assembleDebug before merging.
