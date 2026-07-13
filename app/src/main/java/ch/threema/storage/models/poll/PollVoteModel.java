package ch.threema.storage.models.poll;

import java.time.Instant;

public class PollVoteModel {
    public static final String TABLE = "ballot_vote";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_POLL_ID = "ballotId";
    public static final String COLUMN_POLL_CHOICE_ID = "ballotChoiceId";
    public static final String COLUMN_VOTING_IDENTITY = "votingIdentity";
    public static final String COLUMN_CHOICE = "choice";
    public static final String COLUMN_CREATED_AT = "createdAt";
    public static final String COLUMN_MODIFIED_AT = "modifiedAt";

    private int id;
    private int pollId;
    private int pollChoiceId;
    private String votingIdentity;
    private int choice;
    private Instant createdAt;
    private Instant modifiedAt;

    public int getPollChoiceId() {
        return pollChoiceId;
    }

    public PollVoteModel setPollChoiceId(int pollChoiceId) {
        this.pollChoiceId = pollChoiceId;
        return this;
    }

    public int getId() {
        return id;
    }

    public PollVoteModel setId(int id) {
        this.id = id;
        return this;
    }

    public String getVotingIdentity() {
        return votingIdentity;
    }

    public PollVoteModel setVotingIdentity(String votingIdentity) {
        this.votingIdentity = votingIdentity;
        return this;
    }

    public int getChoice() {
        return choice;
    }

    public PollVoteModel setChoice(int choice) {
        this.choice = choice;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public PollVoteModel setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Instant getModifiedAt() {
        return modifiedAt;
    }

    public PollVoteModel setModifiedAt(Instant modifiedAt) {
        this.modifiedAt = modifiedAt;
        return this;
    }

    public int getPollId() {
        return pollId;
    }

    public PollVoteModel setPollId(int pollId) {
        this.pollId = pollId;
        return this;
    }
}
