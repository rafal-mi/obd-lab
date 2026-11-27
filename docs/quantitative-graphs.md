# Quantitative diagnostic graphs

Connect the simulator and start accelerator diagnostics. Record at least 30 seconds.
The combined chart and individual charts use a common trailing 20-second window
and fixed 0–100% range. Time labels are seconds since recording start.
Live readings show percent, raw decimal/hex, recording-wide valid min/max,
valid/total responses and average request rate per PID (including invalid replies).
Missing samples create gaps rather than false zero readings. The rate is based on
request timestamps; it is not the ECU's internal sensor sampling frequency.
CSV and raw export still include the entire recording.
Synthetic movement has a nominal period of 16 seconds, longer if requests are slow.
The latest 20 seconds remain visible after Stop; earlier samples are in CSV.

Run ./gradlew :app:testDebugUnitTest :app:assembleDebug before merging.
