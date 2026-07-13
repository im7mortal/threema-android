package ch.threema.app.services.poll;

import java.util.List;

import ch.threema.app.services.poll.PollMatrixService.Choice;
import ch.threema.app.services.poll.PollMatrixService.Participant;
import ch.threema.storage.models.poll.PollVoteModel;

public interface PollMatrixData {
    List<Participant> getParticipants();

    List<Choice> getChoices();

    PollVoteModel getVote(final Participant participant, final Choice choice);
}
