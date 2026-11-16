# MYRA Automation System - Dynamic Architecture

## Overview
This document explains the modernized automation system that eliminates hardcoded values and implements dynamic, intelligent UI detection and app management.

## Key Improvements

### 1. **AppDetector.kt** (NEW)
- ✅ **Dynamic App Detection** - Automatically scans device for installed apps
- ✅ **Fuzzy Matching** - Finds apps by partial name matches (e.g., "Gmail" matches "Gmail by Google")
- ✅ **System App Filtering** - Separates user apps from system apps
- ✅ **No Hardcoded App Lists** - Works with any installed app

**Key Functions:**
- `getInstalledApps()` - Get all user applications
- `findAppByName()` - Find specific app with intelligent matching
- `getAllApps()` - Include system apps if needed

### 2. **ScreenMonitor.kt** (NEW)
- ✅ **Continuous Screen Monitoring** - Tracks UI changes in real-time
- ✅ **Dynamic Element Extraction** - Captures all clickable and text elements
- ✅ **Relevance Scoring** - Finds elements matching user intent
- ✅ **No Fixed Coordinates** - Everything is coordinate-independent

**Key Functions:**
- `captureScreenState()` - Get current UI tree
- `findRelevantElements()` - Find elements matching command
- `getClickableElements()` - List all interactive elements
- `onAccessibilityEvent()` - Update on screen changes

### 3. **UiTreeSerializer.kt** (ENHANCED)
Previously: Only serialized the tree to JSON
Now: 
- ✅ Smart element matching with fuzzy search
- ✅ Finds clickable elements by intent
- ✅ Calculates node center coordinates
- ✅ Filters by visibility and state

**New Functions:**
- `findMatchingElements()` - Search UI tree for command match
- `findClickableElements()` - Get all interactive elements
- `getNodeCenter()` - Get click coordinates

### 4. **ActionExecutor.kt** (ENHANCED)**
Previously: Simple text matching
Now:
- ✅ **Multi-method clicking** - Text, description, ID, intention
- ✅ **Semantic Intent** - Understands "click send button"
- ✅ **Gesture Support** - Tap, swipe, long-press (improved)
- ✅ **Smart Parent Search** - Finds clickable parent if needed

**New Functions:**
- `clickByIntention()` - Semantic understanding
- `swipe()` - Directional gestures
- `longPress()` - Long-press support
- `getFirstClickable()` - Fallback to first interactive element

### 5. **ScreenVisionAnalyzer.kt** (REFACTORED)**
**REMOVED:** All hardcoded coordinates like:
```kotlin
// ❌ BEFORE: Hardcoded positions
point(0.92, 0.08, screenWidth, screenHeight)  // Menu
point(0.92, 0.93, screenWidth, screenHeight)  // Send
```

**NOW:** Dynamic element detection:
```kotlin
// ✅ AFTER: Intelligent detection
findInteractiveElement(command)  // Analyzes actual UI
findButtonByIntent("send")        // Finds real send button
```

**New Functions:**
- `findInteractiveElement()` - Smart element detection
- `findButtonByIntent()` - Intent-based button search
- `analyzeAndExecute()` - Full automation pipeline
- `getAllInteractiveElements()` - List all clickables

### 6. **SmartAutomationAgent.kt** (ENHANCED)**
Now implements a 3-tier strategy:
1. UI tree analysis with matching
2. Semantic intention detection
3. Cleaned command fallback

### 7. **AutomationManager.kt** (NEW)**
Central orchestration for all automation:
- ✅ Lifecycle management
- ✅ Task execution pipeline
- ✅ Screen state queries
- ✅ App management
- ✅ Automatic mode control

## Workflow Example

### User says: "Open Gmail"
```
User Command → CommandProcessor
→ "OPEN_APP:Gmail"
→ AutomationManager.executeTask()
→ AppDetector.findAppByName("Gmail")
→ Dynamically finds installed Gmail app
→ Launches with proper intent
→ ✅ Success (works with ANY Gmail app name)
```

### User says: "Click the send button"
```
User Command → SmartAutomationAgent
→ UiTreeSerializer analyzes current screen
→ Finds "send" or similar elements
→ ActionExecutor.performClick()
→ Monitors screen and finds real send button
→ ✅ Success (works on ANY app)
```

## No More Hardcoded Values!

### ❌ OLD Approach
- Menu always at (0.92, 0.08)
- Send always at (0.92, 0.93)
- Next always at (0.82, 0.92)
- Works only if UI is exactly as expected

### ✅ NEW Approach
- Analyzes actual screen content
- Finds elements by text/description
- Adapts to any UI layout
- Works across all apps

## Usage in Your Accessibility Service

```kotlin
// In MyraAccessibilityService or similar
override fun onCreate() {
    super.onCreate()
    
    // Initialize automation
    AutomationManager.initialize(this, lifecycleScope)
}

// When receiving commands
fun processCommand(command: String) {
    val success = AutomationManager.executeTask(command)
    
    // Or for specific automation:
    SmartAutomationAgent.run(this, command)
}

// Get screen info for AI
fun getScreenInfo(): ScreenMonitor.ScreenState? {
    return AutomationManager.getCurrentScreenState()
}

// Find installed apps
fun listApps(): List<AppDetector.InstalledApp> {
    return AutomationManager.getInstalledApps()
}
```

## Supported Commands

```
✅ "OPEN_APP:Gmail" → Opens installed Gmail
✅ "Click Send" → Finds and clicks send button
✅ "Search Pizza" → Opens search and types "Pizza"
✅ "Swipe Left" → Performs left swipe gesture
✅ "Long Press Settings" → Long-press on Settings element
✅ Any UI element text → Finds and clicks it
```

## Benefits

| Aspect | Before | After |
|--------|--------|-------|
| **App Opening** | Hardcoded package names | Dynamic app detection |
| **Button Clicking** | Fixed screen coordinates | Real element detection |
| **Screen Changes** | Manual update | Automatic monitoring |
| **Gesture Support** | Basic tap only | Tap, swipe, long-press |
| **App Adaptation** | Works on 1 layout | Works on any layout |
| **Maintainability** | Update code for each UI change | Zero code updates needed |

## Future Enhancements

- [ ] AI-powered intent classification
- [ ] Screenshot comparison for context awareness
- [ ] OCR for text-based buttons
- [ ] Machine learning for smart element selection
- [ ] Gesture recording and playback
- [ ] Cross-app workflow automation
