# C1750 investigation

Park the vehicle, ignition on, engine off. Close other diagnostic apps.
Connect Bluetooth or USB in OBD Lab. Use Diagnostic Console.

Run ATZ, ATI, ATE0, ATL0, ATS1, ATH1, ATSP0, 0100, ATDP, ATDPN,
03, 07, 0A. Wait for each response. Use Copy log before disconnecting.
ATZ resets the adapter, not vehicle DTCs. If protocol discovery fails,
save the response and stop rather than guessing.

Run 0140 to check supported PIDs, then 0149, 014A, 014B, 015A
where supported. Repeat while slowly moving the pedal. These are raw
samples, not graphs. No DTC clearing is included.

C1750 encodes as 57 50 in a two-byte SAE DTC. The code alone does
not establish the reporting module or failure status. Generic services
03/07/0A may not expose instrument cluster DTCs. Do not infer a code
from arbitrary bytes in a frame. The decoder is a standalone utility;
automatic response parsing is deliberately deferred.

Ford module addressing and diagnostic service must be verified before
using physical addressing. ATSH and ATCRA permit three-digit CAN IDs
for adapter configuration; reset/reinitialize before returning to
functional addressing. Do not use the console while driving.
Clipboard logs can contain device identifiers; review before sharing.

Build and run unit tests: ./gradlew :app:testDebugUnitTest :app:assembleDebug
