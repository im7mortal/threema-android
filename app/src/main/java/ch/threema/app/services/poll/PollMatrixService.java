package ch.threema.app.services.poll;

import ch.threema.base.ThreemaException;
import ch.threema.storage.models.poll.PollChoiceModel;
import ch.threema.storage.models.poll.PollVoteModel;

public interface PollMatrixService {

    interface Participant {
        boolean hasVoted();

        String getIdentity();

        int getPos();
    }

    interface Choice {
        PollChoiceModel getPollChoiceModel();

        boolean isWinner();

        int getVoteCount();

        int getPos();
    }

    interface DataKeyBuilder {
        String build(Participant p, Choice c);
    }

    Participant createParticipant(String identity);

    Choice createChoice(PollChoiceModel choiceModel);

    PollMatrixService addVote(PollVoteModel pollVoteModel) throws ThreemaException;

    PollMatrixData finish();
}
