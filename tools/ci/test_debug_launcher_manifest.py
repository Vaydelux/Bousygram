#!/usr/bin/env python3
import unittest
from verify_debug_launcher_manifest import verify

def activity(name, category, tag="activity"):
    return f'''    E: {tag}
      A: android:name(0x01010003)="{name}" (Raw: "{name}")
      E: intent-filter
        E: action
          A: android:name(0x01010003)="android.intent.action.MAIN"
        E: category
          A: android:name(0x01010003)="android.intent.category.{category}"
'''

def manifest(*entries):
    return "E: manifest\n  E: application\n" + "".join(entries)

class LauncherManifestTest(unittest.TestCase):
    def test_single_main_phone_and_tv(self):
        verify(manifest(activity(".app.MainActivity", "LAUNCHER"),
                        activity(".app.MainActivity", "LEANBACK_LAUNCHER")))
    def test_namespace_uri_and_shared_intent_filter(self):
        text = manifest(activity(".app.MainActivity", "LAUNCHER"))
        text += '        E: category\n          A: android:name(0x01010003)="android.intent.category.LEANBACK_LAUNCHER"\n'
        verify(text.replace("android:name", "http://schemas.android.com/apk/res/android:name"))
    def test_duplicate_lab(self):
        with self.assertRaises(ValueError):
            verify(manifest(activity(".app.MainActivity", "LAUNCHER"),
                            activity(".app.MainActivity", "LEANBACK_LAUNCHER"),
                            activity(".presentation.debug.RollingReceiverLabActivity", "LEANBACK_LAUNCHER")))
    def test_alias_not_allowed(self):
        with self.assertRaises(ValueError):
            verify(manifest(activity(".app.MainActivity", "LAUNCHER"),
                            activity(".LabAlias", "LEANBACK_LAUNCHER", "activity-alias")))
    def test_missing_tv(self):
        with self.assertRaises(ValueError):
            verify(manifest(activity(".app.MainActivity", "LAUNCHER")))
    def test_empty(self):
        with self.assertRaises(ValueError):
            verify("")

if __name__ == "__main__":
    unittest.main()
