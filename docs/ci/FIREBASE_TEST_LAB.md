# Firebase Test Lab

Firebase execution is opt-in through repository variables. Configure `FIREBASE_TEST_LAB_ENABLED=true`, authentication variables, project ID, and explicit phone/TV model/version variables. The workflow calls `gcloud firebase test android models describe` for both configured targets before running tests.

Workload Identity Federation is preferred. Protected service-account JSON is retained only as an explicit fallback. Missing configuration produces a visible NOT CONFIGURED job rather than a false pass.

Phone, Android TV, physical ARM qualification, and Chromecast integration remain separate test categories.
