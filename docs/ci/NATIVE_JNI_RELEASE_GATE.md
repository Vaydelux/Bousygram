# Native JNI Release Gate

`verifyTdlibBindingCompatibility` is required before debug and release assembly. It compares the Java `GIT_COMMIT_HASH` with `RESOLVED_COMMIT.txt`, requires both native ABIs, checks actual embedded revision strings in each ELF, validates ELF class/machine, JNI markers, required shared-library dependencies, and records SHA-256 evidence.

Current native hashes:

- ARM64: `205BE77AAB847ADF1CABEADBB2B004D7153C15839AD85E4E5E9707D1BD1679C19`
- ARM32: `2C8D3A9D2F049D3EEAF028271954FD89F9FA315426BE52BC4D1FB44E91969D7B`

The gate currently blocks because the checked-in Java binding embeds `cb863c1600082404428f1a84e407b866b9d412a8`, while the native libraries embed `4c6377aa7386c39217878a9a56d581922ad37ca4`. Changing only the Java hash is prohibited.
