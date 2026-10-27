# Android TV Navigation

## Player

| State | Key | Result |
| --- | --- | --- |
| Overlay hidden | OK / Up / Down | Reveal controls |
| Overlay hidden | Left / Right | Enter seek preview when seekable |
| Overlay hidden | Back | Return to browser |
| Any | Play/Pause | Toggle playback |
| Overlay visible | Directional | Move focus; Left/Right scrub only when timeline owns focus |
| Seeking | OK | Commit one seek |
| Seeking | Back | Cancel preview and restore focus |

Left/Right are never next/previous shortcuts. Queue changes require an explicitly focused visible action. Compose key handling owns navigation keys; MediaSession owns physical media commands without double-dispatch.

## Browse and focus

Media cards use stable chat/message keys. Grid scroll/focus state is stored per Telegram source/topic, and returning from playback restores the selected card where possible. Dialogs restore their triggering control. Focused surfaces use a high-contrast border, readable label, and short subtle scale animation.

Forum-enabled groups are browsed by actual topic ID. Normal groups expose their media directly. Topic views do not infer seasons or series from filenames.
