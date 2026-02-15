# Function Documentation

## NativeVoiceLikeActivity.kt

### `EmptyState`
**Description:** Displays the UI when there are no more cards to show in the current batch or filter. Handles the "End Fallback" logic when all items are processed.
**Parameters:**
- `onRestart: () -> Unit`: Callback to load the next batch of items.
- `pendingTrashCount: Int`: Number of items currently in the trash queue (default: 0).
- `onViewTrash: () -> Unit`: Callback to view the trash queue.
- `filterFolder: String`: Current folder filter (default: "All").
- `filterType: String`: Current media type filter (default: "All").
- `filterMonth: String`: Current month filter (default: "All").
- `onResetFilter: () -> Unit`: Callback to reset all filters.
- `onChangeFilter: () -> Unit`: Callback to open the filter selection view.
- `onReleaseKept: () -> Unit`: Callback to release processed photos back to the queue.
- `onViewLikes: () -> Unit`: Callback to view the list of liked/favorite items.
- `remainingCount: Int`: Number of items remaining in the global pool that match current filters.
- `isFullAlbumDone: Boolean`: Flag indicating if all items matching the current filter have been processed.
**Return Value:** `Unit` (Composable)

### `CelebrationConfetti`
**Description:** Renders a confetti animation using a Canvas.
**Parameters:**
- `intensity: Int`: Controls the number of particles and animation duration. Higher values create a more intense effect (default: 100).
**Return Value:** `Unit` (Composable)

### `loadNextBatch`
**Description:** Loads the next batch of media items into `displayMedia` based on current filters and processing status.
**Parameters:** None (Uses `NativeVoiceLikeActivity` state)
**Return Value:** `Unit`

### `VoiceLikeApp`
**Description:** The main Composable entry point for the application. Manages global state, navigation, and top-level UI structure.
**Parameters:** None
**Return Value:** `Unit` (Composable)

## ShootingStatsView.kt

### `ShootingStatsView`
**Description:** Displays a heatmap and statistics about the user's photo activity.
**Parameters:**
- `items: List<MediaItem>`: List of all media items to analyze.
**Return Value:** `Unit` (Composable)
**Notes:** Uses `produceState` with `Dispatchers.Default` to calculate statistics off the main thread.
