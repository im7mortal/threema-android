package ch.threema.app.services.poll;

import java.util.List;
import java.util.Map;

import ch.threema.storage.models.poll.PollModel;
import ch.threema.storage.models.poll.PollVoteModel;

public class PollMatrixDataImpl implements PollMatrixData {

    private final List<PollMatrixService.Participant> participants;
    private final List<PollMatrixService.Choice> choices;
    private final Map<String, PollVoteModel> data;
    private final PollMatrixService.DataKeyBuilder keyBuilder;

    public PollMatrixDataImpl(PollModel pollModel,
                              List<PollMatrixService.Participant> participants,
                              List<PollMatrixService.Choice> choices,
                              Map<String, PollVoteModel> data,
                              PollMatrixService.DataKeyBuilder keyBuilder) {
        this.participants = participants;
        this.choices = choices;
        this.data = data;
        this.keyBuilder = keyBuilder;
    }

    @Override
    public List<PollMatrixService.Participant> getParticipants() {
        return this.participants;
    }

    @Override
    public List<PollMatrixService.Choice> getChoices() {
        return this.choices;
    }

    @Override
    public PollVoteModel getVote(final PollMatrixService.Participant participant, final PollMatrixService.Choice choice) {
        synchronized (this.data) {
            String key = this.keyBuilder.build(participant, choice);
            if (key != null) {
                return this.data.get(key);
            }
        }
        return null;
    }


}
