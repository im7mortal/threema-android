package ch.threema.storage.models.poll;

import java.time.Instant;

public class PollChoiceModel {
    public static final String TABLE = "ballot_choice";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_POLL_ID = "ballotId";
    public static final String COLUMN_API_CHOICE_ID = "apiBallotChoiceId";
    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_VOTE_COUNT = "voteCount";
    public static final String COLUMN_ORDER = "order";
    public static final String COLUMN_CREATED_AT = "createdAt";
    public static final String COLUMN_MODIFIED_AT = "modifiedAt";


    public enum Type {
        Text
    }

    private int id;
    private int pollId;
    private int apiPollChoiceId;
    private Type type;
    private String name;
    private int voteCount;
    private int order;
    private Instant createdAt;
    private Instant modifiedAt;


    public int getId() {
        return id;
    }

    public PollChoiceModel setId(int id) {
        this.id = id;
        return this;
    }

    public int getPollId() {
        return pollId;
    }

    public PollChoiceModel setPollId(int pollId) {
        this.pollId = pollId;
        return this;
    }

    public int getApiPollChoiceId() {
        return apiPollChoiceId;
    }

    public PollChoiceModel setApiPollChoiceId(int apiPollChoiceId) {
        this.apiPollChoiceId = apiPollChoiceId;
        return this;
    }

    public Type getType() {
        return type;
    }

    public PollChoiceModel setType(Type type) {
        this.type = type;
        return this;
    }

    public String getName() {
        return name;
    }

    public PollChoiceModel setName(String name) {
        this.name = name;
        return this;
    }

    public int getVoteCount() {
        return voteCount;
    }

    public PollChoiceModel setVoteCount(int voteCount) {
        this.voteCount = voteCount;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public PollChoiceModel setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Instant getModifiedAt() {
        return modifiedAt;
    }

    public PollChoiceModel setModifiedAt(Instant modifiedAt) {
        this.modifiedAt = modifiedAt;
        return this;
    }

    public int getOrder() {
        return order;
    }

    public PollChoiceModel setOrder(int order) {
        this.order = order;
        return this;
    }

}
