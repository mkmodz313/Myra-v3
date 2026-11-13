# MYRA add-on: modified / new files
(Original project is untouched where not listed. A copy of the original zip is the backup.)

## Modified (existing files)
- app/src/main/AndroidManifest.xml – new activities, VideoWallpaperService, ACCESS_NETWORK_STATE, live_wallpaper feature
- app/src/main/java/com/myra/assistant/services/ForegroundVoiceService.kt – transcripts→chat, reconnect/backoff, network watcher, phone-control hook, prompt lines
- app/src/main/java/com/myra/assistant/websocket/GeminiWebSocketClient.kt – shared OkHttp client, transcription, session resumption, stale-socket guard, goAway/close handling (same model, voice, key, audio format)
- app/src/main/java/com/myra/assistant/voice/AudioPlayer.kt – blocking queue (no 10 ms sleep), low-latency AudioTrack mode, dropPending(); lip-sync counters untouched
- app/src/main/java/com/myra/assistant/ui/main/MainActivity.kt – 3-button nav wiring (Chat/Voice/Settings)
- app/src/main/java/com/myra/assistant/ui/settings/SettingsActivity.kt – Live Wallpapers entry
- app/src/main/res/layout/activity_main.xml – bottom area replaced by the 3D glass 3-button bar (avatar/background untouched)
- app/src/main/res/layout/activity_settings.xml – Live Wallpapers card
- app/src/main/res/values/strings.xml – wallpaper description

## New
- data/ChatRepository.kt (shared persistent voice+text history)
- control/PhoneControl.kt (app launching, WhatsApp by number, confirm-before-send, websites, search)
- ui/chat/ChatActivity.kt, ChatMessagesAdapter.kt + layouts activity_chat, item_msg_*
- wallpaper/ CloudConfig, WallpaperItem, WallpaperRepository, ThumbLoader, VideoWallpaperService, WallpaperActivity, WallpaperDetailActivity + layouts
- assets/samples/ (2 real mp4 + thumbnails), res/xml/myra_wallpaper.xml, neon drawables/icons, drawable-nodpi/myra_avatar_thumb.png
- backend/ (admin web panel, firestore.rules, storage.rules, firebase.json, README_BACKEND.md)

## Later additions
- ui/main/SplashActivity (launcher) + splash_bg.jpg
- admin/AdminApi.kt, AdminLoginActivity.kt, AdminPanelActivity.kt (in-app password-protected admin panel, REST: Firebase Auth + Firestore + Storage)
- PhoneControl.executeTool + Gemini function calling (open_app, play_on_youtube, open_website, search_web, whatsapp_*, run_command) so voice commands really execute
- Settings & home 3D redesign, 4-button bottom card

- Backend switched from Firebase to Supabase (admin/AdminApi.kt, wallpaper/CloudConfig.kt, WallpaperRepository.kt, backend/supabase_setup.sql)
- control/UiAutomation.kt + control/WhatsAppAutomation.kt: in-app control through the Accessibility Service (read screen, tap, type, scroll, back/home) and WhatsApp by saved contact name (open chat, read messages, play voice message, type reply; send only after explicit yes); matching Gemini function declarations
- Faster replies: Gemini setup tuned (thinking budget 0, quicker end-of-speech detection) with automatic fallback
- utils/ApiKeys.kt: built-in default Gemini key (user's own key in Settings overrides it)
- Home bottom card: Chat · Voice · Wallpaper (Settings = top gear)
- Girlfriend Mode switch, Charging announce, Message announce (utils/Announcer.kt, service/MessageNotificationListener.kt)
- MyraApp (Application): crash guard (background-thread errors swallowed+logged, main-thread crash -> auto restart), PowerWatcher registered process-wide, thumbnails warmed at start
- ThumbLoader: permanent on-disk thumbnail cache (gallery opens instantly, works offline)
- Announcer/PowerWatcher: TTS created on main thread, Hindi voice or Roman fallback, announcement test button; Settings -> Last crash report
- Welcome dialog (WelcomeDialog + welcome_header.webp) with WhatsApp channel button; network banner (No internet connection / Poor network)
- Calls: CallControl (answer / end / speaker), answer_call + end_call voice tools, "phone pe baat karo" talk mode in CallAssistantActivity
- Default Gemini key is NOT in source anymore: injected at build time from GEMINI_DEFAULT_KEY
  (GitHub Actions secret, environment variable, or local.properties:  GEMINI_DEFAULT_KEY=your_key)
- design/: Premium Designs (26 Android / iOS / Premium looks) + Apps Box launcher-style screen (search, voice search, dock); Settings -> Premium Designs
