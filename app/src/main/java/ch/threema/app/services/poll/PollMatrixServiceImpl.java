package ch.threema.app.services.poll;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.threema.base.ThreemaException;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import static ch.threema.common.JavaCompat.areEqual;

import ch.threema.storage.models.poll.PollChoiceModel;
import ch.threema.storage.models.poll.PollModel;
import ch.threema.storage.models.poll.PollVoteModel;

public class PollMatrixServiceImpl implements PollMatrixService {
    private static final Logger logger = getThreemaLogger("PollMatrixServiceImpl");

    private abstract class AxisElement {
        private final int pos;
        protected boolean[] otherChoose;

        protected AxisElement(int pos) {
            this.pos = pos;
        }

        public int getPos() {
            return this.pos;
        }

        protected boolean hasOtherChoose(int pos) {
            return otherChoose != null
                && pos >= 0
                && otherChoose.length > pos
                && otherChoose[pos];
        }
    }

    public class Participant extends AxisElement implements PollMatrixService.Participant {
        private final String identity;
        private boolean hasVoted;

        public Participant(int pos, String identity) {
            super(pos);
            this.identity = identity;
        }

        @Override
        public boolean hasVoted() {
            return this.hasVoted;
        }

        @Override
        public String getIdentity() {
            return this.identity;
        }

    }

    public class Choice extends AxisElement implements PollMatrixService.Choice {
        private final PollChoiceModel choiceModel;
        private int voteCount = 0;
        private boolean isWinner = false;

        public Choice(int pos, PollChoiceModel choiceModel) {
            super(pos);
            this.choiceModel = choiceModel;
        }

        @Override
        public PollChoiceModel getPollChoiceModel() {
            return this.choiceModel;
        }

        @Override
        public boolean isWinner() {
            return this.isWinner;
        }

        @Override
        public int getVoteCount() {
            return this.voteCount;
        }
    }

    private boolean finished = false;

    private final PollModel pollModel;
    private final List<Participant> participants = new ArrayList<>();
    private final List<Choice> choices = new ArrayList<>();
    private final Map<String, PollVoteModel> data = new HashMap<>();
    private final DataKeyBuilder dataKeyBuilder = new DataKeyBuilder() {
        @Override
        public String build(PollMatrixService.Participant p, PollMatrixService.Choice c) {
            return p.getPos() + "_" + c.getPos();
        }
    };

    public PollMatrixServiceImpl(PollModel pollModel) {
        this.pollModel = pollModel;
    }

    @Override
    public Participant createParticipant(String identity) {
        if (this.finished) {
            return null;
        }

        synchronized (this.participants) {
            int pos = participants.size();
            Participant p = new Participant(pos, identity);
            this.participants.add(p);
            return p;
        }
    }

    @Override
    public Choice createChoice(PollChoiceModel choiceModel) {
        if (this.finished) {
            return null;
        }

        synchronized (this.choices) {
            int pos = choices.size();
            Choice c = new Choice(pos, choiceModel);
            this.choices.add(c);
            return c;
        }
    }


    @Override
    public PollMatrixServiceImpl addVote(PollVoteModel pollVoteModel) throws ThreemaException {
        if (this.finished) {
            return this;
        }

        String voter = pollVoteModel.getVotingIdentity();
        int choiceModelId = pollVoteModel.getPollChoiceId();

        PollMatrixService.Participant participant = null;
        PollMatrixService.Choice choice = null;

        //get position in axis
        for (int x = 0; x < this.participants.size(); x++) {
            if (areEqual(voter, this.participants.get(x).getIdentity())) {
                participant = this.participants.get(x);
                break;
            }
        }
        for (int y = 0; y < this.choices.size(); y++) {
            if (choiceModelId == this.choices.get(y).getPollChoiceModel().getId()) {
                choice = this.choices.get(y);
                break;
            }
        }

        if (participant == null) {
            //participant do not exist
            //possible reason: the user left the group
            //do not crash at this time
            logger.error("a participant was not recognized");
            return this;
        }


        if (choice == null) {
            logger.error("choice {} not found, ignore result", pollVoteModel.getPollChoiceId());
            return this;
        }

        synchronized (this.data) {
            this.data.put(this.dataKeyBuilder.build(participant, choice), pollVoteModel);
        }

        return this;
    }

    private PollVoteModel getVote(final Participant participant, final Choice choice) {
        synchronized (this.data) {
            String key = this.dataKeyBuilder.build(participant, choice);
            if (key != null) {
                return this.data.get(key);
            }
        }
        return null;
    }

    @Override
    public PollMatrixData finish() {
        for (int x = 0; x < this.participants.size(); x++) {
            //get all votes by participants
            boolean[] choices = new boolean[this.choices.size()];
            boolean hasVoted = false;

            Participant p = this.participants.get(x);
            for (int y = 0; y < choices.length; y++) {
                PollVoteModel v = this.getVote(p, this.choices.get(y));
                hasVoted = hasVoted || v != null;
                choices[y] = v != null
                    && v.getChoice() > 0;
            }
            p.otherChoose = choices;
            p.hasVoted = hasVoted;
        }

        for (int y = 0; y < this.choices.size(); y++) {
            //get all votes by participants
            boolean[] participant = new boolean[this.participants.size()];
            Choice c = this.choices.get(y);
            for (int x = 0; x < participant.length; x++) {
                PollVoteModel v = this.getVote(this.participants.get(x), c);
                participant[x] = v != null
                    && v.getChoice() > 0;
            }
            c.otherChoose = participant;
        }

        int maxPoints = 0;
        for (Choice c : this.choices) {
            int point = 0;
            // check if we saved total votes count from a result of display type SUMMARY (case broadcast poll)
            if (c.getPollChoiceModel().getVoteCount() != 0) {
                point = c.getPollChoiceModel().getVoteCount();
            } else { // else compute the count
                for (Participant p : this.participants) {
                    point += p.hasOtherChoose(c.getPos()) ? 1 : 0;
                }
            }
            c.voteCount = point;
            maxPoints = Math.max(point, maxPoints);
        }

        for (Choice c : this.choices) {
            //only a choice with more than 0 points can win
            c.isWinner = maxPoints > 0 && c.getVoteCount() == maxPoints;
        }
        return new PollMatrixDataImpl(this.pollModel,
            (List<PollMatrixService.Participant>) (List<?>) this.participants,
            (List<PollMatrixService.Choice>) (List<?>) this.choices,
            this.data,
            this.dataKeyBuilder);
    }

}
