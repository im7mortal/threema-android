package ch.threema.data.datatypes

/**
 * ID that uniquely identifies a specific poll in the local database.
 * Not to be confused with [ApiPollId]
 */
typealias LocalPollId = Int

/**
 * ID that identifies a specific poll within a conversation, at the protocol level.
 * Stored as a hex-encoded unsigned long
 */
typealias ApiPollId = String

/**
 * ID that uniquely identifies a specific poll vote in the local database.
 * Not to be confused with [ApiPollChoiceId]
 */
typealias LocalPollChoiceId = Int

/**
 * ID that identifies a specific choice of a poll, at the protocol level.
 */
typealias ApiPollChoiceId = Int
