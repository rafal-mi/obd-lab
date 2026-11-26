# Automated accelerator experiment

Park, ignition on, engine off. Close other diagnostic apps. Connect to
the adapter, select Accelerator diagnostics, then Start. Wait for the
recording status and slowly press/release the pedal three times.
Stop, then copy CSV and raw log. No manual hexadecimal input required.

The adapter is reset and configured with headers off; the app discovers
01/00, 01/20 and 01/40 as advertised and samples only supported pedal
PIDs. Multiple responses for a PID are rejected rather than merged.
The recording stops after 120 seconds or 2000 samples, or on Stop.
Stop waits for the current response; Bluetooth reads in the existing
transport can block if the adapter never responds. Reconnect if needed.
Graphs show the latest 120 samples per PID on a fixed 0–100% vertical
scale; CSV contains all recorded samples. Data is in memory only.
This feature does not retrieve Ford-specific codes or prove the cause of C1750.
Run ./gradlew :app:testDebugUnitTest :app:assembleDebug before testing.
