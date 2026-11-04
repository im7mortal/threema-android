package ch.threema.app.libthreema.conversion

import ch.threema.libthreema.PollAnnounceType as LibthreemaPollAnnounceType
import ch.threema.libthreema.PollAnswerType as LibthreemaPollAnswerType
import ch.threema.libthreema.PollDisplayMode as LibthreemaPollDisplayMode
import ch.threema.libthreema.PollState as LibthreemaPollState
import ch.threema.storage.models.poll.PollModel

fun PollModel.State.toLibthreemaPollState(): LibthreemaPollState =
    when (this) {
        PollModel.State.OPEN -> LibthreemaPollState.OPEN
        PollModel.State.CLOSED,
        PollModel.State.TEMPORARY,
        -> LibthreemaPollState.CLOSED
    }

fun PollModel.Assessment.toLibthreemaPollAnswerType(): LibthreemaPollAnswerType =
    when (this) {
        PollModel.Assessment.MULTIPLE_CHOICE -> LibthreemaPollAnswerType.MULTIPLE_CHOICE
        PollModel.Assessment.SINGLE_CHOICE -> LibthreemaPollAnswerType.SINGLE_CHOICE
    }

fun PollModel.Type.toLibthreemaPollAnnounceType(): LibthreemaPollAnnounceType =
    when (this) {
        PollModel.Type.RESULT_ON_CLOSE -> LibthreemaPollAnnounceType.CREATOR_ONLY
        PollModel.Type.INTERMEDIATE -> LibthreemaPollAnnounceType.EVERYONE
    }

fun PollModel.DisplayType.toLibthreemaPollDisplayMode(): LibthreemaPollDisplayMode =
    when (this) {
        PollModel.DisplayType.SUMMARY_MODE -> LibthreemaPollDisplayMode.SUMMARY
        PollModel.DisplayType.LIST_MODE -> LibthreemaPollDisplayMode.LIST
    }

fun LibthreemaPollState.toPollState(): PollModel.State =
    when (this) {
        LibthreemaPollState.OPEN -> PollModel.State.OPEN
        LibthreemaPollState.CLOSED -> PollModel.State.CLOSED
    }

fun LibthreemaPollAnswerType.toAssessment(): PollModel.Assessment =
    when (this) {
        LibthreemaPollAnswerType.MULTIPLE_CHOICE -> PollModel.Assessment.MULTIPLE_CHOICE
        LibthreemaPollAnswerType.SINGLE_CHOICE -> PollModel.Assessment.SINGLE_CHOICE
    }

fun LibthreemaPollAnnounceType.toType(): PollModel.Type =
    when (this) {
        LibthreemaPollAnnounceType.CREATOR_ONLY -> PollModel.Type.RESULT_ON_CLOSE
        LibthreemaPollAnnounceType.EVERYONE -> PollModel.Type.INTERMEDIATE
    }

fun LibthreemaPollDisplayMode.toDisplayType(): PollModel.DisplayType =
    when (this) {
        LibthreemaPollDisplayMode.SUMMARY -> PollModel.DisplayType.SUMMARY_MODE
        LibthreemaPollDisplayMode.LIST -> PollModel.DisplayType.LIST_MODE
    }
