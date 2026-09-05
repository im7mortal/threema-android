## Plan: Preserve deleted-message metadata

We’ll update the message-deletion flow so deletion no longer strips message content/metadata, while the UI still clearly marks deleted messages with a red frame. The safest approach is to identify every place that currently treats `isDeleted` as “remove data,” then switch those paths to keep the model intact and only change presentation/flags.

### Steps
1. Review deletion handling in `MessageServiceImpl.deleteMessageContentsAndRelatedData()` and `AbstractMessageModel.isDeleted`.
2. Update persistence/serialization paths so `deletedAt` is retained and not treated as “drop body/caption/state.”
3. Adjust Compose rendering in `MessageBubble.kt` to draw a red outline when `message.isDeleted` is true.
4. Keep existing deleted-message text fallback logic in `MessageBubble`, `ConversationListItem`, and starred-message views, but stop discarding metadata.
5. Reconcile webclient/update behavior in `MessageUpdateHandler.kt` and any refresh/event paths that assume deleted means removed.
6. Verify all affected call sites that read `deletedAt` or `isDeleted` still show correct history, previews, and details.

### Further Considerations
1. Should deleted messages keep their original body/caption everywhere, or only in detail/history views?
2. Do you want the red frame on all deleted bubbles, including starred and conversation list previews, or only chat messages?
3. If deletion events are sync’d to other devices, should they now send a “modified with deletedAt” update instead of a remove-style update?
